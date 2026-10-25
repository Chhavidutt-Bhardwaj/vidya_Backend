package com.vidya.master.subscription.dto;

import com.vidya.master.subscription.entity.BillingCycle;
import com.vidya.master.subscription.entity.PlanTier;

import java.math.BigDecimal;
import java.util.List;

/**
 * Calculated price quotation with breakdown of base package, selected modular add-ons,
 * discounts, GST (18%), and net payable total.
 */
public record CalculatePlanQuoteResponse(
        PlanTier tier,
        BillingCycle billingCycle,
        BigDecimal basePrice,
        BigDecimal addonsTotal,
        BigDecimal grossAmount,
        BigDecimal discountAmount,
        BigDecimal taxableAmount,
        BigDecimal taxRate,
        BigDecimal taxAmount,
        BigDecimal netPayableAmount,
        List<QuoteItemBreakdown> itemizedBreakdown
) {}
