package com.vidya.master.workflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidya.master.onboarding.entity.OnboardingStatus;
import com.vidya.master.onboarding.entity.OnboardingStep;
import com.vidya.master.payment.entity.PaymentMethod;
import com.vidya.master.payment.entity.PaymentProvider;
import com.vidya.master.payment.entity.PaymentStatus;
import com.vidya.master.school.entity.SchoolStatus;
import com.vidya.master.subscription.entity.BillingCycle;
import com.vidya.master.subscription.entity.SubscriptionStatus;
import com.vidya.master.tenant.entity.TenantStatus;
import com.vidya.master.workflow.admin.dto.InitialSchoolAdminRequest;
import com.vidya.master.workflow.admin.dto.InitialSchoolAdminResponse;
import com.vidya.master.workflow.dto.PlanSelectionRequest;
import com.vidya.master.workflow.dto.PlanSelectionResponse;
import com.vidya.master.workflow.dto.SchoolOnboardingProgressResponse;
import com.vidya.master.workflow.dto.WorkflowActivationResponse;
import com.vidya.master.workflow.dto.WorkflowPaymentInitiateRequest;
import com.vidya.master.workflow.dto.WorkflowPaymentInitiateResponse;
import com.vidya.master.workflow.dto.WorkflowProvisionRequest;
import com.vidya.master.workflow.dto.WorkflowProvisionResponse;
import com.vidya.master.workflow.dto.WorkflowRetryRequest;
import com.vidya.master.workflow.service.SchoolOnboardingWorkflowService;
import com.vidya.security.entity.PlatformRole;
import com.vidya.security.entity.UserStatus;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SchoolOnboardingWorkflowControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock
    private SchoolOnboardingWorkflowService workflowService;

    @InjectMocks
    private SchoolOnboardingWorkflowController workflowController;

    private UUID onboardingId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(workflowController).build();
        objectMapper = new ObjectMapper();
        onboardingId = UUID.randomUUID();
    }

    @Test
    @DisplayName("POST /api/v1/onboarding/{id}/select-plan should lock subscription and return plan snapshot")
    void selectPlan_Success() throws Exception {
        UUID planId = UUID.randomUUID();
        PlanSelectionRequest request = PlanSelectionRequest.builder()
                .planId(planId)
                .billingCycle(BillingCycle.ANNUAL)
                .discountAmount(new BigDecimal("1000.00"))
                .build();

        PlanSelectionResponse response = PlanSelectionResponse.builder()
                .onboardingId(onboardingId)
                .onboardingCode("ONB-12345678")
                .schoolId(UUID.randomUUID())
                .schoolCode("SCH-12345678")
                .subscriptionId(UUID.randomUUID())
                .subscriptionCode("SUB-12345678")
                .planCode("PREMIUM")
                .planVersion(1)
                .finalAmount(new BigDecimal("57820.00"))
                .onboardingStatus(OnboardingStatus.PLAN_SELECTED)
                .nextStep(OnboardingStep.PAYMENT)
                .build();

        when(workflowService.selectPlan(eq(onboardingId), any(PlanSelectionRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/onboarding/{id}/select-plan", onboardingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.planCode").value("PREMIUM"))
                .andExpect(jsonPath("$.data.onboardingStatus").value("PLAN_SELECTED"));
    }

    @Test
    @DisplayName("POST /api/v1/onboarding/{id}/payment/initiate should initiate payment order")
    void initiatePayment_Success() throws Exception {
        WorkflowPaymentInitiateRequest request = WorkflowPaymentInitiateRequest.builder()
                .paymentProvider(PaymentProvider.RAZORPAY)
                .paymentMethod(PaymentMethod.UPI)
                .build();

        WorkflowPaymentInitiateResponse response = WorkflowPaymentInitiateResponse.builder()
                .onboardingId(onboardingId)
                .onboardingCode("ONB-12345678")
                .transactionCode("TXN-12345678")
                .providerOrderId("order_rzp_123")
                .amount(new BigDecimal("57820.00"))
                .paymentProvider(PaymentProvider.RAZORPAY)
                .paymentStatus(PaymentStatus.INITIATED)
                .onboardingStatus(OnboardingStatus.PAYMENT_PENDING)
                .nextStep(OnboardingStep.PAYMENT)
                .build();

        when(workflowService.initiatePayment(eq(onboardingId), any(WorkflowPaymentInitiateRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/onboarding/{id}/payment/initiate", onboardingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PAYMENT_INITIATED"))
                .andExpect(jsonPath("$.data.transactionCode").value("TXN-12345678"));
    }

    @Test
    @DisplayName("POST /api/v1/onboarding/{id}/provision should provision tenant DB")
    void provisionTenant_Success() throws Exception {
        WorkflowProvisionRequest request = WorkflowProvisionRequest.builder()
                .databaseHost("localhost")
                .databasePort(5432)
                .build();

        WorkflowProvisionResponse response = WorkflowProvisionResponse.builder()
                .onboardingId(onboardingId)
                .onboardingCode("ONB-12345678")
                .tenantId(UUID.randomUUID())
                .tenantCode("gw123456")
                .databaseName("vidya_tenant_gw123456")
                .tenantStatus(TenantStatus.PROVISIONED)
                .provisioningStatus("COMPLETED")
                .onboardingStatus(OnboardingStatus.PROVISIONED)
                .nextStep(OnboardingStep.ADMIN_CREATION)
                .build();

        when(workflowService.provisionTenant(eq(onboardingId), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/onboarding/{id}/provision", onboardingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.tenantCode").value("gw123456"))
                .andExpect(jsonPath("$.data.onboardingStatus").value("PROVISIONED"));
    }

    @Test
    @DisplayName("POST /api/v1/onboarding/{id}/admin should provision initial school admin")
    void createInitialAdmin_Success() throws Exception {
        InitialSchoolAdminRequest request = InitialSchoolAdminRequest.builder()
                .email("principal@greenwood.edu.in")
                .firstName("Suresh")
                .lastName("Raina")
                .role(PlatformRole.SCHOOL_OWNER)
                .build();

        InitialSchoolAdminResponse response = InitialSchoolAdminResponse.builder()
                .userId(UUID.randomUUID())
                .userCode("USR-12345678")
                .email("principal@greenwood.edu.in")
                .fullName("Suresh Raina")
                .role(PlatformRole.SCHOOL_OWNER)
                .status(UserStatus.ACTIVE)
                .createdAt(Instant.now())
                .build();

        when(workflowService.createInitialAdmin(eq(onboardingId), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/onboarding/{id}/admin", onboardingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("USER_CREATED"))
                .andExpect(jsonPath("$.data.email").value("principal@greenwood.edu.in"));
    }

    @Test
    @DisplayName("POST /api/v1/onboarding/{id}/retry should retry failed workflow and activate")
    void retryWorkflow_Success() throws Exception {
        WorkflowRetryRequest request = new WorkflowRetryRequest("Retrying after network restoration");

        WorkflowActivationResponse response = WorkflowActivationResponse.builder()
                .onboardingId(onboardingId)
                .schoolCode("SCH-12345678")
                .schoolStatus(SchoolStatus.ACTIVE)
                .tenantCode("gw123456")
                .tenantStatus(TenantStatus.ACTIVE)
                .subscriptionStatus(SubscriptionStatus.ACTIVE)
                .onboardingStatus(OnboardingStatus.ACTIVE)
                .message("Retried successfully")
                .build();

        when(workflowService.retryWorkflow(eq(onboardingId), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/onboarding/{id}/retry", onboardingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("ONBOARDING_APPROVED"))
                .andExpect(jsonPath("$.data.schoolStatus").value("ACTIVE"));
    }

    @Test
    @DisplayName("GET /api/v1/onboarding/{id}/workflow-status should return progress report")
    void getWorkflowProgress_Success() throws Exception {
        SchoolOnboardingProgressResponse response = SchoolOnboardingProgressResponse.builder()
                .onboardingId(onboardingId)
                .onboardingCode("ONB-12345678")
                .onboardingStatus(OnboardingStatus.ACTIVE)
                .schoolCode("SCH-12345678")
                .tenantCode("gw123456")
                .subscriptionCode("SUB-12345678")
                .transactionCode("TXN-12345678")
                .paymentStatus(PaymentStatus.SUCCESS)
                .adminEmail("principal@greenwood.edu.in")
                .build();

        when(workflowService.getWorkflowProgress(onboardingId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/onboarding/{id}/workflow-status", onboardingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("ONBOARDING_FOUND"))
                .andExpect(jsonPath("$.data.onboardingCode").value("ONB-12345678"))
                .andExpect(jsonPath("$.data.paymentStatus").value("SUCCESS"));
    }
}
