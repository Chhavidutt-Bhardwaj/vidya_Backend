package com.vidya.master.onboarding.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request payload for rejecting an onboarding application.
 */
public record RejectOnboardingRequest(
        @NotBlank(message = "Rejection reason is required")
        @Size(max = 1000, message = "Rejection reason must not exceed 1000 characters")
        String rejectionReason
) {}
