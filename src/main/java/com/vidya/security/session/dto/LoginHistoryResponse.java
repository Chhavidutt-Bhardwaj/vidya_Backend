package com.vidya.security.session.dto;

import com.vidya.security.session.entity.LoginEventType;
import com.vidya.security.session.entity.LoginHistory;

import java.time.Instant;
import java.util.UUID;

public record LoginHistoryResponse(
        UUID id,
        UUID userId,
        String email,
        UUID schoolId,
        LoginEventType eventType,
        String failureReason,
        UUID sessionId,
        String ipAddress,
        String deviceType,
        String browser,
        String operatingSystem,
        Instant occurredAt
) {
    public static LoginHistoryResponse from(LoginHistory h) {
        return new LoginHistoryResponse(h.getId(), h.getUserId(), h.getEmail(), h.getSchoolId(), h.getEventType(),
                h.getFailureReason(), h.getSessionId(), h.getIpAddress(), h.getDeviceType(), h.getBrowser(),
                h.getOperatingSystem(), h.getOccurredAt());
    }
}
