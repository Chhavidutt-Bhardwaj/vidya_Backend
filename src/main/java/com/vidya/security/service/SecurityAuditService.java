package com.vidya.security.service;

import java.util.UUID;

public interface SecurityAuditService {

    void logLoginSuccess(UUID userId, String email, String ipAddress, String correlationId);

    void logLoginFailure(String email, String reason, String ipAddress, String correlationId);

    void logLogout(UUID userId, String email, String ipAddress, String correlationId);

    void logTokenRefresh(UUID userId, String email, String ipAddress, String correlationId);

    void logAccessDenied(UUID userId, String email, String resource, String reason, String ipAddress, String correlationId);

    void logAccountLocked(UUID userId, String email, String ipAddress, String correlationId);
}
