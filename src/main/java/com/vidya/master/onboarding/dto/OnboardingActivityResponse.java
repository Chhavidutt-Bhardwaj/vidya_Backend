package com.vidya.master.onboarding.dto;

import com.vidya.master.onboarding.entity.PerformerType;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Response payload representing an audit event in the onboarding trail.
 */
public record OnboardingActivityResponse(
        UUID id,
        String action,
        String previousStatus,
        String newStatus,
        UUID performedBy,
        PerformerType performedByType,
        Map<String, Object> metadata,
        Instant createdAt
) {}
