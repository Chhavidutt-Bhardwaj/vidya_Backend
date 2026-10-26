package com.vidya.master.analytics.controller;

import com.vidya.common.api.ApiResponse;
import com.vidya.common.api.ApiResponseCode;
import com.vidya.master.analytics.dto.OnboardingFunnelResponse;
import com.vidya.master.analytics.dto.PlatformKpiResponse;
import com.vidya.master.analytics.dto.RevenueTrendResponse;
import com.vidya.master.analytics.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST Controller for platform analytics, executive KPIs, revenue forecasting,
 * and onboarding funnel metrics.
 */
@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/kpis")
    public ResponseEntity<ApiResponse<PlatformKpiResponse>> getPlatformKpis() {
        PlatformKpiResponse kpis = analyticsService.getPlatformKpis();
        return ResponseEntity.ok(ApiResponse.success(
                ApiResponseCode.KPIS_FOUND,
                "Platform executive KPIs retrieved successfully",
                kpis
        ));
    }

    @GetMapping("/revenue-trends")
    public ResponseEntity<ApiResponse<List<RevenueTrendResponse>>> getRevenueTrends(
            @RequestParam(defaultValue = "12") int months) {
        List<RevenueTrendResponse> trends = analyticsService.getRevenueTrends(months);
        return ResponseEntity.ok(ApiResponse.success(
                ApiResponseCode.REVENUE_TRENDS_FOUND,
                "Revenue trends retrieved successfully",
                trends
        ));
    }

    @GetMapping("/onboarding-funnel")
    public ResponseEntity<ApiResponse<OnboardingFunnelResponse>> getOnboardingFunnel() {
        OnboardingFunnelResponse funnel = analyticsService.getOnboardingFunnel();
        return ResponseEntity.ok(ApiResponse.success(
                ApiResponseCode.ONBOARDING_FUNNEL_FOUND,
                "School onboarding funnel metrics retrieved successfully",
                funnel
        ));
    }
}
