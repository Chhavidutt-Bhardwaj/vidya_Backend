package com.vidya.master.onboarding.orchestration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.vidya.common.exception.GlobalExceptionHandler;
import com.vidya.master.onboarding.entity.OnboardingStatus;
import com.vidya.master.onboarding.orchestration.dto.CompleteOnboardingRequest;
import com.vidya.master.onboarding.orchestration.dto.OnboardingActivationResponse;
import com.vidya.master.payment.entity.PaymentMethod;
import com.vidya.master.payment.entity.PaymentProvider;
import com.vidya.master.subscription.entity.BillingCycle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OnboardingOrchestrationControllerTest {

    @Mock
    private OnboardingOrchestrationService orchestrationService;

    @InjectMocks
    private OnboardingOrchestrationController orchestrationController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private UUID onboardingId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(orchestrationController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        onboardingId = UUID.randomUUID();
    }

    @Test
    @DisplayName("POST /api/v1/onboarding/{id}/activate should execute pipeline and return 200 OK")
    void completeOnboardingAndActivate_Success() throws Exception {
        CompleteOnboardingRequest request = CompleteOnboardingRequest.builder()
                .planId(UUID.randomUUID())
                .billingCycle(BillingCycle.ANNUAL)
                .paymentProvider(PaymentProvider.MANUAL)
                .paymentMethod(PaymentMethod.NEFT)
                .referenceNumber("NEFT-12345")
                .build();

        OnboardingActivationResponse response = OnboardingActivationResponse.builder()
                .onboardingId(onboardingId)
                .onboardingCode("ONB-12345678")
                .schoolId(UUID.randomUUID())
                .schoolCode("SCH-12345678")
                .tenantId(UUID.randomUUID())
                .tenantCode("gw123456")
                .databaseName("vidya_tenant_gw123456")
                .subscriptionId(UUID.randomUUID())
                .subscriptionCode("SUB-99999")
                .transactionCode("TXN-PAY-001")
                .status(OnboardingStatus.ACTIVE)
                .message("Onboarding completed successfully")
                .activatedAt(Instant.now())
                .build();

        when(orchestrationService.completeOnboardingAndActivate(eq(onboardingId), any()))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/onboarding/{id}/activate", onboardingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", equalTo(true)))
                .andExpect(jsonPath("$.data.onboardingCode", equalTo("ONB-12345678")))
                .andExpect(jsonPath("$.data.tenantCode", equalTo("gw123456")))
                .andExpect(jsonPath("$.data.status", equalTo("ACTIVE")));
    }
}
