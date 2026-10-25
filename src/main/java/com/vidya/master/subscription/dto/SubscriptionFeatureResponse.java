package com.vidya.master.subscription.dto;

import com.vidya.master.subscription.entity.FeatureCategory;
import com.vidya.master.subscription.entity.FeatureStatus;

import java.util.UUID;

/**
 * Response payload representing a feature from the platform catalog.
 */
public record SubscriptionFeatureResponse(
        UUID id,
        String featureCode,
        String name,
        String description,
        FeatureCategory category,
        FeatureStatus status,
        String parentModuleCode,
        String subCategory,
        java.math.BigDecimal basePrice,
        boolean isAddon,
        int displayOrder
) {}
