package com.vidya.master.analytics.dto;

import java.math.BigDecimal;

/**
 * Top-level Key Performance Indicators (KPIs) for the Platform Executive Dashboard.
 */
public record PlatformKpiResponse(
        long totalSchools,
        long activeSchools,
        long draftSchools,
        long pendingOnboardings,
        long totalCommunities,
        long activeCommunities,
        long totalUsers,
        long activeSubscriptions,
        BigDecimal monthlyRecurringRevenue,
        BigDecimal annualRecurringRevenue,
        BigDecimal totalSettledRevenue,
        long totalTransactions,
        long openSupportQueries,
        long escalatedSupportQueries,
        long resolvedSupportQueries
) {}
