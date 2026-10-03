package com.vidya.security.service;

import com.vidya.master.audit.entity.AuditLog;
import com.vidya.master.audit.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityAuditServiceImpl implements SecurityAuditService {

    private final AuditLogRepository auditLogRepository;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, transactionManager = "masterTransactionManager")
    public void logLoginSuccess(UUID userId, String email, String ipAddress, String correlationId) {
        log.info("[SecurityAudit] LOGIN_SUCCESS for user: {} (id: {}), ip: {}, correlationId: {}", email, userId, ipAddress, correlationId);
        try {
            AuditLog auditLog = new AuditLog(
                    "SECURITY_AUTH",
                    userId,
                    "LOGIN",
                    null,
                    "{\"email\":\"" + email + "\",\"result\":\"SUCCESS\"}",
                    userId,
                    "PLATFORM_USER",
                    correlationId,
                    ipAddress
            );
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.warn("Failed to persist security audit log for LOGIN_SUCCESS: {}", e.getMessage());
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, transactionManager = "masterTransactionManager")
    public void logLoginFailure(String email, String reason, String ipAddress, String correlationId) {
        log.warn("[SecurityAudit] LOGIN_FAILURE for email: {}, reason: {}, ip: {}, correlationId: {}", email, reason, ipAddress, correlationId);
        try {
            AuditLog auditLog = new AuditLog(
                    "SECURITY_AUTH",
                    UUID.nameUUIDFromBytes(email != null ? email.getBytes() : new byte[0]),
                    "LOGIN",
                    null,
                    "{\"email\":\"" + email + "\",\"result\":\"FAILURE\",\"reason\":\"" + reason + "\"}",
                    null,
                    "EXTERNAL",
                    correlationId,
                    ipAddress
            );
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.warn("Failed to persist security audit log for LOGIN_FAILURE: {}", e.getMessage());
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, transactionManager = "masterTransactionManager")
    public void logLogout(UUID userId, String email, String ipAddress, String correlationId) {
        log.info("[SecurityAudit] LOGOUT for user: {} (id: {}), ip: {}, correlationId: {}", email, userId, ipAddress, correlationId);
        try {
            AuditLog auditLog = new AuditLog(
                    "SECURITY_AUTH",
                    userId,
                    "LOGOUT",
                    null,
                    "{\"email\":\"" + email + "\",\"result\":\"LOGOUT\"}",
                    userId,
                    "PLATFORM_USER",
                    correlationId,
                    ipAddress
            );
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.warn("Failed to persist security audit log for LOGOUT: {}", e.getMessage());
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, transactionManager = "masterTransactionManager")
    public void logTokenRefresh(UUID userId, String email, String ipAddress, String correlationId) {
        log.info("[SecurityAudit] TOKEN_REFRESH for user: {} (id: {}), ip: {}, correlationId: {}", email, userId, ipAddress, correlationId);
        try {
            AuditLog auditLog = new AuditLog(
                    "SECURITY_AUTH",
                    userId,
                    "UPDATE",
                    null,
                    "{\"email\":\"" + email + "\",\"action\":\"TOKEN_REFRESH\"}",
                    userId,
                    "PLATFORM_USER",
                    correlationId,
                    ipAddress
            );
            auditLogRepository.save(auditLog);
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
            AuditLog auditLog = new AuditLog(
                    "SECURITY_ACCESS",
                    userId != null ? userId : UUID.randomUUID(),
                    "ACCESS_DENIED",
                    null,
                    "{\"email\":\"" + email + "\",\"resource\":\"" + resource + "\",\"reason\":\"" + reason + "\"}",
                    userId,
                    "PLATFORM_USER",
                    correlationId,
                    ipAddress
            );
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.warn("Failed to persist security audit log for ACCESS_DENIED: {}", e.getMessage());
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, transactionManager = "masterTransactionManager")
    public void logAccountLocked(UUID userId, String email, String ipAddress, String correlationId) {
        log.warn("[SecurityAudit] ACCOUNT_LOCKED for user: {} (id: {}), ip: {}, correlationId: {}", email, userId, ipAddress, correlationId);
        try {
            AuditLog auditLog = new AuditLog(
                    "SECURITY_AUTH",
                    userId,
                    "STATUS_CHANGE",
                    null,
                    "{\"email\":\"" + email + "\",\"event\":\"ACCOUNT_TEMPORARILY_LOCKED\"}",
                    userId,
                    "SYSTEM",
                    correlationId,
                    ipAddress
            );
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.warn("Failed to persist security audit log for ACCOUNT_LOCKED: {}", e.getMessage());
        }
    }
}
