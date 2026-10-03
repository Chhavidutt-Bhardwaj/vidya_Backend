package com.vidya.security.service;

import com.vidya.common.api.ApiResponseCode;
import com.vidya.common.correlation.MdcKeys;
import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.common.exception.UnauthorizedException;
import com.vidya.security.dto.AuthResponse;
import com.vidya.security.dto.LoginRequest;
import com.vidya.security.dto.PlatformUserResponse;
import com.vidya.security.dto.RefreshTokenRequest;
import com.vidya.security.entity.PlatformPermission;
import com.vidya.security.entity.PlatformUser;
import com.vidya.security.entity.RefreshToken;
import com.vidya.security.entity.UserStatus;
import com.vidya.security.jwt.JwtTokenProvider;
import com.vidya.security.repository.PlatformUserRepository;
import com.vidya.security.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final PlatformUserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final SecurityAuditService securityAuditService;

    @Value("${vidya.security.login.max-failed-attempts:5}")
    private int maxFailedAttempts = 5;

    @Value("${vidya.security.login.lockout-minutes:15}")
    private long lockoutMinutes = 15;

    @Override
    @Transactional(transactionManager = "masterTransactionManager")
    public AuthResponse login(LoginRequest request) {
        return login(request, null, MDC.get(MdcKeys.CORRELATION_ID));
    }

    @Override
    @Transactional(transactionManager = "masterTransactionManager")
    public AuthResponse login(LoginRequest request, String ipAddress, String correlationId) {
        log.info("[{}] Attempting authentication for email: {}", correlationId, request.getEmail());

        Optional<PlatformUser> userOpt = userRepository.findByEmailIgnoreCase(request.getEmail());
        if (userOpt.isEmpty()) {
            log.warn("[{}] User not found for login: {}", correlationId, request.getEmail());
            securityAuditService.logLoginFailure(request.getEmail(), "INVALID_CREDENTIALS", ipAddress, correlationId);
            // Constant response message to prevent account enumeration
            throw new UnauthorizedException("Invalid email or password");
        }

        PlatformUser user = userOpt.get();

        if (user.isAccountLocked()) {
            log.warn("[{}] Login rejected: Account locked for user: {}", correlationId, user.getEmail());
            securityAuditService.logLoginFailure(user.getEmail(), "ACCOUNT_LOCKED", ipAddress, correlationId);
            throw new BusinessException(ApiResponseCode.FORBIDDEN, "Account is temporarily locked due to consecutive failed attempts. Please try again later.");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            log.warn("[{}] Password mismatch for user: {}", correlationId, user.getEmail());
            user.recordFailedLogin(maxFailedAttempts, lockoutMinutes);
            userRepository.save(user);

            if (user.isAccountLocked()) {
                securityAuditService.logAccountLocked(user.getId(), user.getEmail(), ipAddress, correlationId);
            } else {
                securityAuditService.logLoginFailure(user.getEmail(), "INVALID_CREDENTIALS", ipAddress, correlationId);
            }

            throw new UnauthorizedException("Invalid email or password");
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            log.warn("[{}] Login attempt for inactive user: {}, status: {}", correlationId, user.getEmail(), user.getStatus());
            securityAuditService.logLoginFailure(user.getEmail(), "ACCOUNT_INACTIVE", ipAddress, correlationId);
            throw new BusinessException(ApiResponseCode.FORBIDDEN, "Account is not active. Current status: " + user.getStatus());
        }

        user.recordSuccessfulLogin();
        userRepository.save(user);

        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getId(), user.getEmail(), user.getUserCode(), user.getPlatformRole().name(), user.getSchoolId());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId(), user.getEmail());

        // Save hashed refresh token in database for revocability and rotation tracking
        String tokenHash = jwtTokenProvider.hashToken(refreshToken);
        Instant expiryInstant = Instant.now().plusSeconds(jwtTokenProvider.getRefreshTokenExpirySeconds());
        RefreshToken persistedRefreshToken = new RefreshToken(user.getId(), tokenHash, expiryInstant);
        refreshTokenRepository.save(persistedRefreshToken);

        securityAuditService.logLoginSuccess(user.getId(), user.getEmail(), ipAddress, correlationId);

        log.info("[{}] User {} successfully authenticated with role: {}", correlationId, user.getEmail(), user.getPlatformRole());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getAccessTokenExpirySeconds())
                .user(mapToUserResponse(user))
                .build();
    }

    @Override
    @Transactional(transactionManager = "masterTransactionManager")
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        return refreshToken(request, null, MDC.get(MdcKeys.CORRELATION_ID));
    }

    @Override
    @Transactional(transactionManager = "masterTransactionManager")
    public AuthResponse refreshToken(RefreshTokenRequest request, String ipAddress, String correlationId) {
        log.info("[{}] Attempting token refresh", correlationId);

        if (request.getRefreshToken() == null || !jwtTokenProvider.validateToken(request.getRefreshToken())) {
            throw new UnauthorizedException("Invalid or expired refresh token");
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
            log.warn("[{}] Revoked refresh token reused for user [{}]. Revoking all active tokens as a precaution against token theft.",
                    correlationId, userId);
            refreshTokenRepository.revokeAllByUserId(userId, Instant.now());
            securityAuditService.logAccessDenied(userId, null, "REFRESH_TOKEN", "REUSED_REVOKED_TOKEN", ipAddress, correlationId);
            throw new UnauthorizedException("Refresh token has been revoked");
        }

        if (storedToken.isExpired()) {
            throw new UnauthorizedException("Refresh token has expired");
        }

        PlatformUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("PlatformUser", userId));

        if (user.getStatus() != UserStatus.ACTIVE || user.isAccountLocked()) {
            throw new BusinessException(ApiResponseCode.FORBIDDEN, "User account is no longer active or is locked");
        }

        // Issue new rotated tokens
        String newAccessToken = jwtTokenProvider.generateAccessToken(
                user.getId(), user.getEmail(), user.getUserCode(), user.getPlatformRole().name(), user.getSchoolId());
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(user.getId(), user.getEmail());
        String newRefreshTokenHash = jwtTokenProvider.hashToken(newRefreshToken);

        // Revoke old token and store new one
        storedToken.revoke(newRefreshTokenHash);
        refreshTokenRepository.save(storedToken);

        Instant expiryInstant = Instant.now().plusSeconds(jwtTokenProvider.getRefreshTokenExpirySeconds());
        RefreshToken newPersistedToken = new RefreshToken(user.getId(), newRefreshTokenHash, expiryInstant);
        refreshTokenRepository.save(newPersistedToken);

        securityAuditService.logTokenRefresh(user.getId(), user.getEmail(), ipAddress, correlationId);

        log.info("[{}] Tokens successfully refreshed & rotated for user: {}", correlationId, user.getEmail());

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getAccessTokenExpirySeconds())
                .user(mapToUserResponse(user))
                .build();
    }

    @Override
    @Transactional(transactionManager = "masterTransactionManager")
    public void logout(RefreshTokenRequest request) {
        logout(request, null, MDC.get(MdcKeys.CORRELATION_ID));
    }

    @Override
    @Transactional(transactionManager = "masterTransactionManager")
    public void logout(RefreshTokenRequest request, String ipAddress, String correlationId) {
        UUID userId = null;
        String email = null;

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getPrincipal())) {
            email = authentication.getName();
        }

        if (request != null && request.getRefreshToken() != null && !request.getRefreshToken().isBlank()) {
            try {
                String tokenHash = jwtTokenProvider.hashToken(request.getRefreshToken());
                Optional<RefreshToken> storedTokenOpt = refreshTokenRepository.findByTokenHash(tokenHash);
                if (storedTokenOpt.isPresent()) {
                    RefreshToken token = storedTokenOpt.get();
                    token.revoke("LOGOUT");
                    refreshTokenRepository.save(token);
                    userId = token.getUserId();
                }
            } catch (Exception e) {
                log.warn("[{}] Error revoking refresh token on logout: {}", correlationId, e.getMessage());
            }
        }

        if (userId == null && email != null) {
            userRepository.findByEmailIgnoreCase(email).ifPresent(u -> refreshTokenRepository.revokeAllByUserId(u.getId(), Instant.now()));
        }

        securityAuditService.logLogout(userId, email, ipAddress, correlationId);
        log.info("[{}] User {} logged out successfully", correlationId, email != null ? email : userId);
    }

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

        return mapToUserResponse(user);
    }

    private PlatformUserResponse mapToUserResponse(PlatformUser user) {
        Set<String> permissions = Set.of();
        if (user.getPlatformRole() != null && user.getPlatformRole().getPermissions() != null) {
            permissions = user.getPlatformRole().getPermissions().stream()
                    .map(PlatformPermission::name)
                    .collect(Collectors.toSet());
        }

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
                .schoolId(user.getSchoolId())
                .permissions(permissions)
                .lastLoginAt(user.getLastLoginAt())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
