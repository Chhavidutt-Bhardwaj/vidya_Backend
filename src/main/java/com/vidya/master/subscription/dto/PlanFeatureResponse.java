package com.vidya.master.subscription.dto;

import com.vidya.master.subscription.entity.FeatureCategory;

import java.util.UUID;

/**
 * Response payload representing a feature configured within a plan version.
 */
public record PlanFeatureResponse(
        UUID featureId,
        String featureCode,
        String featureName,
        FeatureCategory category,
        boolean isIncluded,
        Integer usageLimit
) {}
