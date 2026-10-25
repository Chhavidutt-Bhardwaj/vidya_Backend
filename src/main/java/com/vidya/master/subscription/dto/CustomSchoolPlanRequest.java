package com.vidya.master.subscription.dto;

import com.vidya.master.subscription.entity.BillingCycle;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Request payload for SuperUser or Platform User creating and assigning a bespoke custom plan to a school.
 */
public record CustomSchoolPlanRequest(
        @NotNull(message = "Target School ID is required")
        UUID schoolId,

        @NotBlank(message = "Plan name is required")
        @Size(max = 200, message = "Plan name must not exceed 200 characters")
        String planName,

        String description,

        @NotNull(message = "Billing cycle is required")
        BillingCycle billingCycle,

        @NotNull(message = "Custom base price is required")
        @DecimalMin(value = "0.00", message = "Price must not be negative")
        BigDecimal customBasePrice,

        Integer trialDays,

        @Valid
        List<PlanFeatureRequest> features
) {}
