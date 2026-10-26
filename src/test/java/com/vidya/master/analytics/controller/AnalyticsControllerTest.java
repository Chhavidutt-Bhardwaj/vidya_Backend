package com.vidya.master.analytics.controller;

import com.vidya.common.api.ApiResponseCode;
import com.vidya.master.analytics.dto.OnboardingFunnelResponse;
import com.vidya.master.analytics.dto.PlatformKpiResponse;
import com.vidya.master.analytics.dto.RevenueTrendResponse;
import com.vidya.master.analytics.service.AnalyticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("AnalyticsController — Web REST API Tests")
class AnalyticsControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AnalyticsService analyticsService;

    @InjectMocks
    private AnalyticsController analyticsController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(analyticsController).build();
    }

    @Test
    @DisplayName("GET /api/v1/analytics/kpis should return 200 with platform KPIs")
    void shouldReturnPlatformKpis() throws Exception {
        PlatformKpiResponse kpis = new PlatformKpiResponse(
                20L, 15L, 5L, 4L, 5L, 5L, 30L, 12L,
                new BigDecimal("45000.00"), new BigDecimal("540000.00"),
                new BigDecimal("600000.00"), 50L, 3L, 1L, 25L
        );
        when(analyticsService.getPlatformKpis()).thenReturn(kpis);

        mockMvc.perform(get("/api/v1/analytics/kpis"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ApiResponseCode.KPIS_FOUND))
                .andExpect(jsonPath("$.data.totalSchools").value(20))
                .andExpect(jsonPath("$.data.activeSchools").value(15))
                .andExpect(jsonPath("$.data.monthlyRecurringRevenue").value(45000.00));
    }

    @Test
    @DisplayName("GET /api/v1/analytics/revenue-trends should return 200 with trend array")
    void shouldReturnRevenueTrends() throws Exception {
        RevenueTrendResponse trend = new RevenueTrendResponse(
                "2026-10", "Oct 2026", new BigDecimal("50000.00"), 5L, 2L
        );
        when(analyticsService.getRevenueTrends(anyInt())).thenReturn(List.of(trend));

        mockMvc.perform(get("/api/v1/analytics/revenue-trends?months=6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ApiResponseCode.REVENUE_TRENDS_FOUND))
                .andExpect(jsonPath("$.data[0].month").value("2026-10"))
                .andExpect(jsonPath("$.data[0].revenue").value(50000.00));
    }

    @Test
    @DisplayName("GET /api/v1/analytics/onboarding-funnel should return 200 with funnel data")
    void shouldReturnOnboardingFunnel() throws Exception {
        OnboardingFunnelResponse funnel = new OnboardingFunnelResponse(
                10L, 6L, 60.0, Map.of("ACTIVE", 6L, "SUBMITTED", 4L), Map.of("COMMUNITY", 7L, "WEBSITE", 3L)
        );
        when(analyticsService.getOnboardingFunnel()).thenReturn(funnel);

        mockMvc.perform(get("/api/v1/analytics/onboarding-funnel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ApiResponseCode.ONBOARDING_FUNNEL_FOUND))
                .andExpect(jsonPath("$.data.totalApplications").value(10))
                .andExpect(jsonPath("$.data.conversionRate").value(60.0));
    }
}
