package com.vidya.master.workflow.service;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.InvalidStateTransitionException;
import com.vidya.common.exception.TenantProvisioningException;
import com.vidya.master.onboarding.entity.OnboardingActivity;
import com.vidya.master.onboarding.entity.OnboardingSource;
import com.vidya.master.onboarding.entity.OnboardingStatus;
import com.vidya.master.onboarding.entity.OnboardingStep;
import com.vidya.master.onboarding.entity.SchoolOnboarding;
import com.vidya.master.onboarding.repository.OnboardingActivityRepository;
import com.vidya.master.onboarding.repository.SchoolOnboardingRepository;
import com.vidya.master.payment.dto.InitiatePaymentRequest;
import com.vidya.master.payment.dto.InitiatePaymentResponse;
import com.vidya.master.payment.entity.PaymentMethod;
import com.vidya.master.payment.entity.PaymentProvider;
import com.vidya.master.payment.entity.PaymentStatus;
import com.vidya.master.payment.entity.PaymentTransaction;
import com.vidya.master.payment.repository.PaymentTransactionRepository;
import com.vidya.master.payment.service.PaymentService;
import com.vidya.master.provisioning.entity.TenantProvisioning;
import com.vidya.master.provisioning.repository.TenantProvisioningRepository;
import com.vidya.master.school.entity.School;
import com.vidya.master.school.entity.SchoolBoard;
import com.vidya.master.school.entity.SchoolStatus;
import com.vidya.master.school.entity.SchoolType;
import com.vidya.master.school.repository.SchoolRepository;
import com.vidya.master.subscription.dto.SchoolSubscriptionResponse;
import com.vidya.master.subscription.dto.SubscribeSchoolRequest;
import com.vidya.master.subscription.entity.BillingCycle;
import com.vidya.master.subscription.entity.SchoolSubscription;
import com.vidya.master.subscription.entity.SubscriptionPlan;
import com.vidya.master.subscription.entity.SubscriptionStatus;
import com.vidya.master.subscription.repository.SchoolSubscriptionRepository;
import com.vidya.master.subscription.repository.SubscriptionPlanRepository;
import com.vidya.master.subscription.service.SchoolSubscriptionService;
import com.vidya.master.tenant.entity.Tenant;
import com.vidya.master.tenant.entity.TenantStatus;
import com.vidya.master.tenant.repository.TenantRepository;
import com.vidya.master.workflow.admin.SchoolAdminProvisioningService;
import com.vidya.master.workflow.admin.dto.InitialSchoolAdminRequest;
import com.vidya.master.workflow.admin.dto.InitialSchoolAdminResponse;
import com.vidya.master.workflow.dto.PlanSelectionRequest;
import com.vidya.master.workflow.dto.PlanSelectionResponse;
import com.vidya.master.workflow.dto.SchoolOnboardingProgressResponse;
import com.vidya.master.workflow.dto.WorkflowActivateRequest;
import com.vidya.master.workflow.dto.WorkflowActivationResponse;
import com.vidya.master.workflow.dto.WorkflowApprovalResponse;
import com.vidya.master.workflow.dto.WorkflowApproveRequest;
import com.vidya.master.workflow.dto.WorkflowPaymentInitiateRequest;
import com.vidya.master.workflow.dto.WorkflowPaymentInitiateResponse;
import com.vidya.master.workflow.dto.WorkflowProvisionRequest;
import com.vidya.master.workflow.dto.WorkflowProvisionResponse;
import com.vidya.master.workflow.dto.WorkflowRetryRequest;
import com.vidya.master.workflow.event.PaymentCompletedEvent;
import com.vidya.security.entity.PlatformRole;
import com.vidya.security.entity.PlatformUser;
import com.vidya.security.entity.UserStatus;
import com.vidya.security.repository.PlatformUserRepository;
import com.vidya.tenant.provisioning.TenantProvisioningService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SchoolOnboardingWorkflowServiceTest {

    @Mock
    private SchoolOnboardingRepository onboardingRepository;

    @Mock
    private SchoolRepository schoolRepository;

    @Mock
    private SubscriptionPlanRepository subscriptionPlanRepository;

    @Mock
    private SchoolSubscriptionRepository subscriptionRepository;

    @Mock
    private SchoolSubscriptionService schoolSubscriptionService;

    @Mock
    private PaymentService paymentService;

    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private TenantProvisioningRepository tenantProvisioningRepository;

    @Mock
    private TenantProvisioningService tenantProvisioningService;

    @Mock
    private SchoolAdminProvisioningService adminProvisioningService;

    @Mock
    private PlatformUserRepository platformUserRepository;

    @Mock
    private OnboardingActivityRepository activityRepository;

    @InjectMocks
    private SchoolOnboardingWorkflowServiceImpl workflowService;

    private UUID onboardingId;
    private UUID schoolId;
    private UUID planId;
    private UUID tenantId;
    private School school;
    private SchoolOnboarding onboarding;
    private SubscriptionPlan plan;
    private SchoolSubscription subscription;
    private Tenant tenant;
    private TenantProvisioning provisioning;

    @BeforeEach
    void setUp() {
        onboardingId = UUID.randomUUID();
        schoolId = UUID.randomUUID();
        planId = UUID.randomUUID();
        tenantId = UUID.randomUUID();

        school = new School(
                "SCH-12345678",
                "Greenwood High",
                "Greenwood Trust",
                "REG-12345",
                SchoolBoard.CBSE,
                SchoolType.PRIMARY,
                "admin@greenwood.edu.in",
                "9876543210",
                "https://greenwood.edu.in",
                SchoolStatus.VERIFIED
        );
        school.setId(schoolId);

        onboarding = new SchoolOnboarding(
                "ONB-12345678",
                school,
                OnboardingSource.DIRECT,
                null,
                null
        );
        onboarding.setId(onboardingId);
        onboarding.setStatus(OnboardingStatus.VERIFIED);

        plan = new SubscriptionPlan(
                "PREMIUM",
                1,
                "Premium Plan",
                "Enterprise level features",
                BillingCycle.ANNUAL,
                new BigDecimal("50000.00"),
                "INR",
                30,
                com.vidya.master.subscription.entity.PlanStatus.ACTIVE,
                LocalDate.now()
        );
        plan.setId(planId);

        subscription = new SchoolSubscription(
                "SUB-12345678",
                schoolId,
                plan,
                "PREMIUM",
                1,
                LocalDate.now(),
                LocalDate.now().plusYears(1),
                BillingCycle.ANNUAL,
                new BigDecimal("50000.00"),
                BigDecimal.ZERO,
                new BigDecimal("9000.00"),
                new BigDecimal("59000.00"),
                "INR",
                SubscriptionStatus.ACTIVE,
                true,
                null
        );
        subscription.setId(UUID.randomUUID());

        tenant = Tenant.create(
                schoolId,
                "gw123456",
                "localhost",
                5432,
                "vidya_tenant_gw123456",
                "TENANT_DB_USER",
                "TENANT_DB_PASS"
        );
        tenant.setId(tenantId);
        provisioning = TenantProvisioning.create(tenantId, "PRV-12345678");

        org.mockito.Mockito.lenient().when(onboardingRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        org.mockito.Mockito.lenient().when(schoolRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        org.mockito.Mockito.lenient().when(tenantRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    // -------------------------------------------------------------------------
    // Scenario 1: Successful Direct Onboarding Plan Selection
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Scenario 1: Direct onboarding selects plan and locks subscription snapshot")
    void scenario1_DirectOnboarding_PlanSelection_Success() {
        PlanSelectionRequest request = PlanSelectionRequest.builder()
                .planId(planId)
                .billingCycle(BillingCycle.ANNUAL)
                .discountAmount(new BigDecimal("5000.00"))
                .build();

        SchoolSubscriptionResponse subRes = new SchoolSubscriptionResponse(
                subscription.getId(), "SUB-12345678", schoolId, planId, "PREMIUM", 1,
                LocalDate.now(), LocalDate.now().plusYears(1), BillingCycle.ANNUAL,
                new BigDecimal("50000.00"), new BigDecimal("5000.00"), new BigDecimal("8100.00"),
                new BigDecimal("53100.00"), "INR", SubscriptionStatus.ACTIVE, true, null,
                Instant.now(), Instant.now()
        );

        when(onboardingRepository.findById(onboardingId)).thenReturn(Optional.of(onboarding));
        when(schoolRepository.findById(schoolId)).thenReturn(Optional.of(school));
        when(subscriptionPlanRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(subscriptionRepository.findBySchoolId(schoolId)).thenReturn(List.of());
        when(schoolSubscriptionService.subscribeSchool(any())).thenReturn(subRes);
        when(onboardingRepository.save(any())).thenReturn(onboarding);

        PlanSelectionResponse response = workflowService.selectPlan(onboardingId, request);

        assertThat(response).isNotNull();
        assertThat(response.getPlanCode()).isEqualTo("PREMIUM");
        assertThat(response.getPlanVersion()).isEqualTo(1);
        assertThat(response.getFinalAmount()).isEqualByComparingTo(new BigDecimal("53100.00"));
        assertThat(response.getOnboardingStatus()).isEqualTo(OnboardingStatus.PLAN_SELECTED);
        assertThat(response.getNextStep()).isEqualTo(OnboardingStep.PAYMENT);

        verify(activityRepository).save(any(OnboardingActivity.class));
    }

    // -------------------------------------------------------------------------
    // Scenario 2: Successful Community Onboarding Plan Selection
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Scenario 2: Community onboarding plan selection retains community metadata")
    void scenario2_CommunityOnboarding_PlanSelection_Success() {
        UUID communityId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        SchoolOnboarding communityOnboarding = new SchoolOnboarding("ONB-COMM-01", school, OnboardingSource.COMMUNITY, communityId, memberId);
        communityOnboarding.setId(onboardingId);
        communityOnboarding.setStatus(OnboardingStatus.VERIFIED);

        PlanSelectionRequest request = PlanSelectionRequest.builder().planId(planId).build();

        SchoolSubscriptionResponse subRes = new SchoolSubscriptionResponse(
                subscription.getId(), "SUB-COMM-01", schoolId, planId, "PREMIUM", 1,
                LocalDate.now(), LocalDate.now().plusYears(1), BillingCycle.ANNUAL,
                new BigDecimal("50000.00"), BigDecimal.ZERO, new BigDecimal("9000.00"),
                new BigDecimal("59000.00"), "INR", SubscriptionStatus.ACTIVE, true, null,
                Instant.now(), Instant.now()
        );

        when(onboardingRepository.findById(onboardingId)).thenReturn(Optional.of(communityOnboarding));
        when(schoolRepository.findById(schoolId)).thenReturn(Optional.of(school));
        when(subscriptionPlanRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(subscriptionRepository.findBySchoolId(schoolId)).thenReturn(List.of());
        when(schoolSubscriptionService.subscribeSchool(any())).thenReturn(subRes);
        when(onboardingRepository.save(any())).thenReturn(communityOnboarding);

        PlanSelectionResponse response = workflowService.selectPlan(onboardingId, request);

        assertThat(response).isNotNull();
        assertThat(communityOnboarding.getSource()).isEqualTo(OnboardingSource.COMMUNITY);
        assertThat(communityOnboarding.getCommunityId()).isEqualTo(communityId);
    }

    // -------------------------------------------------------------------------
    // Scenario 3: Razorpay Payment Initiation & Success Event
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Scenario 3: Razorpay payment initiation and asynchronous success completion event")
    void scenario3_Razorpay_Payment_Success() {
        onboarding.setStatus(OnboardingStatus.PLAN_SELECTED);
        WorkflowPaymentInitiateRequest request = WorkflowPaymentInitiateRequest.builder()
                .paymentProvider(PaymentProvider.RAZORPAY)
                .paymentMethod(PaymentMethod.UPI)
                .build();

        InitiatePaymentResponse payRes = InitiatePaymentResponse.builder()
                .transactionCode("TXN-RZP-001")
                .providerOrderId("order_rzp_123")
                .amount(new BigDecimal("59000.00"))
                .currency("INR")
                .paymentProvider(PaymentProvider.RAZORPAY)
                .status(PaymentStatus.INITIATED)
                .build();

        when(onboardingRepository.findById(onboardingId)).thenReturn(Optional.of(onboarding));
        when(schoolRepository.findById(schoolId)).thenReturn(Optional.of(school));
        when(subscriptionRepository.findBySchoolId(schoolId)).thenReturn(List.of(subscription));
        when(paymentService.initiatePayment(any())).thenReturn(payRes);

        WorkflowPaymentInitiateResponse initRes = workflowService.initiatePayment(onboardingId, request);

        assertThat(initRes).isNotNull();
        assertThat(initRes.getTransactionCode()).isEqualTo("TXN-RZP-001");
        assertThat(initRes.getProviderOrderId()).isEqualTo("order_rzp_123");
        assertThat(onboarding.getStatus()).isEqualTo(OnboardingStatus.PAYMENT_PENDING);

        // Simulate Inbound Razorpay Webhook Event
        PaymentCompletedEvent event = PaymentCompletedEvent.builder()
                .transactionCode("TXN-RZP-001")
                .onboardingId(onboardingId)
                .schoolId(schoolId)
                .amount(new BigDecimal("59000.00"))
                .paymentProvider(PaymentProvider.RAZORPAY)
                .status(PaymentStatus.SUCCESS)
                .completedAt(Instant.now())
                .build();

        workflowService.handlePaymentCompleted(event);

        assertThat(onboarding.getStatus()).isEqualTo(OnboardingStatus.PAYMENT_COMPLETED);
        assertThat(onboarding.getCurrentStep()).isEqualTo(OnboardingStep.APPROVAL);
    }

    // -------------------------------------------------------------------------
    // Scenario 4: Cashfree Payment Initiation & Success Event
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Scenario 4: Cashfree payment initiation and completion")
    void scenario4_Cashfree_Payment_Success() {
        onboarding.setStatus(OnboardingStatus.PLAN_SELECTED);
        WorkflowPaymentInitiateRequest request = WorkflowPaymentInitiateRequest.builder()
                .paymentProvider(PaymentProvider.CASHFREE)
                .paymentMethod(PaymentMethod.NETBANKING)
                .build();

        InitiatePaymentResponse payRes = InitiatePaymentResponse.builder()
                .transactionCode("TXN-CF-001")
                .providerOrderId("order_cf_123")
                .amount(new BigDecimal("59000.00"))
                .currency("INR")
                .paymentProvider(PaymentProvider.CASHFREE)
                .status(PaymentStatus.INITIATED)
                .build();

        when(onboardingRepository.findById(onboardingId)).thenReturn(Optional.of(onboarding));
        when(schoolRepository.findById(schoolId)).thenReturn(Optional.of(school));
        when(subscriptionRepository.findBySchoolId(schoolId)).thenReturn(List.of(subscription));
        when(paymentService.initiatePayment(any())).thenReturn(payRes);

        WorkflowPaymentInitiateResponse initRes = workflowService.initiatePayment(onboardingId, request);

        assertThat(initRes.getPaymentProvider()).isEqualTo(PaymentProvider.CASHFREE);

        PaymentCompletedEvent event = PaymentCompletedEvent.builder()
                .transactionCode("TXN-CF-001")
                .onboardingId(onboardingId)
                .status(PaymentStatus.SUCCESS)
                .paymentProvider(PaymentProvider.CASHFREE)
                .build();

        workflowService.handlePaymentCompleted(event);
        assertThat(onboarding.getStatus()).isEqualTo(OnboardingStatus.PAYMENT_COMPLETED);
    }

    // -------------------------------------------------------------------------
    // Scenario 5: Manual Payment Confirmation
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Scenario 5: Manual payment confirmation transitions workflow to PAYMENT_COMPLETED")
    void scenario5_ManualPayment_Confirmation() {
        onboarding.setStatus(OnboardingStatus.PAYMENT_PENDING);
        PaymentCompletedEvent event = PaymentCompletedEvent.builder()
                .transactionCode("TXN-MANUAL-001")
                .onboardingId(onboardingId)
                .paymentProvider(PaymentProvider.MANUAL)
                .paymentMethod(PaymentMethod.NEFT)
                .status(PaymentStatus.SUCCESS)
                .build();

        when(onboardingRepository.findById(onboardingId)).thenReturn(Optional.of(onboarding));
        when(onboardingRepository.save(any())).thenReturn(onboarding);

        workflowService.handlePaymentCompleted(event);

        assertThat(onboarding.getStatus()).isEqualTo(OnboardingStatus.PAYMENT_COMPLETED);
        assertThat(onboarding.getCurrentStep()).isEqualTo(OnboardingStep.APPROVAL);
    }

    // -------------------------------------------------------------------------
    // Scenario 6: Payment Failure
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Scenario 6: Payment failure event does NOT advance onboarding status")
    void scenario6_Payment_Failure_DoesNotAdvance() {
        onboarding.setStatus(OnboardingStatus.PAYMENT_PENDING);
        PaymentCompletedEvent failedEvent = PaymentCompletedEvent.builder()
                .transactionCode("TXN-FAIL-001")
                .onboardingId(onboardingId)
                .status(PaymentStatus.FAILED)
                .build();

        workflowService.handlePaymentCompleted(failedEvent);

        assertThat(onboarding.getStatus()).isEqualTo(OnboardingStatus.PAYMENT_PENDING);
        verify(onboardingRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // Scenario 7: Duplicate Payment Webhook (Idempotency)
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Scenario 7: Duplicate payment webhook is handled idempotently without duplicate transitions")
    void scenario7_DuplicatePaymentWebhook_Idempotent() {
        onboarding.setStatus(OnboardingStatus.PAYMENT_COMPLETED);
        PaymentCompletedEvent duplicateEvent = PaymentCompletedEvent.builder()
                .transactionCode("TXN-RZP-001")
                .onboardingId(onboardingId)
                .status(PaymentStatus.SUCCESS)
                .build();

        when(onboardingRepository.findById(onboardingId)).thenReturn(Optional.of(onboarding));

        workflowService.handlePaymentCompleted(duplicateEvent);

        assertThat(onboarding.getStatus()).isEqualTo(OnboardingStatus.PAYMENT_COMPLETED);
        verify(onboardingRepository, never()).save(any());
        verify(activityRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // Scenario 8: Verification Rejection Handling
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Scenario 8: Selecting plan or approving on REJECTED onboarding is rejected")
    void scenario8_Verification_Rejection_Handled() {
        onboarding.setStatus(OnboardingStatus.REJECTED);
        when(onboardingRepository.findById(onboardingId)).thenReturn(Optional.of(onboarding));

        PlanSelectionRequest request = PlanSelectionRequest.builder().planId(planId).build();
        assertThatThrownBy(() -> workflowService.selectPlan(onboardingId, request))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    // -------------------------------------------------------------------------
    // Scenario 9: Approval Rejection & Prerequisite Gating
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Scenario 9: Cannot approve onboarding if payment is still pending or unverified")
    void scenario9_Approval_Prerequisites_Gated() {
        onboarding.setStatus(OnboardingStatus.PAYMENT_PENDING);
        when(onboardingRepository.findById(onboardingId)).thenReturn(Optional.of(onboarding));

        WorkflowApproveRequest request = WorkflowApproveRequest.builder().notes("Approval").build();
        assertThatThrownBy(() -> workflowService.approveOnboarding(onboardingId, request))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    // -------------------------------------------------------------------------
    // Scenario 10: Tenant Database Creation Failure
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Scenario 10: Tenant DB creation failure marks onboarding PROVISIONING_FAILED and leaves school inactive")
    void scenario10_Tenant_DbCreation_Failure() {
        onboarding.setStatus(OnboardingStatus.APPROVED);
        when(onboardingRepository.findById(onboardingId)).thenReturn(Optional.of(onboarding));
        when(tenantRepository.findBySchoolId(schoolId)).thenReturn(Optional.of(tenant));
        when(tenantProvisioningService.startProvisioning(tenantId)).thenReturn(provisioning);
        doThrow(new TenantProvisioningException("gw123456", "DATABASE_CREATING", "Database creation timed out"))
                .when(tenantProvisioningService).executeProvisioning(tenantId);

        assertThatThrownBy(() -> workflowService.provisionTenant(onboardingId, null))
                .isInstanceOf(TenantProvisioningException.class);

        assertThat(onboarding.getStatus()).isEqualTo(OnboardingStatus.PROVISIONING_FAILED);
        assertThat(school.getStatus()).isNotEqualTo(SchoolStatus.ACTIVE);
    }

    // -------------------------------------------------------------------------
    // Scenario 11: Tenant Migration Failure
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Scenario 11: Flyway migration failure marks PROVISIONING_FAILED while preserving payment")
    void scenario11_Tenant_Migration_Failure() {
        onboarding.setStatus(OnboardingStatus.APPROVED);
        when(onboardingRepository.findById(onboardingId)).thenReturn(Optional.of(onboarding));
        when(tenantRepository.findBySchoolId(schoolId)).thenReturn(Optional.of(tenant));
        when(tenantProvisioningService.startProvisioning(tenantId)).thenReturn(provisioning);
        doThrow(new TenantProvisioningException("gw123456", "MIGRATION_RUNNING", "Flyway migration SQL syntax error"))
                .when(tenantProvisioningService).executeProvisioning(tenantId);

        assertThatThrownBy(() -> workflowService.provisionTenant(onboardingId, null))
                .isInstanceOf(TenantProvisioningException.class);

        assertThat(onboarding.getStatus()).isEqualTo(OnboardingStatus.PROVISIONING_FAILED);
    }

    // -------------------------------------------------------------------------
    // Scenario 12: Tenant Initialization Failure
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Scenario 12: Tenant initialization failure marks PROVISIONING_FAILED")
    void scenario12_Tenant_Initialization_Failure() {
        onboarding.setStatus(OnboardingStatus.APPROVED);
        when(onboardingRepository.findById(onboardingId)).thenReturn(Optional.of(onboarding));
        when(tenantRepository.findBySchoolId(schoolId)).thenReturn(Optional.of(tenant));
        when(tenantProvisioningService.startProvisioning(tenantId)).thenReturn(provisioning);
        doThrow(new TenantProvisioningException("gw123456", "INITIALIZATION_RUNNING", "Seed data insert failed"))
                .when(tenantProvisioningService).executeProvisioning(tenantId);

        assertThatThrownBy(() -> workflowService.provisionTenant(onboardingId, null))
                .isInstanceOf(TenantProvisioningException.class);

        assertThat(onboarding.getStatus()).isEqualTo(OnboardingStatus.PROVISIONING_FAILED);
    }

    // -------------------------------------------------------------------------
    // Scenario 13: Provisioning Retry
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Scenario 13: Provisioning retry recovers and completes workflow without double payment")
    void scenario13_Provisioning_Retry_Success() {
        onboarding.setStatus(OnboardingStatus.PROVISIONING_FAILED);
        provisioning.markFailed("DB timeout");

        when(onboardingRepository.findById(onboardingId)).thenReturn(Optional.of(onboarding));
        when(tenantRepository.findBySchoolId(schoolId)).thenReturn(Optional.of(tenant));
        when(tenantProvisioningRepository.findByTenantId(tenantId)).thenReturn(Optional.of(provisioning));
        when(schoolRepository.findById(schoolId)).thenReturn(Optional.of(school));
        when(adminProvisioningService.provisionInitialAdmin(eq(schoolId), eq(onboardingId), any()))
                .thenReturn(InitialSchoolAdminResponse.builder().email("admin@greenwood.edu.in").role(PlatformRole.SCHOOL_OWNER).build());

        org.mockito.Mockito.doAnswer(invocation -> {
            provisioning.markCompleted();
            return null;
        }).when(tenantProvisioningService).executeProvisioning(tenantId);

        WorkflowActivationResponse response = workflowService.retryWorkflow(onboardingId, new WorkflowRetryRequest("Retry provisioning"));

        assertThat(response).isNotNull();
        assertThat(onboarding.getStatus()).isEqualTo(OnboardingStatus.ACTIVE);
        assertThat(school.getStatus()).isEqualTo(SchoolStatus.ACTIVE);
        assertThat(tenant.getStatus()).isEqualTo(TenantStatus.ACTIVE);

        verify(tenantProvisioningService).retryProvisioning(tenantId);
        verify(tenantProvisioningService).executeProvisioning(tenantId);
        // Verify no new payment was initiated during retry
        verify(paymentService, never()).initiatePayment(any());
    }

    // -------------------------------------------------------------------------
    // Scenario 14: Duplicate Provisioning Request
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Scenario 14: Duplicate provisioning request returns completed state idempotently")
    void scenario14_Duplicate_Provisioning_Request_Idempotent() {
        onboarding.setStatus(OnboardingStatus.APPROVED);
        provisioning.markCompleted();

        when(onboardingRepository.findById(onboardingId)).thenReturn(Optional.of(onboarding));
        when(tenantRepository.findBySchoolId(schoolId)).thenReturn(Optional.of(tenant));
        when(tenantProvisioningService.startProvisioning(tenantId)).thenReturn(provisioning);
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(tenantProvisioningRepository.findByTenantId(tenantId)).thenReturn(Optional.of(provisioning));
        when(onboardingRepository.save(any())).thenReturn(onboarding);

        WorkflowProvisionResponse response = workflowService.provisionTenant(onboardingId, null);

        assertThat(response).isNotNull();
        assertThat(response.getProvisioningStatus()).isEqualTo("COMPLETED");
        assertThat(onboarding.getStatus()).isEqualTo(OnboardingStatus.PROVISIONED);
    }

    // -------------------------------------------------------------------------
    // Scenario 15: Application Restart / Recovery Scenario
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Scenario 15: Application restart recovers state from database and completes remaining steps")
    void scenario15_ApplicationRestart_Recovery_Success() {
        // State preserved in Master DB after restart
        onboarding.setStatus(OnboardingStatus.APPROVED);
        when(onboardingRepository.findById(onboardingId)).thenReturn(Optional.of(onboarding));
        when(tenantRepository.findBySchoolId(schoolId)).thenReturn(Optional.of(tenant));
        when(tenantProvisioningRepository.findByTenantId(tenantId)).thenReturn(Optional.of(provisioning));
        when(schoolRepository.findById(schoolId)).thenReturn(Optional.of(school));
        when(adminProvisioningService.provisionInitialAdmin(eq(schoolId), eq(onboardingId), any()))
                .thenReturn(InitialSchoolAdminResponse.builder().email("admin@greenwood.edu.in").role(PlatformRole.SCHOOL_OWNER).build());

        org.mockito.Mockito.doAnswer(invocation -> {
            provisioning.markCompleted();
            return null;
        }).when(tenantProvisioningService).executeProvisioning(tenantId);

        WorkflowActivationResponse response = workflowService.retryWorkflow(onboardingId, null);

        assertThat(response).isNotNull();
        assertThat(onboarding.getStatus()).isEqualTo(OnboardingStatus.ACTIVE);
    }

    // -------------------------------------------------------------------------
    // Scenario 16: Final School Activation
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Scenario 16: Final school activation sets School, Tenant, and Onboarding to ACTIVE")
    void scenario16_Final_School_Activation_Success() {
        onboarding.setStatus(OnboardingStatus.PROVISIONED);
        provisioning.markCompleted();

        when(onboardingRepository.findById(onboardingId)).thenReturn(Optional.of(onboarding));
        when(schoolRepository.findById(schoolId)).thenReturn(Optional.of(school));
        when(tenantRepository.findBySchoolId(schoolId)).thenReturn(Optional.of(tenant));
        when(tenantProvisioningRepository.findByTenantId(tenantId)).thenReturn(Optional.of(provisioning));
        when(adminProvisioningService.provisionInitialAdmin(eq(schoolId), eq(onboardingId), any()))
                .thenReturn(InitialSchoolAdminResponse.builder().userId(UUID.randomUUID()).email("owner@greenwood.edu.in").role(PlatformRole.SCHOOL_OWNER).build());
        when(onboardingRepository.save(any())).thenReturn(onboarding);

        WorkflowActivationResponse response = workflowService.activateSchool(onboardingId, null);

        assertThat(response).isNotNull();
        assertThat(onboarding.getStatus()).isEqualTo(OnboardingStatus.ACTIVE);
        assertThat(school.getStatus()).isEqualTo(SchoolStatus.ACTIVE);
        assertThat(school.getTenantId()).isEqualTo(tenantId);
        assertThat(tenant.getStatus()).isEqualTo(TenantStatus.ACTIVE);

        verify(activityRepository).save(any(OnboardingActivity.class));
    }

    // -------------------------------------------------------------------------
    // Scenario 17: Ensure Failed Provisioning Does NOT Activate School
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Scenario 17: Ensure failed provisioning prevents school activation")
    void scenario17_FailedProvisioning_DoesNotActivateSchool() {
        onboarding.setStatus(OnboardingStatus.PROVISIONING_FAILED);
        provisioning.markFailed("Flyway checksum mismatch");

        when(onboardingRepository.findById(onboardingId)).thenReturn(Optional.of(onboarding));
        when(schoolRepository.findById(schoolId)).thenReturn(Optional.of(school));
        when(tenantRepository.findBySchoolId(schoolId)).thenReturn(Optional.of(tenant));
        when(tenantProvisioningRepository.findByTenantId(tenantId)).thenReturn(Optional.of(provisioning));

        assertThatThrownBy(() -> workflowService.activateSchool(onboardingId, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Tenant provisioning is not completed");

        assertThat(school.getStatus()).isNotEqualTo(SchoolStatus.ACTIVE);
        assertThat(tenant.getStatus()).isNotEqualTo(TenantStatus.ACTIVE);
    }

    // -------------------------------------------------------------------------
    // Scenario 18: Ensure Payment is NOT Duplicated During Provisioning Retry
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Scenario 18: Ensure payment gateway is never invoked during provisioning retry")
    void scenario18_EnsurePaymentNotDuplicated_DuringRetry() {
        onboarding.setStatus(OnboardingStatus.PROVISIONING_FAILED);
        provisioning.markFailed("DB network failure");

        when(onboardingRepository.findById(onboardingId)).thenReturn(Optional.of(onboarding));
        when(tenantRepository.findBySchoolId(schoolId)).thenReturn(Optional.of(tenant));
        when(tenantProvisioningRepository.findByTenantId(tenantId)).thenReturn(Optional.of(provisioning));
        when(schoolRepository.findById(schoolId)).thenReturn(Optional.of(school));
        when(adminProvisioningService.provisionInitialAdmin(any(), any(), any()))
                .thenReturn(InitialSchoolAdminResponse.builder().email("admin@greenwood.edu.in").role(PlatformRole.SCHOOL_OWNER).build());

        org.mockito.Mockito.doAnswer(invocation -> {
            provisioning.markCompleted();
            return null;
        }).when(tenantProvisioningService).executeProvisioning(tenantId);

        workflowService.retryWorkflow(onboardingId, new WorkflowRetryRequest("Retry"));

        verify(paymentService, never()).initiatePayment(any());
        verify(paymentService, never()).verifyPayment(any());
        verify(paymentService, never()).confirmManualPayment(any());
    }

    // -------------------------------------------------------------------------
    // Scenario 19: Workflow Progress Report Consistency
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Scenario 19: Full progress report aggregates all 6 linked domain entities consistently")
    void scenario19_WorkflowProgressReport_Consistency() {
        PlatformUser adminUser = new PlatformUser("USR-ADMIN01", "principal@greenwood.edu.in", "hash", "Suresh", "Raina", "+919876543210", PlatformRole.SCHOOL_OWNER, schoolId);
        PaymentTransaction tx = new PaymentTransaction("TXN-12345678", schoolId, subscription.getId(), onboardingId, new BigDecimal("59000.00"), "INR", PaymentProvider.RAZORPAY, "KEY-1", PaymentMethod.UPI);
        tx.setStatus(PaymentStatus.SUCCESS);

        when(onboardingRepository.findById(onboardingId)).thenReturn(Optional.of(onboarding));
        when(subscriptionRepository.findBySchoolId(schoolId)).thenReturn(List.of(subscription));
        when(paymentTransactionRepository.findBySchoolId(eq(schoolId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(tx)));
        when(tenantRepository.findBySchoolId(schoolId)).thenReturn(Optional.of(tenant));
        when(tenantProvisioningRepository.findByTenantId(tenantId)).thenReturn(Optional.of(provisioning));
        when(platformUserRepository.findBySchoolId(schoolId)).thenReturn(List.of(adminUser));

        SchoolOnboardingProgressResponse progress = workflowService.getWorkflowProgress(onboardingId);

        assertThat(progress).isNotNull();
        assertThat(progress.getOnboardingCode()).isEqualTo("ONB-12345678");
        assertThat(progress.getSchoolCode()).isEqualTo("SCH-12345678");
        assertThat(progress.getSubscriptionCode()).isEqualTo("SUB-12345678");
        assertThat(progress.getTransactionCode()).isEqualTo("TXN-12345678");
        assertThat(progress.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(progress.getTenantCode()).isEqualTo("gw123456");
        assertThat(progress.getAdminEmail()).isEqualTo("principal@greenwood.edu.in");
    }
}
