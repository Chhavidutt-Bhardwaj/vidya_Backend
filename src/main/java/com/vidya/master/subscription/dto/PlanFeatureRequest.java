package com.vidya.master.subscription.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Associates a feature with a plan version during plan creation.
 */
public record PlanFeatureRequest(
        @NotNull(message = "Feature ID is required")
        UUID featureId,

        boolean included,

        Integer usageLimit
) {}
