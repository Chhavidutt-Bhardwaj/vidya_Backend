package com.vidya.master.onboarding.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request payload for verifying onboarding documentation.
 */
public record VerifyOnboardingRequest(
        @NotNull(message = "Verification status flag is required")
        boolean verified,

        String notes
) {}
