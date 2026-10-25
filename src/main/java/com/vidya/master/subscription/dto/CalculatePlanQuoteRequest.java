package com.vidya.master.subscription.dto;

import com.vidya.master.subscription.entity.BillingCycle;
import com.vidya.master.subscription.entity.PlanTier;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

/**
 * Request payload for calculating dynamic module-wise pricing quotes.
 */
public record CalculatePlanQuoteRequest(
        PlanTier tier,
        @NotNull(message = "Billing cycle is required")
        BillingCycle billingCycle,
        List<String> selectedFeatureCodes,
        Integer studentCount,
        Integer staffCount,
        BigDecimal discountPercentage
) {
    public CalculatePlanQuoteRequest {
        if (tier == null) {
            tier = PlanTier.CUSTOM;
        }
        if (discountPercentage == null) {
            discountPercentage = BigDecimal.ZERO;
        }
    }
}
