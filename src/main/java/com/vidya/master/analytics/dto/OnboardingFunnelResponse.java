package com.vidya.master.analytics.dto;

import java.util.Map;

/**
 * Onboarding pipeline conversion funnel and channel distribution statistics.
 */
public record OnboardingFunnelResponse(
        long totalApplications,
        long activeSchools,
        double conversionRate,
        Map<String, Long> stageCounts,
        Map<String, Long> channelDistribution
) {}
