package com.vidya.security.session.dto;

import com.vidya.security.session.entity.SessionStatus;
import com.vidya.security.session.entity.UserSession;

import java.time.Instant;
import java.util.UUID;

public record SessionResponse(
        UUID id,
        UUID userId,
        UUID schoolId,
        SessionStatus status,
        boolean current,
        String ipAddress,
        String deviceType,
        String browser,
        String operatingSystem,
        Instant createdAt,
        Instant lastSeenAt,
        Instant expiresAt,
        Instant endedAt,
        String endReason
) {
    public static SessionResponse from(UserSession s, UUID currentSessionId) {
        return new SessionResponse(s.getId(), s.getUserId(), s.getSchoolId(), s.getStatus(),
                s.getId().equals(currentSessionId), s.getIpAddress(), s.getDeviceType(), s.getBrowser(),
                s.getOperatingSystem(), s.getCreatedAt(), s.getLastSeenAt(), s.getExpiresAt(),
                s.getEndedAt(), s.getEndReason());
    }
}
