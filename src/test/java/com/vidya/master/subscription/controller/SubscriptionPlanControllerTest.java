package com.vidya.master.subscription.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.vidya.common.exception.GlobalExceptionHandler;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.master.subscription.dto.CreateSubscriptionPlanRequest;
import com.vidya.master.subscription.dto.SubscriptionPlanResponse;
import com.vidya.master.subscription.entity.BillingCycle;
import com.vidya.master.subscription.entity.PlanStatus;
import com.vidya.master.subscription.service.SubscriptionPlanService;
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
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SubscriptionPlanControllerTest {

    @Mock
    private SubscriptionPlanService subscriptionPlanService;

    @InjectMocks
    private SubscriptionPlanController subscriptionPlanController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private UUID planId;
    private SubscriptionPlanResponse samplePlanResponse;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(subscriptionPlanController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        planId = UUID.randomUUID();
        samplePlanResponse = new SubscriptionPlanResponse(
                planId,
                "STANDARD",
                1,
                "Standard Growth Plan",
                "Description",
                BillingCycle.MONTHLY,
                new BigDecimal("4999.00"),
                "INR",
                14,
                PlanStatus.ACTIVE,
                LocalDate.now(),
                null,
                Collections.emptyList()
        );
    }

    @Test
    @DisplayName("GET /api/v1/subscription-plans/catalog should return active public plans")
    void getPublicCatalog_Success() throws Exception {
        when(subscriptionPlanService.getActivePublicPlans()).thenReturn(List.of(samplePlanResponse));

        mockMvc.perform(get("/api/v1/subscription-plans/catalog"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", equalTo(true)))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].planCode", equalTo("STANDARD")));
    }

    @Test
    @DisplayName("GET /api/v1/subscription-plans/{id} should return plan by ID")
    void getPlanById_Success() throws Exception {
        when(subscriptionPlanService.getPlanById(planId)).thenReturn(samplePlanResponse);

        mockMvc.perform(get("/api/v1/subscription-plans/{id}", planId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", equalTo(true)))
                .andExpect(jsonPath("$.data.id", equalTo(planId.toString())));
    }

    @Test
    @DisplayName("GET /api/v1/subscription-plans/{id} with unknown ID should return 404")
    void getPlanById_NotFound() throws Exception {
        UUID unknownId = UUID.randomUUID();
        when(subscriptionPlanService.getPlanById(unknownId))
                .thenThrow(new ResourceNotFoundException("SubscriptionPlan", unknownId));

        mockMvc.perform(get("/api/v1/subscription-plans/{id}", unknownId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success", equalTo(false)))
                .andExpect(jsonPath("$.code", equalTo("RESOURCE_NOT_FOUND")));
    }

    @Test
    @DisplayName("POST /api/v1/subscription-plans should return 201 Created")
    void createPlan_Success() throws Exception {
        CreateSubscriptionPlanRequest request = new CreateSubscriptionPlanRequest(
                "STANDARD", "Standard Growth Plan", "Description",
                BillingCycle.MONTHLY, new BigDecimal("4999.00"), "INR", 14, LocalDate.now(), null);

        when(subscriptionPlanService.createPlan(any())).thenReturn(samplePlanResponse);

        mockMvc.perform(post("/api/v1/subscription-plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", equalTo(true)))
                .andExpect(jsonPath("$.data.planCode", equalTo("STANDARD")));
    }

    @Test
    @DisplayName("PATCH /api/v1/subscription-plans/{id}/publish should publish plan")
    void publishPlan_Success() throws Exception {
        when(subscriptionPlanService.publishPlan(eq(planId))).thenReturn(samplePlanResponse);

        mockMvc.perform(patch("/api/v1/subscription-plans/{id}/publish", planId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", equalTo(true)));
    }
}
