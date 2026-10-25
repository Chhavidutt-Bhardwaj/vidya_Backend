package com.vidya.master.subscription.dto;

import java.math.BigDecimal;

/**
 * Itemized line item in a custom plan quote calculation.
 */
public record QuoteItemBreakdown(
        String code,
        String name,
        String category,
        BigDecimal amount,
        String calculationRule
) {}
