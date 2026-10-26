package com.vidya.master.analytics.service;

import com.vidya.master.analytics.dto.OnboardingFunnelResponse;
import com.vidya.master.analytics.dto.PlatformKpiResponse;
import com.vidya.master.analytics.dto.RevenueTrendResponse;

import java.util.List;

/**
 * Service for platform-wide analytics, KPIs, revenue forecasting, and onboarding funnel tracking.
 */
public interface AnalyticsService {

    /**
     * Computes real-time platform executive KPIs across schools, subscriptions, revenue, and tickets.
     */
    PlatformKpiResponse getPlatformKpis();

    /**
     * Computes time-series revenue and transaction trends for the past N months.
     */
    List<RevenueTrendResponse> getRevenueTrends(int months);

    /**
     * Evaluates the onboarding pipeline stages and conversion metrics.
     */
    OnboardingFunnelResponse getOnboardingFunnel();
}
