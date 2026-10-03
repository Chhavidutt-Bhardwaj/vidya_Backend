package com.vidya.master.onboarding.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.vidya.common.exception.GlobalExceptionHandler;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.master.onboarding.dto.InitiateOnboardingRequest;
import com.vidya.master.onboarding.dto.OnboardingDetailResponse;
import com.vidya.master.onboarding.dto.OnboardingResponse;
import com.vidya.master.onboarding.dto.RejectOnboardingRequest;
import com.vidya.master.onboarding.dto.SaveStepDataRequest;
import com.vidya.master.onboarding.dto.VerifyOnboardingRequest;
import com.vidya.master.onboarding.entity.OnboardingSource;
import com.vidya.master.onboarding.entity.OnboardingStatus;
import com.vidya.master.onboarding.entity.OnboardingStep;
import com.vidya.master.onboarding.service.OnboardingService;
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

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OnboardingControllerTest {

    @Mock
    private OnboardingService onboardingService;

    @InjectMocks
    private OnboardingController onboardingController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private UUID onboardingId;
    private UUID schoolId;
    private OnboardingResponse sampleResponse;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(onboardingController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        onboardingId = UUID.randomUUID();
        schoolId = UUID.randomUUID();

        sampleResponse = new OnboardingResponse(
                onboardingId,
                "ONB-12345678",
                schoolId,
                "SCH-12345678",
                "Vidya Model School",
                OnboardingSource.DIRECT,
                null,
                null,
                OnboardingStep.BASIC_INFORMATION,
                Collections.emptyList(),
                OnboardingStatus.DRAFT,
                null,
                Instant.now(),
                null,
                null,
                Instant.now(),
                Instant.now()
        );
    }

    @Test
    @DisplayName("POST /api/v1/onboarding should return 201 Created on valid initiation")
    void initiateOnboarding_Success() throws Exception {
        InitiateOnboardingRequest request = new InitiateOnboardingRequest(
                OnboardingSource.DIRECT, null, null, "Vidya Model School", "contact@vidyamodel.edu.in", "+919876543210");

        when(onboardingService.initiateOnboarding(any())).thenReturn(sampleResponse);

        mockMvc.perform(post("/api/v1/onboarding")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", equalTo(true)))
                .andExpect(jsonPath("$.data.onboardingCode", equalTo("ONB-12345678")));
    }

    @Test
    @DisplayName("GET /api/v1/onboarding/{id} should return onboarding summary")
    void getOnboardingById_Success() throws Exception {
        when(onboardingService.getOnboardingById(onboardingId)).thenReturn(sampleResponse);

        mockMvc.perform(get("/api/v1/onboarding/{id}", onboardingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", equalTo(true)))
                .andExpect(jsonPath("$.data.id", equalTo(onboardingId.toString())));
    }

    @Test
    @DisplayName("GET /api/v1/onboarding/{id}/detail should return detail response")
    void getOnboardingDetail_Success() throws Exception {
        OnboardingDetailResponse detailResponse = new OnboardingDetailResponse(
                sampleResponse, Collections.emptyList(), Collections.emptyList());

        when(onboardingService.getOnboardingDetail(onboardingId)).thenReturn(detailResponse);

        mockMvc.perform(get("/api/v1/onboarding/{id}/detail", onboardingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", equalTo(true)))
                .andExpect(jsonPath("$.data.summary.onboardingCode", equalTo("ONB-12345678")));
    }

    @Test
    @DisplayName("PUT /api/v1/onboarding/{id}/steps should update step data")
    void saveStepData_Success() throws Exception {
        SaveStepDataRequest request = new SaveStepDataRequest(
                OnboardingStep.BASIC_INFORMATION,
                Map.of("schoolName", "Vidya Model School"),
                true
        );

        when(onboardingService.saveStepData(eq(onboardingId), any())).thenReturn(sampleResponse);

        mockMvc.perform(put("/api/v1/onboarding/{id}/steps", onboardingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", equalTo(true)));
    }

    @Test
    @DisplayName("POST /api/v1/onboarding/{id}/submit should submit onboarding")
    void submitOnboarding_Success() throws Exception {
        when(onboardingService.submitOnboarding(onboardingId)).thenReturn(sampleResponse);

        mockMvc.perform(post("/api/v1/onboarding/{id}/submit", onboardingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", equalTo(true)));
    }

    @Test
    @DisplayName("POST /api/v1/onboarding/{id}/verify should verify onboarding")
    void verifyOnboarding_Success() throws Exception {
        VerifyOnboardingRequest request = new VerifyOnboardingRequest(true, "Documents verified");
        when(onboardingService.verifyOnboarding(eq(onboardingId), any())).thenReturn(sampleResponse);

        mockMvc.perform(post("/api/v1/onboarding/{id}/verify", onboardingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", equalTo(true)));
    }

    @Test
    @DisplayName("POST /api/v1/onboarding/{id}/approve should approve onboarding")
    void approveOnboarding_Success() throws Exception {
        when(onboardingService.approveOnboarding(eq(onboardingId), any())).thenReturn(sampleResponse);

        mockMvc.perform(post("/api/v1/onboarding/{id}/approve", onboardingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", equalTo(true)));
    }

    @Test
    @DisplayName("POST /api/v1/onboarding/{id}/reject should reject onboarding")
    void rejectOnboarding_Success() throws Exception {
        RejectOnboardingRequest request = new RejectOnboardingRequest("Invalid accreditation");
        when(onboardingService.rejectOnboarding(eq(onboardingId), any())).thenReturn(sampleResponse);

        mockMvc.perform(post("/api/v1/onboarding/{id}/reject", onboardingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", equalTo(true)));
    }
}
