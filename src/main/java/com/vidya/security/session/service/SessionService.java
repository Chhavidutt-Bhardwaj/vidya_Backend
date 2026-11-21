package com.vidya.security.session.service;

import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.security.entity.PlatformUser;
import com.vidya.security.repository.RefreshTokenRepository;
import com.vidya.security.session.dto.SessionResponse;
import com.vidya.security.session.entity.SessionStatus;
import com.vidya.security.session.entity.UserSession;
import com.vidya.security.session.repository.UserSessionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Server-side sessions for every user type. One session per login/device; the session id goes
 * into both tokens as {@code sid}. Ending a session revokes its refresh tokens and makes its
 * access token stop working on the very next request.
 */
@Service
@Slf4j
public class SessionService {

    public static final String REASON_LOGOUT = "LOGOUT";
    public static final String REASON_LOGOUT_ALL = "LOGOUT_ALL";
    public static final String REASON_SESSION_LIMIT = "SESSION_LIMIT";
    public static final String REASON_ADMIN = "REVOKED_BY_ADMIN";
    public static final String REASON_USER = "REVOKED_BY_USER";
    public static final String REASON_TOKEN_REUSE = "REFRESH_TOKEN_REUSE";
    public static final String REASON_EXPIRED = "EXPIRED";

    private static final Duration TOUCH_INTERVAL = Duration.ofSeconds(60);

    private final UserSessionRepository sessionRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final int maxActivePerUser;

    public SessionService(UserSessionRepository sessionRepository,
                          RefreshTokenRepository refreshTokenRepository,
                          @Value("${vidya.security.session.max-active-per-user:10}") int maxActivePerUser) {
        this.sessionRepository = sessionRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.maxActivePerUser = Math.max(1, maxActivePerUser);
    }

    /** Opens a session; ends the oldest active ones when the per-user limit is reached. */
    @Transactional(transactionManager = "masterTransactionManager")
    public UserSession open(PlatformUser user, ClientInfo client, Instant expiresAt) {
        Instant now = Instant.now();
        List<UserSession> active = sessionRepository
                .findByUserIdAndStatusOrderByCreatedAtAsc(user.getId(), SessionStatus.ACTIVE);
        int live = 0;
        for (UserSession s : active) {
            if (!s.isActive(now)) {
                end(s, SessionStatus.EXPIRED, REASON_EXPIRED, null);
            } else {
                live++;
            }
        }
        for (UserSession s : active) {
            if (live < maxActivePerUser) {
                break;
            }
            if (s.getStatus() == SessionStatus.ACTIVE) {
                end(s, SessionStatus.REVOKED, REASON_SESSION_LIMIT, null);
                live--;
            }
        }
        UserSession session = new UserSession(user.getId(), user.getSchoolId(),
                user.getPlatformRole().getScope(), client != null ? client : ClientInfo.UNKNOWN, expiresAt);
        return sessionRepository.save(session);
    }

    /**
     * Per-request check used by the JWT filter: the session exists, belongs to the user, is
     * ACTIVE and not expired. Updates {@code last_seen_at} at most once a minute.
     */
    @Transactional(transactionManager = "masterTransactionManager")
    public boolean validateAndTouch(UUID sessionId, UUID userId) {
        if (sessionId == null || userId == null) {
            return false;
        }
        Optional<UserSession> opt = sessionRepository.findById(sessionId);
        if (opt.isEmpty() || !opt.get().getUserId().equals(userId)) {
            return false;
        }
        UserSession session = opt.get();
        Instant now = Instant.now();
        if (session.getStatus() == SessionStatus.ACTIVE && !session.isActive(now)) {
            end(session, SessionStatus.EXPIRED, REASON_EXPIRED, null);
            return false;
        }
        if (session.getStatus() != SessionStatus.ACTIVE) {
            return false;
        }
        sessionRepository.touch(sessionId, now, now.minus(TOUCH_INTERVAL));
        return true;
    }

    @Transactional(readOnly = true, transactionManager = "masterTransactionManager")
    public Optional<UserSession> find(UUID sessionId) {
        return sessionId == null ? Optional.empty() : sessionRepository.findById(sessionId);
    }

    /** Sliding expiry on token refresh. */
    @Transactional(transactionManager = "masterTransactionManager")
    public void markRefreshed(UserSession session, Instant newExpiresAt) {
        Instant now = Instant.now();
        session.setLastRefreshedAt(now);
        session.setLastSeenAt(now);
        session.setExpiresAt(newExpiresAt);
        sessionRepository.save(session);
    }

    @Transactional(transactionManager = "masterTransactionManager")
    public void end(UUID sessionId, SessionStatus status, String reason, UUID endedBy) {
        sessionRepository.findById(sessionId).ifPresent(s -> end(s, status, reason, endedBy));
    }

    /** Ends one of the user's sessions; 404 when it does not belong to them (no existence leak). */
    @Transactional(transactionManager = "masterTransactionManager")
    public void endOwned(UUID userId, UUID sessionId, SessionStatus status, String reason, UUID endedBy) {
        UserSession s = sessionRepository.findById(sessionId)
                .filter(x -> x.getUserId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("UserSession", sessionId));
        end(s, status, reason, endedBy);
    }

    /** Ends every active session of the user, optionally keeping one (e.g. "log out other devices"). */
    @Transactional(transactionManager = "masterTransactionManager")
    public int endAllForUser(UUID userId, SessionStatus status, String reason, UUID endedBy, UUID keepSessionId) {
        int count = 0;
        for (UserSession s : sessionRepository.findByUserIdAndStatusOrderByCreatedAtAsc(userId, SessionStatus.ACTIVE)) {
            if (s.getId().equals(keepSessionId)) {
                continue;
            }
            end(s, status, reason, endedBy);
            count++;
        }
        if (keepSessionId == null) {
            refreshTokenRepository.revokeAllByUserId(userId, Instant.now());
        }
        return count;
    }

    @Transactional(readOnly = true, transactionManager = "masterTransactionManager")
    public List<SessionResponse> list(UUID userId, boolean activeOnly, UUID currentSessionId) {
        Instant now = Instant.now();
        List<UserSession> sessions = activeOnly
                ? sessionRepository.findByUserIdAndStatusOrderByCreatedAtAsc(userId, SessionStatus.ACTIVE)
                .stream().filter(s -> s.isActive(now)).toList()
                : sessionRepository.findByUserIdOrderByCreatedAtDesc(userId);
        return sessions.stream().map(s -> SessionResponse.from(s, currentSessionId)).toList();
    }

    private void end(UserSession session, SessionStatus status, String reason, UUID endedBy) {
        session.end(status, reason, endedBy);
        sessionRepository.save(session);
        refreshTokenRepository.revokeAllBySessionId(session.getId(), Instant.now());
        log.info("Session [{}] of user [{}] ended: {} ({})", session.getId(), session.getUserId(), status, reason);
    }
}
