package com.vidya.security.service;

import com.vidya.common.api.ApiResponseCode;
import com.vidya.common.correlation.MdcKeys;
import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.common.exception.UnauthorizedException;
import com.vidya.master.tenant.entity.Tenant;
import com.vidya.master.tenant.repository.TenantRepository;
import com.vidya.security.access.service.AccessTokenClaims;
import com.vidya.security.access.service.EffectiveAccess;
import com.vidya.security.access.service.EffectiveAccessService;
import com.vidya.security.dto.AuthResponse;
import com.vidya.security.dto.LoginRequest;
import com.vidya.security.dto.PlatformUserResponse;
import com.vidya.security.dto.RefreshTokenRequest;
import com.vidya.security.entity.PlatformUser;
import com.vidya.security.entity.RefreshToken;
import com.vidya.security.entity.UserStatus;
import com.vidya.security.jwt.JwtTokenProvider;
import com.vidya.security.repository.PlatformUserRepository;
import com.vidya.security.repository.RefreshTokenRepository;
import com.vidya.security.session.entity.LoginEventType;
import com.vidya.security.session.entity.SessionStatus;
import com.vidya.security.session.entity.UserSession;
import com.vidya.security.session.service.ClientInfo;
import com.vidya.security.session.service.LoginHistoryService;
import com.vidya.security.session.service.SessionService;
import com.vidya.security.user.PlatformUserDetails;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Login, token refresh and logout for every user type, backed by server-side sessions.
 *
 * <p>Login and refresh use {@code noRollbackFor} on the auth exceptions on purpose: failed-attempt
 * counters, lockouts and token-theft revocations must be committed even though the request fails.
 */
@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    private final PlatformUserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final SecurityAuditService securityAuditService;
    private final UserPermissionMatrixService userPermissionMatrixService;
    private final TenantRepository tenantRepository;
    private final SessionService sessionService;
    private final LoginHistoryService loginHistoryService;
    private final EffectiveAccessService effectiveAccessService;

    @Autowired
    public AuthServiceImpl(
            PlatformUserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider,
            SecurityAuditService securityAuditService,
            UserPermissionMatrixService userPermissionMatrixService,
            @Autowired(required = false) TenantRepository tenantRepository,
            SessionService sessionService,
            LoginHistoryService loginHistoryService,
            EffectiveAccessService effectiveAccessService) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.securityAuditService = securityAuditService;
        this.userPermissionMatrixService = userPermissionMatrixService;
        this.tenantRepository = tenantRepository;
        this.sessionService = sessionService;
        this.loginHistoryService = loginHistoryService;
        this.effectiveAccessService = effectiveAccessService;
    }

    @Value("${vidya.security.login.max-failed-attempts:5}")
    private int maxFailedAttempts = 5;

    @Value("${vidya.security.login.lockout-minutes:15}")
    private long lockoutMinutes = 15;

    /** Put the access snapshot (permissions, roles, features, data scopes) into access tokens for the UI. */
    @Value("${vidya.security.jwt.embed-access:true}")
    private boolean embedAccessInToken = true;

    private String resolveTenantCode(PlatformUser user) {
        if (user != null && user.getSchoolId() != null && tenantRepository != null) {
            return tenantRepository.findBySchoolId(user.getSchoolId())
                    .map(Tenant::getTenantCode)
                    .orElse(null);
        }
        return null;
    }

    // ============================== LOGIN ==============================

    @Override
    @Transactional(transactionManager = "masterTransactionManager",
            noRollbackFor = {UnauthorizedException.class, BusinessException.class})
    public AuthResponse login(LoginRequest request) {
        return login(request, ClientInfo.UNKNOWN, MDC.get(MdcKeys.CORRELATION_ID));
    }

    @Override
    @Transactional(transactionManager = "masterTransactionManager",
            noRollbackFor = {UnauthorizedException.class, BusinessException.class})
    public AuthResponse login(LoginRequest request, String ipAddress, String correlationId) {
        return login(request, ClientInfo.of(ipAddress, null), correlationId);
    }

    @Override
    @Transactional(transactionManager = "masterTransactionManager",
            noRollbackFor = {UnauthorizedException.class, BusinessException.class})
    public AuthResponse login(LoginRequest request, ClientInfo client, String correlationId) {
        ClientInfo c = client != null ? client : ClientInfo.UNKNOWN;
        String ipAddress = c.ipAddress();
        log.info("[{}] Attempting authentication for email: {}", correlationId, request.getEmail());

        Optional<PlatformUser> userOpt = userRepository.findByEmailIgnoreCase(request.getEmail());
        if (userOpt.isEmpty()) {
            log.warn("[{}] User not found for login: {}", correlationId, request.getEmail());
            securityAuditService.logLoginFailure(request.getEmail(), "INVALID_CREDENTIALS", ipAddress, correlationId);
            loginHistoryService.record(null, request.getEmail(), LoginEventType.LOGIN_FAILED, "UNKNOWN_EMAIL", null, c, correlationId);
            // Constant response message to prevent account enumeration
            throw new UnauthorizedException("Invalid email or password");
        }

        PlatformUser user = userOpt.get();

        if (user.isAccountLocked()) {
            log.warn("[{}] Login rejected: Account locked for user: {}", correlationId, user.getEmail());
            securityAuditService.logLoginFailure(user.getEmail(), "ACCOUNT_LOCKED", ipAddress, correlationId);
            loginHistoryService.record(user, user.getEmail(), LoginEventType.LOGIN_FAILED, "ACCOUNT_LOCKED", null, c, correlationId);
            throw new BusinessException(ApiResponseCode.FORBIDDEN, "Account is temporarily locked due to consecutive failed attempts. Please try again later.");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            log.warn("[{}] Password mismatch for user: {}", correlationId, user.getEmail());
            user.recordFailedLogin(maxFailedAttempts, lockoutMinutes);
            userRepository.save(user);

            if (user.isAccountLocked()) {
                securityAuditService.logAccountLocked(user.getId(), user.getEmail(), ipAddress, correlationId);
                loginHistoryService.record(user, user.getEmail(), LoginEventType.ACCOUNT_LOCKED, "MAX_FAILED_ATTEMPTS", null, c, correlationId);
            } else {
                securityAuditService.logLoginFailure(user.getEmail(), "INVALID_CREDENTIALS", ipAddress, correlationId);
                loginHistoryService.record(user, user.getEmail(), LoginEventType.LOGIN_FAILED, "INVALID_CREDENTIALS", null, c, correlationId);
            }

            throw new UnauthorizedException("Invalid email or password");
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            log.warn("[{}] Login attempt for inactive user: {}, status: {}", correlationId, user.getEmail(), user.getStatus());
            securityAuditService.logLoginFailure(user.getEmail(), "ACCOUNT_INACTIVE", ipAddress, correlationId);
            loginHistoryService.record(user, user.getEmail(), LoginEventType.LOGIN_FAILED, "ACCOUNT_" + user.getStatus(), null, c, correlationId);
            throw new BusinessException(ApiResponseCode.FORBIDDEN, "Account is not active. Current status: " + user.getStatus());
        }

        user.recordSuccessfulLogin();
        userRepository.save(user);

        Instant refreshExpiry = Instant.now().plusSeconds(jwtTokenProvider.getRefreshTokenExpirySeconds());
        UserSession session = sessionService.open(user, c, refreshExpiry);

        AuthResponse response = issueTokens(user, session, refreshExpiry);

        securityAuditService.logLoginSuccess(user.getId(), user.getEmail(), ipAddress, correlationId);
        loginHistoryService.record(user, user.getEmail(), LoginEventType.LOGIN_SUCCESS, null, session.getId(), c, correlationId);
        log.info("[{}] User {} authenticated (role: {}, session: {})", correlationId, user.getEmail(), user.getPlatformRole(), session.getId());
        return response;
    }

    // ============================== REFRESH ==============================

    @Override
    @Transactional(transactionManager = "masterTransactionManager",
            noRollbackFor = {UnauthorizedException.class, BusinessException.class})
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        return refreshToken(request, ClientInfo.UNKNOWN, MDC.get(MdcKeys.CORRELATION_ID));
    }

    @Override
    @Transactional(transactionManager = "masterTransactionManager",
            noRollbackFor = {UnauthorizedException.class, BusinessException.class})
    public AuthResponse refreshToken(RefreshTokenRequest request, String ipAddress, String correlationId) {
        return refreshToken(request, ClientInfo.of(ipAddress, null), correlationId);
    }

    @Override
    @Transactional(transactionManager = "masterTransactionManager",
            noRollbackFor = {UnauthorizedException.class, BusinessException.class})
    public AuthResponse refreshToken(RefreshTokenRequest request, ClientInfo client, String correlationId) {
        ClientInfo c = client != null ? client : ClientInfo.UNKNOWN;
        log.info("[{}] Attempting token refresh", correlationId);

        if (request.getRefreshToken() == null || !jwtTokenProvider.validateToken(request.getRefreshToken())) {
            throw new UnauthorizedException("Invalid or expired refresh token");
        }

        if (!jwtTokenProvider.isRefreshToken(request.getRefreshToken())) {
            log.warn("[{}] Token refresh rejected: Provided token does not have REFRESH type claim", correlationId);
            throw new UnauthorizedException("Invalid token type: Expected REFRESH token");
        }

        UUID userId = jwtTokenProvider.getUserIdFromToken(request.getRefreshToken());
        String tokenHash = jwtTokenProvider.hashToken(request.getRefreshToken());

        Optional<RefreshToken> storedTokenOpt = refreshTokenRepository.findByTokenHash(tokenHash);
        if (storedTokenOpt.isEmpty()) {
            log.warn("[{}] Refresh token not found in database: {}", correlationId, tokenHash);
            throw new UnauthorizedException("Invalid or expired refresh token");
        }

        RefreshToken storedToken = storedTokenOpt.get();
        if (storedToken.isRevoked()) {
            log.warn("[{}] Revoked refresh token reused for user [{}]. Ending all sessions as a precaution against token theft.",
                    correlationId, userId);
            sessionService.endAllForUser(userId, SessionStatus.REVOKED, SessionService.REASON_TOKEN_REUSE, null, null);
            refreshTokenRepository.revokeAllByUserId(userId, Instant.now());
            securityAuditService.logAccessDenied(userId, null, "REFRESH_TOKEN", "REUSED_REVOKED_TOKEN", c.ipAddress(), correlationId);
            loginHistoryService.record(userRepository.findById(userId).orElse(null), null,
                    LoginEventType.TOKEN_REUSE_DETECTED, "REUSED_REVOKED_TOKEN", storedToken.getSessionId(), c, correlationId);
            throw new UnauthorizedException("Refresh token has been revoked");
        }

        if (storedToken.isExpired()) {
            throw new UnauthorizedException("Refresh token has expired");
        }

        UserSession session = sessionService.find(storedToken.getSessionId())
                .filter(s -> s.getUserId().equals(userId) && s.isActive(Instant.now()))
                .orElseThrow(() -> new UnauthorizedException("Session has ended. Please log in again."));

        PlatformUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("PlatformUser", userId));

        if (user.getStatus() != UserStatus.ACTIVE || user.isAccountLocked()) {
            sessionService.end(session.getId(), SessionStatus.REVOKED, "ACCOUNT_INACTIVE", null);
            throw new BusinessException(ApiResponseCode.FORBIDDEN, "User account is no longer active or is locked");
        }

        Instant refreshExpiry = Instant.now().plusSeconds(jwtTokenProvider.getRefreshTokenExpirySeconds());
        AuthResponse response = issueTokens(user, session, refreshExpiry);

        // Rotate: the presented token is revoked and chained to its successor
        storedToken.revoke(jwtTokenProvider.hashToken(response.getRefreshToken()));
        refreshTokenRepository.save(storedToken);
        sessionService.markRefreshed(session, refreshExpiry);

        securityAuditService.logTokenRefresh(user.getId(), user.getEmail(), c.ipAddress(), correlationId);
        loginHistoryService.record(user, user.getEmail(), LoginEventType.TOKEN_REFRESHED, null, session.getId(), c, correlationId);
        log.info("[{}] Tokens refreshed & rotated for user: {} (session {})", correlationId, user.getEmail(), session.getId());
        return response;
    }

    // ============================== LOGOUT ==============================

    @Override
    @Transactional(transactionManager = "masterTransactionManager")
    public void logout(RefreshTokenRequest request) {
        logout(request, ClientInfo.UNKNOWN, MDC.get(MdcKeys.CORRELATION_ID));
    }

    @Override
    @Transactional(transactionManager = "masterTransactionManager")
    public void logout(RefreshTokenRequest request, String ipAddress, String correlationId) {
        logout(request, ClientInfo.of(ipAddress, null), correlationId);
    }

    @Override
    @Transactional(transactionManager = "masterTransactionManager")
    public void logout(RefreshTokenRequest request, ClientInfo client, String correlationId) {
        ClientInfo c = client != null ? client : ClientInfo.UNKNOWN;
        PlatformUserDetails principal = currentPrincipal();
        UUID userId = principal != null ? principal.getUserId() : null;
        UUID sessionId = principal != null ? principal.getSessionId() : null;

        if (request != null && request.getRefreshToken() != null && !request.getRefreshToken().isBlank()) {
            try {
                String tokenHash = jwtTokenProvider.hashToken(request.getRefreshToken());
                Optional<RefreshToken> storedTokenOpt = refreshTokenRepository.findByTokenHash(tokenHash);
                if (storedTokenOpt.isPresent()) {
                    RefreshToken token = storedTokenOpt.get();
                    // Never let a caller end someone else's session with a stolen-but-foreign token
                    if (userId == null || userId.equals(token.getUserId())) {
                        token.revoke("LOGOUT");
                        refreshTokenRepository.save(token);
                        userId = token.getUserId();
                        if (sessionId == null) {
                            sessionId = token.getSessionId();
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("[{}] Error revoking refresh token on logout: {}", correlationId, e.getMessage());
            }
        }

        if (sessionId != null) {
            sessionService.end(sessionId, SessionStatus.LOGGED_OUT, SessionService.REASON_LOGOUT, userId);
        } else if (userId != null) {
            // No session context at all: fall back to ending everything for this user
            sessionService.endAllForUser(userId, SessionStatus.LOGGED_OUT, SessionService.REASON_LOGOUT, userId, null);
        }

        PlatformUser user = userId != null ? userRepository.findById(userId).orElse(null) : null;
        String email = user != null ? user.getEmail() : (principal != null ? principal.getEmail() : null);
        securityAuditService.logLogout(userId, email, c.ipAddress(), correlationId);
        if (userId != null) {
            loginHistoryService.record(user, email, LoginEventType.LOGOUT, null, sessionId, c, correlationId);
        }
        log.info("[{}] User {} logged out (session {})", correlationId, email != null ? email : userId, sessionId);
    }

    @Override
    @Transactional(transactionManager = "masterTransactionManager")
    public void logoutAll(ClientInfo client, String correlationId) {
        PlatformUserDetails principal = currentPrincipal();
        if (principal == null) {
            throw new UnauthorizedException("No authenticated user in context");
        }
        int ended = sessionService.endAllForUser(principal.getUserId(), SessionStatus.LOGGED_OUT,
                SessionService.REASON_LOGOUT_ALL, principal.getUserId(), null);
        PlatformUser user = userRepository.findById(principal.getUserId()).orElse(null);
        securityAuditService.logLogout(principal.getUserId(), principal.getEmail(),
                client != null ? client.ipAddress() : null, correlationId);
        loginHistoryService.record(user, principal.getEmail(), LoginEventType.LOGOUT_ALL, null,
                principal.getSessionId(), client, correlationId);
        log.info("[{}] User {} logged out of all {} sessions", correlationId, principal.getEmail(), ended);
    }

    // ============================== CURRENT USER ==============================

    @Override
    @Transactional(readOnly = true, transactionManager = "masterTransactionManager")
    public PlatformUserResponse getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new UnauthorizedException("No authenticated user in context");
        }

        String email = authentication.getName();
        PlatformUser user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("PlatformUser", email));

        PlatformUserDetails principal = currentPrincipal();
        return mapToUserResponse(user, effectiveAccessService.getAccess(user),
                principal != null ? principal.getSessionId() : null);
    }

    // ============================== helpers ==============================

    private AuthResponse issueTokens(PlatformUser user, UserSession session, Instant refreshExpiry) {
        String tenantCode = resolveTenantCode(user);
        EffectiveAccess access = effectiveAccessService.resolveFresh(user);
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail(), user.getUserCode(),
                user.getPlatformRole().name(), user.getSchoolId(), tenantCode, session.getId(),
                embedAccessInToken ? AccessTokenClaims.of(access) : null);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId(), user.getEmail(), session.getId());

        // Persist only the hash of the refresh token
        refreshTokenRepository.save(new RefreshToken(user.getId(), jwtTokenProvider.hashToken(refreshToken),
                refreshExpiry, session.getId()));

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getAccessTokenExpirySeconds())
                .user(mapToUserResponse(user, access, session.getId()))
                .build();
    }

    private PlatformUserDetails currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof PlatformUserDetails pud) {
            return pud;
        }
        return null;
    }

    private PlatformUserResponse mapToUserResponse(PlatformUser user, EffectiveAccess access, UUID sessionId) {
        Map<Integer, Set<Integer>> permissionsMatrix = userPermissionMatrixService != null
                ? userPermissionMatrixService.resolvePermissionsMatrix(user, access)
                : Map.of();

        return PlatformUserResponse.builder()
                .id(user.getId())
                .userCode(user.getUserCode())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .status(user.getStatus())
                .platformRole(user.getPlatformRole())
                .accountScope(access.scope())
                .schoolId(user.getSchoolId())
                .tenantCode(resolveTenantCode(user))
                .roles(access.roles())
                .permissions(access.permissions())
                .enabledFeatures(access.enabledFeatures())
                .sessionId(sessionId)
                .dataScopes(access.dataScopes().entrySet().stream().collect(java.util.stream.Collectors.toMap(
                        e -> e.getKey().name(), e -> e.getValue().name(), (a, b) -> a, java.util.LinkedHashMap::new)))
                .accessVersion(access.version())
                .permissionsMatrix(permissionsMatrix)
                .lastLoginAt(user.getLastLoginAt())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
