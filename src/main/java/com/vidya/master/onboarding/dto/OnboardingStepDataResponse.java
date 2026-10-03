package com.vidya.master.onboarding.dto;

import com.vidya.master.onboarding.entity.OnboardingStep;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Response payload representing step data in the wizard.
 */
public record OnboardingStepDataResponse(
        UUID id,
        OnboardingStep stepName,
        Map<String, Object> stepData,
        boolean isCompleted,
        Instant completedAt,
        Instant updatedAt
) {}
