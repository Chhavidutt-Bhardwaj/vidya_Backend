package com.vidya.master.onboarding.dto;

import com.vidya.master.onboarding.entity.OnboardingStep;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

/**
 * Request payload for saving or updating step data in the onboarding wizard.
 */
public record SaveStepDataRequest(
        @NotNull(message = "Step name is required")
        OnboardingStep stepName,

        @NotNull(message = "Step data payload is required")
        Map<String, Object> data,

        boolean markCompleted
) {}
