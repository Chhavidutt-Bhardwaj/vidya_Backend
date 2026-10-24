package com.vidya.security.service;

import com.vidya.master.audit.entity.AuditLog;
import com.vidya.master.audit.repository.AuditLogRepository;
import com.vidya.security.entity.PlatformUser;
import com.vidya.security.repository.PlatformUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityAuditServiceImpl implements SecurityAuditService {

    private final AuditLogRepository auditLogRepository;
    private final PlatformUserRepository userRepository;

    private UUID resolveRealUserId(UUID userId, String email) {
        if (userId != null) {
            return userId;
        }
        if (email != null && !email.isBlank()) {
            Optional<PlatformUser> userOpt = userRepository.findByEmailIgnoreCase(email);
            if (userOpt.isPresent()) {
                return userOpt.get().getId();
            }
            // Deterministic UUID for non-existent email to satisfy DB NOT NULL constraint without using random UUIDs
            return UUID.nameUUIDFromBytes(email.getBytes());
        }
        throw new IllegalArgumentException("Cannot audit security event without a userId or email");
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, transactionManager = "masterTransactionManager")
    public void logLoginSuccess(UUID userId, String email, String ipAddress, String correlationId) {
        log.info("[SecurityAudit] LOGIN_SUCCESS for user: {} (id: {}), ip: {}, correlationId: {}", email, userId, ipAddress, correlationId);
        try {
            UUID actualUserId = resolveRealUserId(userId, email);
            AuditLog auditLog = new AuditLog(
                    "PLATFORM_USER",
                    actualUserId,
                    "LOGIN",
                    null,
                    "{\"email\":\"" + email + "\",\"result\":\"SUCCESS\"}",
                    actualUserId,
                    "PLATFORM_USER",
                    correlationId,
                    ipAddress
            );
            auditLogRepository.saveAndFlush(auditLog);
        } catch (Exception e) {
            log.warn("Failed to persist security audit log for LOGIN_SUCCESS: {}", e.getMessage());
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, transactionManager = "masterTransactionManager")
    public void logLoginFailure(String email, String reason, String ipAddress, String correlationId) {
        log.warn("[SecurityAudit] LOGIN_FAILURE for email: {}, reason: {}, ip: {}, correlationId: {}", email, reason, ipAddress, correlationId);
        try {
            UUID actualUserId = resolveRealUserId(null, email);
            AuditLog auditLog = new AuditLog(
                    "PLATFORM_USER",
                    actualUserId,
                    "LOGIN",
                    null,
                    "{\"email\":\"" + email + "\",\"result\":\"FAILURE\",\"reason\":\"" + reason + "\"}",
                    null,
                    "EXTERNAL",
                    correlationId,
                    ipAddress
            );
            auditLogRepository.saveAndFlush(auditLog);
        } catch (Exception e) {
            log.warn("Failed to persist security audit log for LOGIN_FAILURE: {}", e.getMessage());
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, transactionManager = "masterTransactionManager")
    public void logLogout(UUID userId, String email, String ipAddress, String correlationId) {
        log.info("[SecurityAudit] LOGOUT for user: {} (id: {}), ip: {}, correlationId: {}", email, userId, ipAddress, correlationId);
        try {
            UUID actualUserId = resolveRealUserId(userId, email);
            AuditLog auditLog = new AuditLog(
                    "PLATFORM_USER",
                    actualUserId,
                    "LOGOUT",
                    null,
                    "{\"email\":\"" + email + "\",\"result\":\"LOGOUT\"}",
                    actualUserId,
                    "PLATFORM_USER",
                    correlationId,
                    ipAddress
            );
            auditLogRepository.saveAndFlush(auditLog);
        } catch (Exception e) {
            log.warn("Failed to persist security audit log for LOGOUT: {}", e.getMessage());
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, transactionManager = "masterTransactionManager")
    public void logTokenRefresh(UUID userId, String email, String ipAddress, String correlationId) {
        log.info("[SecurityAudit] TOKEN_REFRESH for user: {} (id: {}), ip: {}, correlationId: {}", email, userId, ipAddress, correlationId);
        try {
            UUID actualUserId = resolveRealUserId(userId, email);
            AuditLog auditLog = new AuditLog(
                    "PLATFORM_USER",
                    actualUserId,
                    "UPDATE",
                    null,
                    "{\"email\":\"" + email + "\",\"action\":\"TOKEN_REFRESH\"}",
                    actualUserId,
                    "PLATFORM_USER",
                    correlationId,
                    ipAddress
            );
            auditLogRepository.saveAndFlush(auditLog);
        } catch (Exception e) {
            log.warn("Failed to persist security audit log for TOKEN_REFRESH: {}", e.getMessage());
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, transactionManager = "masterTransactionManager")
    public void logAccessDenied(UUID userId, String email, String resource, String reason, String ipAddress, String correlationId) {
        log.warn("[SecurityAudit] ACCESS_DENIED for user: {} (id: {}), resource: {}, reason: {}, ip: {}, correlationId: {}",
                email, userId, resource, reason, ipAddress, correlationId);
        try {
            UUID actualUserId = resolveRealUserId(userId, email);
            AuditLog auditLog = new AuditLog(
                    "PLATFORM_USER",
                    actualUserId,
                    "ACCESS_DENIED",
                    null,
                    "{\"email\":\"" + email + "\",\"resource\":\"" + resource + "\",\"reason\":\"" + reason + "\"}",
                    actualUserId,
                    "PLATFORM_USER",
                    correlationId,
                    ipAddress
            );
            auditLogRepository.saveAndFlush(auditLog);
        } catch (Exception e) {
            log.warn("Failed to persist security audit log for ACCESS_DENIED: {}", e.getMessage());
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, transactionManager = "masterTransactionManager")
    public void logAccountLocked(UUID userId, String email, String ipAddress, String correlationId) {
        log.warn("[SecurityAudit] ACCOUNT_LOCKED for user: {} (id: {}), ip: {}, correlationId: {}", email, userId, ipAddress, correlationId);
        try {
            UUID actualUserId = resolveRealUserId(userId, email);
            AuditLog auditLog = new AuditLog(
                    "PLATFORM_USER",
                    actualUserId,
                    "STATUS_CHANGE",
                    null,
                    "{\"email\":\"" + email + "\",\"event\":\"ACCOUNT_TEMPORARILY_LOCKED\"}",
                    actualUserId,
                    "SYSTEM",
                    correlationId,
                    ipAddress
            );
            auditLogRepository.saveAndFlush(auditLog);
        } catch (Exception e) {
            log.warn("Failed to persist security audit log for ACCOUNT_LOCKED: {}", e.getMessage());
        }
    }
}
