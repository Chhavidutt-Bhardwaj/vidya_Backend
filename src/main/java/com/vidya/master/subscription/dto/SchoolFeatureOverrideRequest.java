package com.vidya.master.subscription.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

/**
 * Request payload to override a module or granular sub-feature for a specific school subscription.
 */
public record SchoolFeatureOverrideRequest(
        @NotBlank(message = "Feature code is required")
        String featureCode,
        boolean isEnabled,
        Integer customUsageLimit,
        BigDecimal addonPrice,
        String notes
) {}
