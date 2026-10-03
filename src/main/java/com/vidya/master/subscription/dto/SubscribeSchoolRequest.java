package com.vidya.master.subscription.dto;

import com.vidya.master.subscription.entity.BillingCycle;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request payload for subscribing a school to a plan.
 */
public record SubscribeSchoolRequest(
        @NotNull(message = "School ID is required")
        UUID schoolId,

        @NotNull(message = "Plan ID is required")
        UUID planId,

        BillingCycle billingCycle,

        @DecimalMin(value = "0.00", message = "Discount amount must not be negative")
        BigDecimal discountAmount,

        boolean autoRenew,

        boolean isTrial
) {}
