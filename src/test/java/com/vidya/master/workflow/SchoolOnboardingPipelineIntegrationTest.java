package com.vidya.master.workflow;

import com.vidya.master.onboarding.dto.InitiateOnboardingRequest;
import com.vidya.master.onboarding.dto.OnboardingResponse;
import com.vidya.master.onboarding.dto.SaveStepDataRequest;
import com.vidya.master.onboarding.dto.VerifyOnboardingRequest;
import com.vidya.master.onboarding.entity.OnboardingSource;
import com.vidya.master.onboarding.entity.OnboardingStatus;
import com.vidya.master.onboarding.entity.OnboardingStep;
import com.vidya.master.onboarding.mapper.OnboardingMapper;
import com.vidya.master.onboarding.repository.OnboardingActivityRepository;
import com.vidya.master.onboarding.repository.OnboardingStepDataRepository;
import com.vidya.master.onboarding.repository.SchoolOnboardingRepository;
import com.vidya.master.onboarding.service.OnboardingServiceImpl;
import com.vidya.master.payment.dto.InitiatePaymentRequest;
import com.vidya.master.payment.dto.InitiatePaymentResponse;
import com.vidya.master.payment.dto.ManualPaymentConfirmRequest;
import com.vidya.master.payment.dto.PaymentTransactionResponse;
import com.vidya.master.payment.entity.PaymentMethod;
import com.vidya.master.payment.entity.PaymentProvider;
import com.vidya.master.payment.entity.PaymentStatus;
import com.vidya.master.payment.entity.PaymentTransaction;
import com.vidya.master.payment.gateway.PaymentGatewayRegistry;
import com.vidya.master.payment.gateway.manual.ManualPaymentGateway;
import com.vidya.master.payment.mapper.PaymentMapper;
import com.vidya.master.payment.repository.PaymentTransactionRepository;
import com.vidya.master.payment.service.PaymentServiceImpl;
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
import com.vidya.master.subscription.mapper.SubscriptionMapper;
import com.vidya.master.subscription.repository.SchoolSubscriptionRepository;
import com.vidya.master.subscription.repository.SubscriptionPlanRepository;
import com.vidya.master.subscription.service.SchoolSubscriptionServiceImpl;
import com.vidya.master.tenant.entity.Tenant;
import com.vidya.master.tenant.entity.TenantStatus;
import com.vidya.master.tenant.repository.TenantRepository;
import com.vidya.master.workflow.admin.SchoolAdminProvisioningServiceImpl;
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
import com.vidya.master.workflow.dto.WorkflowProvisionResponse;
import com.vidya.master.workflow.event.PaymentCompletedEvent;
import com.vidya.master.workflow.service.SchoolOnboardingWorkflowServiceImpl;
import com.vidya.security.entity.PlatformRole;
import com.vidya.security.entity.PlatformUser;
import com.vidya.security.entity.UserStatus;
import com.vidya.security.repository.PlatformUserRepository;
import com.vidya.tenant.provisioning.TenantProvisioningService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SchoolOnboardingPipelineIntegrationTest {

    // Repositories
    @Mock private SchoolOnboardingRepository onboardingRepository;
    @Mock private OnboardingStepDataRepository stepDataRepository;
    @Mock private OnboardingActivityRepository activityRepository;
    @Mock private SchoolRepository schoolRepository;
    @Mock private SubscriptionPlanRepository planRepository;
    @Mock private SchoolSubscriptionRepository subscriptionRepository;
    @Mock private PaymentTransactionRepository paymentTransactionRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private TenantProvisioningRepository tenantProvisioningRepository;
    @Mock private PlatformUserRepository platformUserRepository;
    @Mock private TenantProvisioningService tenantProvisioningService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private ApplicationEventPublisher eventPublisher;

    private OnboardingServiceImpl onboardingService;
    private SchoolSubscriptionServiceImpl subscriptionService;
    private PaymentServiceImpl paymentService;
    private SchoolAdminProvisioningServiceImpl adminProvisioningService;
    private SchoolOnboardingWorkflowServiceImpl workflowService;

    // Test Domain Entities
    private UUID schoolId;
    private UUID onboardingId;
    private UUID planId;
    private UUID subscriptionId;
    private UUID tenantId;

    private School school;
    private SubscriptionPlan plan;
    private SchoolSubscription subscription;
    private Tenant tenant;
    private TenantProvisioning provisioning;

    @BeforeEach
    void setUp() {
        schoolId = UUID.randomUUID();
        onboardingId = UUID.randomUUID();
        planId = UUID.randomUUID();
        subscriptionId = UUID.randomUUID();
        tenantId = UUID.randomUUID();

        // 1. Instantiate Core Domain Services
        OnboardingMapper onboardingMapper = new OnboardingMapper();
        onboardingService = new OnboardingServiceImpl(
                onboardingRepository, stepDataRepository, activityRepository, schoolRepository, onboardingMapper);

        SubscriptionMapper subscriptionMapper = new SubscriptionMapper();
        subscriptionService = new SchoolSubscriptionServiceImpl(
                schoolRepository, planRepository, subscriptionRepository, subscriptionMapper);

        PaymentGatewayRegistry gatewayRegistry = new PaymentGatewayRegistry(List.of(new ManualPaymentGateway()));
        PaymentMapper paymentMapper = new PaymentMapper();
        paymentService = new PaymentServiceImpl(
                paymentTransactionRepository, schoolRepository, gatewayRegistry, paymentMapper, eventPublisher);

        adminProvisioningService = new SchoolAdminProvisioningServiceImpl(
                schoolRepository, onboardingRepository, stepDataRepository, platformUserRepository, passwordEncoder);

        workflowService = new SchoolOnboardingWorkflowServiceImpl(
                onboardingRepository, schoolRepository, planRepository, subscriptionRepository,
                subscriptionService, paymentService, paymentTransactionRepository, tenantRepository,
                tenantProvisioningRepository, tenantProvisioningService, adminProvisioningService,
                platformUserRepository, activityRepository
        );

        // 2. Prepare Fixtures
        school = new School(
                "SCH-IND001",
                "Vidya International School",
                "Vidya Educational Trust",
                "REG-VIDYA-2026",
                SchoolBoard.CBSE,
                SchoolType.ALL,
                "principal@vidyainternational.edu.in",
                "+919876543210",
                "https://vidyainternational.edu.in",
                SchoolStatus.DRAFT
        );
        school.setId(schoolId);

        plan = new SubscriptionPlan(
                "PREMIUM",
                1,
                "Enterprise Pro Plan",
                "All inclusive management package",
                BillingCycle.ANNUAL,
                new BigDecimal("49999.00"),
                "INR",
                30,
                com.vidya.master.subscription.entity.PlanStatus.ACTIVE,
                LocalDate.now()
        );
        plan.setId(planId);

        subscription = new SchoolSubscription(
                "SUB-IND001",
                schoolId,
                plan,
                "PREMIUM",
                1,
                LocalDate.now(),
                LocalDate.now().plusYears(1),
                BillingCycle.ANNUAL,
                new BigDecimal("49999.00"),
                BigDecimal.ZERO,
                new BigDecimal("8999.82"),
                new BigDecimal("58998.82"),
                "INR",
                SubscriptionStatus.ACTIVE,
                true,
                null
        );
        subscription.setId(subscriptionId);

        tenant = Tenant.create(
                schoolId,
                "vidya_sch_ind001",
                "localhost",
                5432,
                "vidya_tenant_sch_ind001",
                "TENANT_DB_USER",
                "TENANT_DB_PASS"
        );
        tenant.setId(tenantId);

        provisioning = TenantProvisioning.create(tenantId, "PRV-IND001");
    }

    @Test
    @DisplayName("Complete End-to-End School Onboarding Pipeline Integration Test")
    void executeFullOnboardingPipeline_EndToEnd_Success() {
        // =====================================================================
        // Step 1: Initiate Onboarding
        // =====================================================================
        InitiateOnboardingRequest initRequest = new InitiateOnboardingRequest(
                OnboardingSource.DIRECT,
                null,
                null,
                "Vidya International School",
                "principal@vidyainternational.edu.in",
                "+919876543210"
        );

        when(schoolRepository.save(any(School.class))).thenAnswer(i -> {
            School s = i.getArgument(0);
            s.setId(schoolId);
            return s;
        });

        when(onboardingRepository.save(any())).thenAnswer(i -> {
            com.vidya.master.onboarding.entity.SchoolOnboarding o = i.getArgument(0);
            o.setId(onboardingId);
            return o;
        });

        OnboardingResponse onboardingRes = onboardingService.initiateOnboarding(initRequest);

        assertThat(onboardingRes).isNotNull();
        assertThat(onboardingRes.status()).isEqualTo(OnboardingStatus.DRAFT);
        assertThat(onboardingRes.currentStep()).isEqualTo(OnboardingStep.BASIC_INFORMATION);

        com.vidya.master.onboarding.entity.SchoolOnboarding liveOnboarding = new com.vidya.master.onboarding.entity.SchoolOnboarding(
                onboardingRes.onboardingCode(), school, OnboardingSource.DIRECT, null, null);
        liveOnboarding.setId(onboardingId);

        // =====================================================================
        // Step 2: Complete Wizard Steps (Contact info for School Admin)
        // =====================================================================
        when(onboardingRepository.findById(onboardingId)).thenReturn(Optional.of(liveOnboarding));
        when(stepDataRepository.findByOnboardingIdAndStepName(onboardingId, OnboardingStep.CONTACT_INFORMATION))
                .thenReturn(Optional.empty());

        Map<String, Object> contactData = new HashMap<>();
        contactData.put("email", "principal@vidyainternational.edu.in");
        contactData.put("firstName", "Anand");
        contactData.put("lastName", "Kumar");
        contactData.put("phone", "+919876543210");

        onboardingService.saveStepData(onboardingId, new SaveStepDataRequest(
                OnboardingStep.CONTACT_INFORMATION, contactData, true));

        assertThat(liveOnboarding.getCompletedSteps()).contains("CONTACT_INFORMATION");

        // =====================================================================
        // Step 3: Submit & Verify Onboarding Application
        // =====================================================================
        onboardingService.submitOnboarding(onboardingId);
        assertThat(liveOnboarding.getStatus()).isEqualTo(OnboardingStatus.SUBMITTED);
        assertThat(school.getStatus()).isEqualTo(SchoolStatus.PENDING);

        onboardingService.verifyOnboarding(onboardingId, new VerifyOnboardingRequest(true, "All documents verified"));
        assertThat(liveOnboarding.getStatus()).isEqualTo(OnboardingStatus.VERIFIED);
        assertThat(school.getStatus()).isEqualTo(SchoolStatus.VERIFIED);

        // =====================================================================
        // Step 4: Plan Selection & Snapshot Creation
        // =====================================================================
        when(schoolRepository.findById(schoolId)).thenReturn(Optional.of(school));
        when(schoolRepository.existsById(schoolId)).thenReturn(true);
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(subscriptionRepository.findBySchoolId(schoolId)).thenReturn(List.of());
        when(subscriptionRepository.save(any())).thenReturn(subscription);

        PlanSelectionResponse planRes = workflowService.selectPlan(onboardingId, PlanSelectionRequest.builder()
                .planId(planId)
                .billingCycle(BillingCycle.ANNUAL)
                .build());

        assertThat(planRes).isNotNull();
        assertThat(planRes.getPlanCode()).isEqualTo("PREMIUM");
        assertThat(planRes.getPlanVersion()).isEqualTo(1);
        assertThat(planRes.getFinalAmount()).isEqualByComparingTo(new BigDecimal("58998.82"));
        assertThat(liveOnboarding.getStatus()).isEqualTo(OnboardingStatus.PLAN_SELECTED);
        assertThat(liveOnboarding.getCurrentStep()).isEqualTo(OnboardingStep.PAYMENT);

        // =====================================================================
        // Step 5: Initiate Payment & Confirm Payment Success
        // =====================================================================
        when(schoolRepository.existsById(schoolId)).thenReturn(true);
        when(subscriptionRepository.findBySchoolId(schoolId)).thenReturn(List.of(subscription));

        PaymentTransaction tx = new PaymentTransaction(
                "TXN-IND001", schoolId, subscriptionId, onboardingId,
                new BigDecimal("58998.82"), "INR", PaymentProvider.MANUAL,
                "IDEMP-KEY-001", PaymentMethod.NEFT
        );
        tx.setId(UUID.randomUUID());

        when(paymentTransactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        WorkflowPaymentInitiateResponse payInitRes = workflowService.initiatePayment(onboardingId,
                WorkflowPaymentInitiateRequest.builder()
                        .paymentProvider(PaymentProvider.MANUAL)
                        .paymentMethod(PaymentMethod.NEFT)
                        .idempotencyKey("IDEMP-KEY-001")
                        .build());

        assertThat(payInitRes).isNotNull();
        assertThat(liveOnboarding.getStatus()).isEqualTo(OnboardingStatus.PAYMENT_PENDING);

        // Confirm manual payment and publish event
        PaymentCompletedEvent paymentEvent = PaymentCompletedEvent.builder()
                .transactionCode(tx.getTransactionCode())
                .onboardingId(onboardingId)
                .schoolId(schoolId)
                .subscriptionId(subscriptionId)
                .amount(new BigDecimal("58998.82"))
                .currency("INR")
                .paymentProvider(PaymentProvider.MANUAL)
                .paymentMethod(PaymentMethod.NEFT)
                .status(PaymentStatus.SUCCESS)
                .completedAt(java.time.Instant.now())
                .build();

        workflowService.handlePaymentCompleted(paymentEvent);

        assertThat(liveOnboarding.getStatus()).isEqualTo(OnboardingStatus.PAYMENT_COMPLETED);
        assertThat(liveOnboarding.getCurrentStep()).isEqualTo(OnboardingStep.APPROVAL);

        // =====================================================================
        // Step 6: Platform Admin Approval
        // =====================================================================
        WorkflowApprovalResponse appRes = workflowService.approveOnboarding(onboardingId,
                WorkflowApproveRequest.builder().notes("Application and payment approved").build());

        assertThat(appRes.getOnboardingStatus()).isEqualTo(OnboardingStatus.APPROVED);
        assertThat(liveOnboarding.getStatus()).isEqualTo(OnboardingStatus.APPROVED);
        assertThat(liveOnboarding.getCurrentStep()).isEqualTo(OnboardingStep.TENANT_PROVISIONING);

        // =====================================================================
        // Step 7: Tenant Database Provisioning & Migrations
        // =====================================================================
        when(tenantRepository.findBySchoolId(schoolId)).thenReturn(Optional.of(tenant));
        when(tenantProvisioningService.startProvisioning(tenantId)).thenReturn(provisioning);
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(tenantProvisioningRepository.findByTenantId(tenantId)).thenReturn(Optional.of(provisioning));

        provisioning.markCompleted();
        tenant.markProvisioned();

        WorkflowProvisionResponse provRes = workflowService.provisionTenant(onboardingId, null);

        assertThat(provRes).isNotNull();
        assertThat(provRes.getTenantStatus()).isEqualTo(TenantStatus.PROVISIONED);
        assertThat(provRes.getProvisioningStatus()).isEqualTo("COMPLETED");
        assertThat(liveOnboarding.getStatus()).isEqualTo(OnboardingStatus.PROVISIONED);
        assertThat(liveOnboarding.getCurrentStep()).isEqualTo(OnboardingStep.ADMIN_CREATION);

        // =====================================================================
        // Step 8: Create School Administrator & Final School Activation
        // =====================================================================
        PlatformUser adminUser = new PlatformUser(
                "USR-ADMIN-IND001",
                "principal@vidyainternational.edu.in",
                "encrypted_hash",
                "Anand",
                "Kumar",
                "+919876543210",
                PlatformRole.SCHOOL_OWNER,
                schoolId
        );
        adminUser.setId(UUID.randomUUID());

        when(platformUserRepository.findByEmailIgnoreCase("principal@vidyainternational.edu.in")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("encrypted_hash");
        when(platformUserRepository.save(any())).thenReturn(adminUser);
        when(platformUserRepository.findBySchoolId(schoolId)).thenReturn(List.of(adminUser));
        when(paymentTransactionRepository.findBySchoolId(eq(schoolId), any())).thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(tx)));

        WorkflowActivationResponse activationRes = workflowService.activateSchool(onboardingId,
                new WorkflowActivateRequest(new InitialSchoolAdminRequest("principal@vidyainternational.edu.in", "Anand", "Kumar", "+919876543210", PlatformRole.SCHOOL_OWNER, "Secret@123"), "Final activation"));

        // =====================================================================
        // Assertions: Verify Exact Required Final States
        // =====================================================================
        assertThat(activationRes).isNotNull();

        // 1. Onboarding = ACTIVE
        assertThat(activationRes.getOnboardingStatus()).isEqualTo(OnboardingStatus.ACTIVE);
        assertThat(liveOnboarding.getStatus()).isEqualTo(OnboardingStatus.ACTIVE);
        assertThat(liveOnboarding.getCompletedAt()).isNotNull();

        // 2. School = ACTIVE
        assertThat(activationRes.getSchoolStatus()).isEqualTo(SchoolStatus.ACTIVE);
        assertThat(school.getStatus()).isEqualTo(SchoolStatus.ACTIVE);
        assertThat(school.getTenantId()).isEqualTo(tenantId);

        // 3. Subscription = ACTIVE
        assertThat(activationRes.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);

        // 4. Payment = SUCCESS
        assertThat(paymentEvent.getStatus()).isEqualTo(PaymentStatus.SUCCESS);

        // 5. Tenant = ACTIVE
        assertThat(activationRes.getTenantStatus()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(tenant.getStatus()).isEqualTo(TenantStatus.ACTIVE);

        // 6. Provisioning = COMPLETED
        assertThat(provisioning.isCompleted()).isTrue();

        // 7. School Admin = CREATED
        assertThat(activationRes.getAdmin()).isNotNull();
        assertThat(activationRes.getAdmin().getEmail()).isEqualTo("principal@vidyainternational.edu.in");
        assertThat(activationRes.getAdmin().getRole()).isEqualTo(PlatformRole.SCHOOL_OWNER);
        assertThat(activationRes.getAdmin().getSchoolId()).isEqualTo(schoolId);

        // Verify Full Progress Report Multi-Domain Consistency
        SchoolOnboardingProgressResponse progress = workflowService.getWorkflowProgress(onboardingId);
        assertThat(progress).isNotNull();
        assertThat(progress.getOnboardingStatus()).isEqualTo(OnboardingStatus.ACTIVE);
        assertThat(progress.getSchoolStatus()).isEqualTo(SchoolStatus.ACTIVE);
        assertThat(progress.getTenantStatus()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(progress.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(progress.getAdminEmail()).isEqualTo("principal@vidyainternational.edu.in");
    }
}
