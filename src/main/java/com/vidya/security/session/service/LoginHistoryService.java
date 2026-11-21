package com.vidya.security.session.service;

import com.vidya.security.entity.PlatformUser;
import com.vidya.security.session.dto.LoginHistoryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.vidya.security.session.entity.LoginEventType;
import com.vidya.security.session.entity.LoginHistory;
import com.vidya.security.session.repository.LoginHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Writes login history in its own transaction, so failed-login events survive the rollback of
 * the failing login, and a history write error never breaks authentication.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LoginHistoryService {

    private final LoginHistoryRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW, transactionManager = "masterTransactionManager")
    public void record(PlatformUser user, String email, LoginEventType event, String failureReason,
                       UUID sessionId, ClientInfo client, String correlationId) {
        try {
            ClientInfo c = client != null ? client : ClientInfo.UNKNOWN;
            repository.save(LoginHistory.builder()
                    .userId(user != null ? user.getId() : null)
                    .email(user != null ? user.getEmail() : truncate(email, 255))
                    .schoolId(user != null ? user.getSchoolId() : null)
                    .accountScope(user != null && user.getPlatformRole() != null ? user.getPlatformRole().getScope() : null)
                    .eventType(event)
                    .failureReason(truncate(failureReason, 100))
                    .sessionId(sessionId)
                    .ipAddress(c.ipAddress())
                    .userAgent(c.userAgent())
                    .deviceType(c.deviceType())
                    .browser(c.browser())
                    .operatingSystem(c.operatingSystem())
                    .correlationId(truncate(correlationId, 100))
                    .occurredAt(Instant.now())
                    .build());
        } catch (Exception e) {
            log.error("Failed to write login history event {} for {}: {}", event, email, e.getMessage());
        }
    }

    @Transactional(readOnly = true, transactionManager = "masterTransactionManager")
    public Page<LoginHistoryResponse> forUser(UUID userId, Pageable pageable) {
        return repository.findByUserIdOrderByOccurredAtDesc(userId, pageable).map(LoginHistoryResponse::from);
    }

    @Transactional(readOnly = true, transactionManager = "masterTransactionManager")
    public Page<LoginHistoryResponse> forSchool(UUID schoolId, Pageable pageable) {
        return repository.findBySchoolIdOrderByOccurredAtDesc(schoolId, pageable).map(LoginHistoryResponse::from);
    }

    private static String truncate(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max) : s;
    }
}
