package com.vidya.master.subscription.dto;

import com.vidya.master.subscription.entity.PlanTier;

import java.util.List;
import java.util.UUID;

/**
 * 360-degree runtime entitlement payload for a school.
 * Returns the effective feature access hierarchy (Modules -> Sub-Features)
 * after evaluating base plan entitlements + school-specific overrides.
 */
public record SchoolEntitlementResponse(
        UUID schoolId,
        UUID activeSubscriptionId,
        String planCode,
        String planName,
        PlanTier planTier,
        boolean isCustomPlan,
        List<ModuleFeatureGroupResponse> modules,
        List<SchoolFeatureOverrideResponse> overrides
) {}
