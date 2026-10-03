package com.vidya.master.subscription.dto;

import com.vidya.master.subscription.entity.BillingCycle;
import com.vidya.master.subscription.entity.PlanStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Response payload representing a versioned subscription plan.
 */
public record SubscriptionPlanResponse(
        UUID id,
        String planCode,
        Integer version,
        String name,
        String description,
        BillingCycle billingCycle,
        BigDecimal basePrice,
        String currency,
        int trialDays,
        PlanStatus status,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        List<PlanFeatureResponse> features
) {}
