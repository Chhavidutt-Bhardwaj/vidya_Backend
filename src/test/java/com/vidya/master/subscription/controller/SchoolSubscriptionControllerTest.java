package com.vidya.master.subscription.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.vidya.common.exception.GlobalExceptionHandler;
import com.vidya.master.subscription.dto.SchoolSubscriptionResponse;
import com.vidya.master.subscription.dto.SubscribeSchoolRequest;
import com.vidya.master.subscription.entity.BillingCycle;
import com.vidya.master.subscription.entity.SubscriptionStatus;
import com.vidya.master.subscription.service.SchoolSubscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SchoolSubscriptionControllerTest {

    @Mock
    private SchoolSubscriptionService schoolSubscriptionService;

    @InjectMocks
    private SchoolSubscriptionController schoolSubscriptionController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private UUID schoolId;
    private UUID planId;
    private UUID subscriptionId;
    private SchoolSubscriptionResponse sampleResponse;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(schoolSubscriptionController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        schoolId = UUID.randomUUID();
        planId = UUID.randomUUID();
        subscriptionId = UUID.randomUUID();

        sampleResponse = new SchoolSubscriptionResponse(
                subscriptionId,
                "SUB-12345678",
                schoolId,
                planId,
                "STANDARD",
                1,
                LocalDate.now(),
                LocalDate.now().plusMonths(1),
                BillingCycle.MONTHLY,
                new BigDecimal("5000.00"),
                BigDecimal.ZERO,
                new BigDecimal("900.00"),
                new BigDecimal("5900.00"),
                "INR",
                SubscriptionStatus.ACTIVE,
                true,
                null,
                Instant.now(),
                Instant.now()
        );
    }

    @Test
    @DisplayName("POST /api/v1/schools/{schoolId}/subscriptions should return 201 Created")
    void subscribeSchool_Success() throws Exception {
        SubscribeSchoolRequest request = new SubscribeSchoolRequest(
                schoolId, planId, BillingCycle.MONTHLY, BigDecimal.ZERO, true, false);

        when(schoolSubscriptionService.subscribeSchool(any())).thenReturn(sampleResponse);

        mockMvc.perform(post("/api/v1/schools/{schoolId}/subscriptions", schoolId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", equalTo(true)))
                .andExpect(jsonPath("$.data.subscriptionCode", equalTo("SUB-12345678")))
                .andExpect(jsonPath("$.data.finalAmount", equalTo(5900.00)));
    }

    @Test
    @DisplayName("GET /api/v1/schools/{schoolId}/subscriptions/active should return active subscription")
    void getActiveSubscription_Success() throws Exception {
        when(schoolSubscriptionService.getActiveSubscriptionForSchool(schoolId)).thenReturn(sampleResponse);

        mockMvc.perform(get("/api/v1/schools/{schoolId}/subscriptions/active", schoolId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", equalTo(true)))
                .andExpect(jsonPath("$.data.schoolId", equalTo(schoolId.toString())));
    }

    @Test
    @DisplayName("GET /api/v1/schools/{schoolId}/subscriptions should return subscription history")
    void getSubscriptionHistory_Success() throws Exception {
        when(schoolSubscriptionService.getSubscriptionHistory(eq(schoolId), any()))
                .thenReturn(new PageImpl<>(List.of(sampleResponse), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/schools/{schoolId}/subscriptions", schoolId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", equalTo(true)))
                .andExpect(jsonPath("$.data", hasSize(1)));
    }

    @Test
    @DisplayName("POST /api/v1/schools/{schoolId}/subscriptions/{id}/cancel should cancel subscription")
    void cancelSubscription_Success() throws Exception {
        when(schoolSubscriptionService.cancelSubscription(subscriptionId)).thenReturn(sampleResponse);

        mockMvc.perform(post("/api/v1/schools/{schoolId}/subscriptions/{id}/cancel", schoolId, subscriptionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", equalTo(true)));
    }
}
