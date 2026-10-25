package com.vidya.master.subscription.dto;

import com.vidya.master.subscription.entity.FeatureCategory;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Granular sub-feature representation within a functional module.
 */
public record ModuleFeatureItemResponse(
        UUID featureId,
        String featureCode,
        String name,
        String description,
        FeatureCategory category,
        String subCategory,
        BigDecimal basePrice,
        boolean isAddon,
        int displayOrder,
        boolean isEnabled,
        Integer usageLimit
) {}
