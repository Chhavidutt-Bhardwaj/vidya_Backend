package com.vidya.master.subscription.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Representation of an active feature override on a school subscription.
 */
public record SchoolFeatureOverrideResponse(
        UUID id,
        UUID schoolId,
        String featureCode,
        String featureName,
        String parentModuleCode,
        String subCategory,
        boolean isEnabled,
        Integer customUsageLimit,
        BigDecimal addonPrice,
        String notes,
        UUID overriddenBy,
        Instant updatedAt
) {}
