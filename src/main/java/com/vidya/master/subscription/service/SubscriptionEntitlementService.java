package com.vidya.master.subscription.service;

import com.vidya.master.subscription.dto.CalculatePlanQuoteRequest;
import com.vidya.master.subscription.dto.CalculatePlanQuoteResponse;
import com.vidya.master.subscription.dto.ModuleFeatureGroupResponse;
import com.vidya.master.subscription.dto.SchoolEntitlementResponse;
import com.vidya.master.subscription.dto.SchoolFeatureOverrideRequest;
import com.vidya.master.subscription.dto.SchoolFeatureOverrideResponse;

import java.util.List;
import java.util.UUID;

/**
 * Service orchestrating granular modular features, sub-categories,
 * dynamic quote calculation, and school-level feature overrides.
 */
public interface SubscriptionEntitlementService {

    /**
     * Retrieves the complete hierarchical runtime entitlements (Modules -> Sub-Features) for a school.
     */
    SchoolEntitlementResponse getSchoolEntitlements(UUID schoolId);

    /**
     * Checks if a specific feature code is enabled for the school.
     */
    boolean isFeatureEnabled(UUID schoolId, String featureCode);

    /**
     * Checks if a functional module (e.g. STAFF_MANAGEMENT, STUDENT_MANAGEMENT) has at least one active feature for the school.
     */
    boolean isModuleEnabled(UUID schoolId, String moduleCode);

    /**
     * Overrides (enables/disables) a specific feature or sub-feature for a school's subscription.
     */
    SchoolFeatureOverrideResponse overrideSchoolFeature(UUID schoolId, SchoolFeatureOverrideRequest request, UUID platformUserId);

    /**
     * Removes an active override and resets the feature back to the base plan default.
     */
    void removeSchoolFeatureOverride(UUID schoolId, String featureCode);

    /**
     * Dynamically calculates pricing quote based on tier, modular selection, and student/staff volume.
     */
    CalculatePlanQuoteResponse calculatePlanQuote(CalculatePlanQuoteRequest request);

    /**
     * Returns the complete modular and sub-feature catalog grouped by parent module.
     */
    List<ModuleFeatureGroupResponse> getModularFeatureCatalog();
}
