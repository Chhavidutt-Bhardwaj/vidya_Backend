package com.vidya.master.subscription.dto;

import com.vidya.master.subscription.entity.BillingCycle;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Request payload for creating a new subscription plan or new version.
 */
public record CreateSubscriptionPlanRequest(
        @NotBlank(message = "Plan code is required")
        @Size(max = 50, message = "Plan code must not exceed 50 characters")
        String planCode,

        @NotBlank(message = "Plan name is required")
        @Size(max = 200, message = "Plan name must not exceed 200 characters")
        String name,

        String description,

        @NotNull(message = "Billing cycle is required")
        BillingCycle billingCycle,

        @NotNull(message = "Base price is required")
        @DecimalMin(value = "0.00", message = "Base price must not be negative")
        BigDecimal basePrice,

        @Size(max = 10, message = "Currency must not exceed 10 characters")
        String currency,

        @Min(value = 0, message = "Trial days must not be negative")
        int trialDays,

        LocalDate effectiveFrom,

        @Valid
        List<PlanFeatureRequest> features
) {}
