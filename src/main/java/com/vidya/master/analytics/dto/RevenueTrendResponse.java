package com.vidya.master.analytics.dto;

import java.math.BigDecimal;

/**
 * Monthly revenue and transaction volume time-series point.
 */
public record RevenueTrendResponse(
        String month,
        String monthName,
        BigDecimal revenue,
        long transactionCount,
        long newSubscriptions
) {}
