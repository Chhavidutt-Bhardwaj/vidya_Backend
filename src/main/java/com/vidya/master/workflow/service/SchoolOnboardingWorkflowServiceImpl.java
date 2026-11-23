package com.vidya.master.workflow.service;

import com.vidya.common.api.ApiResponseCode;
import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.InvalidStateTransitionException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.common.exception.TenantProvisioningException;
import com.vidya.common.util.CodeGenerator;
import com.vidya.master.onboarding.entity.OnboardingActivity;
import com.vidya.master.onboarding.entity.OnboardingStatus;
import com.vidya.master.onboarding.entity.OnboardingStep;
import com.vidya.master.onboarding.service.OnboardingActor;
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
import com.vidya.master.school.entity.SchoolStatus;
import com.vidya.master.school.repository.SchoolRepository;
import com.vidya.master.subscription.dto.SchoolSubscriptionResponse;
import com.vidya.master.subscription.dto.SubscribeSchoolRequest;
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
import com.vidya.master.workflow.dto.PaymentWaiverRequest;
import com.vidya.master.workflow.dto.PaymentWaiverResponse;
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
import com.vidya.security.entity.PlatformUser;
import com.vidya.security.repository.PlatformUserRepository;
import com.vidya.tenant.provisioning.TenantProvisioningService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SchoolOnboardingWorkflowServiceImpl implements SchoolOnboardingWorkflowService {

    private final SchoolOnboardingRepository onboardingRepository;
    private final SchoolRepository schoolRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final SchoolSubscriptionRepository subscriptionRepository;
    private final SchoolSubscriptionService schoolSubscriptionService;
    private final PaymentService paymentService;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final TenantRepository tenantRepository;
    private final TenantProvisioningRepository tenantProvisioningRepository;
    private final TenantProvisioningService tenantProvisioningService;
    private final SchoolAdminProvisioningService adminProvisioningService;
    private final PlatformUserRepository platformUserRepository;
    private final OnboardingActivityRepository activityRepository;

    /**
     * Tenant database location is server configuration only; it is never taken from a request.
     * Field-injected (non-final) so the Lombok constructor used by existing tests is unchanged.
     */
    @Value("${vidya.provisioning.tenant-db.host:localhost}")
    private String tenantDbHost = "localhost";

    @Value("${vidya.provisioning.tenant-db.port:5432}")
    private int tenantDbPort = 5432;

    /**
     * Transactional proxy of this service. {@link #retryWorkflow} must call {@link #activateSchool}
     * through the proxy so that activation runs in its own transaction (a plain self-call bypasses
     * {@code @Transactional} and fails with LazyInitializationException on real JPA).
     * Null in plain unit tests, where {@code this} is used.
     */
    @Autowired
    @Lazy
    private SchoolOnboardingWorkflowService self;

    private SchoolOnboardingWorkflowService self() {
        return self != null ? self : this;
    }

    // -------------------------------------------------------------------------
    // 1. Plan Selection & Subscription Snapshot
    // -------------------------------------------------------------------------

    @Override
    @Transactional(transactionManager = "masterTransactionManager")
    public PlanSelectionResponse selectPlan(UUID onboardingId, PlanSelectionRequest request) {
        log.info("Workflow: Selecting plan [{}] for onboarding ID: [{}]", request.getPlanId(), onboardingId);

        SchoolOnboarding onboarding = findOnboardingOrThrow(onboardingId);

        if (onboarding.getStatus() == OnboardingStatus.ACTIVE) {
            throw new BusinessException(ApiResponseCode.BUSINESS_ERROR, "School onboarding is already active");
        }
        // Plan selection requires a VERIFIED onboarding (re-selection allowed while payment is pending).
        onboarding.getStatus().validateTransitionTo(OnboardingStatus.PLAN_SELECTED);

        UUID schoolId = onboarding.getSchool() != null ? onboarding.getSchool().getId() : null;
        if (schoolId == null) {
            throw new BusinessException(ApiResponseCode.BUSINESS_ERROR, "Onboarding record does not have an associated school");
        }

        School school = schoolRepository.findById(schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("School", schoolId));

        SubscriptionPlan plan = subscriptionPlanRepository.findById(request.getPlanId())
                .orElseThrow(() -> new ResourceNotFoundException("SubscriptionPlan", request.getPlanId()));

        String prevStatus = onboarding.getStatus().name();

        // Check if an unpaid subscription already exists for this school and plan (Idempotency)
        List<SchoolSubscription> existingSubs = subscriptionRepository.findBySchoolId(schoolId);
        SchoolSubscriptionResponse subscriptionRes;
        Optional<SchoolSubscription> reusableSub = existingSubs.stream()
                .filter(s -> s.getPlan().getId().equals(request.getPlanId()) && s.getStatus() == SubscriptionStatus.ACTIVE)
                .findFirst();

        if (reusableSub.isPresent() && onboarding.getStatus() == OnboardingStatus.PLAN_SELECTED) {
            log.info("Idempotent plan selection: Reusing subscription [{}]", reusableSub.get().getSubscriptionCode());
            SchoolSubscription sub = reusableSub.get();
            subscriptionRes = new SchoolSubscriptionResponse(
                    sub.getId(), sub.getSubscriptionCode(), sub.getSchoolId(), sub.getPlan().getId(),
                    sub.getPlanCode(), sub.getPlanVersion(), sub.getStartDate(), sub.getEndDate(),
                    sub.getBillingCycle(), sub.getBaseAmount(), sub.getDiscountAmount(), sub.getTaxAmount(),
                    sub.getFinalAmount(), sub.getCurrency(), sub.getStatus(), sub.isAutoRenew(),
                    sub.getTrialEndDate(), sub.getCreatedAt(), sub.getUpdatedAt()
            );
        } else {
            // Create immutable subscription snapshot
            SubscribeSchoolRequest subRequest = new SubscribeSchoolRequest(
                    schoolId,
                    request.getPlanId(),
                    request.getBillingCycle() != null ? request.getBillingCycle() : plan.getBillingCycle(),
                    request.getDiscountAmount() != null ? request.getDiscountAmount() : BigDecimal.ZERO,
                    request.getAutoRenew() != null ? request.getAutoRenew() : true,
                    request.getIsTrial() != null ? request.getIsTrial() : false
            );
            subscriptionRes = schoolSubscriptionService.subscribeSchool(subRequest);
        }

        // Transition Onboarding state
        onboarding.setStatus(OnboardingStatus.PLAN_SELECTED);
        onboarding.setCurrentStep(OnboardingStep.PAYMENT);
        onboarding.addCompletedStep(OnboardingStep.PLAN_SELECTION.name());
        SchoolOnboarding updated = onboardingRepository.save(onboarding);

        // Audit Trail
        recordActivity(updated, "PLAN_SELECTED", prevStatus, OnboardingStatus.PLAN_SELECTED.name(),
                Map.of("planCode", subscriptionRes.planCode(),
                        "planVersion", subscriptionRes.planVersion(),
                        "finalAmount", subscriptionRes.finalAmount(),
                        "subscriptionCode", subscriptionRes.subscriptionCode()));

        return PlanSelectionResponse.builder()
                .onboardingId(updated.getId())
                .onboardingCode(updated.getOnboardingCode())
                .schoolId(school.getId())
                .schoolCode(school.getSchoolCode())
                .subscriptionId(subscriptionRes.id())
                .subscriptionCode(subscriptionRes.subscriptionCode())
                .planCode(subscriptionRes.planCode())
                .planVersion(subscriptionRes.planVersion())
                .billingCycle(subscriptionRes.billingCycle())
                .baseAmount(subscriptionRes.baseAmount())
                .discountAmount(subscriptionRes.discountAmount())
                .taxAmount(subscriptionRes.taxAmount())
                .finalAmount(subscriptionRes.finalAmount())
                .currency(subscriptionRes.currency())
                .onboardingStatus(updated.getStatus())
                .nextStep(updated.getCurrentStep())
                .build();
    }

    // -------------------------------------------------------------------------
    // 2. Payment Initiation
    // -------------------------------------------------------------------------

    @Override
    @Transactional(transactionManager = "masterTransactionManager")
    public WorkflowPaymentInitiateResponse initiatePayment(UUID onboardingId, WorkflowPaymentInitiateRequest request) {
        log.info("Workflow: Initiating payment for onboarding ID: [{}] via provider: [{}]",
                onboardingId, request.getPaymentProvider());

        SchoolOnboarding onboarding = findOnboardingOrThrow(onboardingId);

        if (onboarding.getStatus() == OnboardingStatus.ACTIVE) {
            throw new BusinessException(ApiResponseCode.BUSINESS_ERROR, "School onboarding is already active");
        }
        // Payment can only be initiated after a plan has been selected (or re-initiated while pending).
        onboarding.getStatus().validateTransitionTo(OnboardingStatus.PAYMENT_PENDING);

        UUID schoolId = onboarding.getSchool() != null ? onboarding.getSchool().getId() : null;
        if (schoolId == null) {
            throw new BusinessException(ApiResponseCode.BUSINESS_ERROR, "Onboarding record does not have an associated school");
        }

        School school = schoolRepository.findById(schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("School", schoolId));

        // Retrieve latest subscription snapshot
        List<SchoolSubscription> subs = subscriptionRepository.findBySchoolId(schoolId);
        if (subs.isEmpty()) {
            throw new BusinessException(ApiResponseCode.BUSINESS_ERROR, "No subscription plan selected for school. Select a plan first.");
        }
        SchoolSubscription subscription = latestSubscription(subs);

        String idempotencyKey = (request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank())
                ? request.getIdempotencyKey()
                : "ONB-PAY-" + onboarding.getOnboardingCode();

        InitiatePaymentRequest payRequest = InitiatePaymentRequest.builder()
                .schoolId(schoolId)
                .subscriptionId(subscription.getId())
                .onboardingId(onboarding.getId())
                .amount(subscription.getFinalAmount())
                .currency(subscription.getCurrency())
                .paymentProvider(request.getPaymentProvider())
                .paymentMethod(request.getPaymentMethod() != null ? request.getPaymentMethod() : PaymentMethod.UPI)
                .idempotencyKey(idempotencyKey)
                .customerName(school.getSchoolName())
                .customerEmail(school.getEmail())
                .customerPhone(school.getPhone())
                .notes(Map.of("description", request.getNotes() != null ? request.getNotes() : "Onboarding payment for " + school.getSchoolName()))
                .build();

        InitiatePaymentResponse payRes = paymentService.initiatePayment(payRequest);

        String prevStatus = onboarding.getStatus().name();
        if (onboarding.getStatus() == OnboardingStatus.PLAN_SELECTED) {
            onboarding.setStatus(OnboardingStatus.PAYMENT_PENDING);
            onboarding.setCurrentStep(OnboardingStep.PAYMENT);
            onboarding = onboardingRepository.save(onboarding);

            recordActivity(onboarding, "PAYMENT_INITIATED", prevStatus, OnboardingStatus.PAYMENT_PENDING.name(),
                    Map.of("transactionCode", payRes.getTransactionCode(),
                            "provider", request.getPaymentProvider().name(),
                            "amount", subscription.getFinalAmount()));
        }

        return WorkflowPaymentInitiateResponse.builder()
                .onboardingId(onboarding.getId())
                .onboardingCode(onboarding.getOnboardingCode())
                .transactionCode(payRes.getTransactionCode())
                .providerOrderId(payRes.getProviderOrderId())
                .amount(payRes.getAmount())
                .currency(payRes.getCurrency())
                .paymentProvider(payRes.getPaymentProvider())
                .paymentStatus(payRes.getStatus())
                .onboardingStatus(onboarding.getStatus())
                .nextStep(onboarding.getCurrentStep())
                .gatewayParameters(payRes.getCheckoutPayload())
                .build();
    }

    // -------------------------------------------------------------------------
    // 3. Payment Completed Event Handler (Webhook / Verify / Manual Confirm)
    // -------------------------------------------------------------------------

    @Override
    @EventListener
    @Transactional(transactionManager = "masterTransactionManager")
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        log.info("Workflow: Received PaymentCompletedEvent for transaction: {}, status: {}",
                event.getTransactionCode(), event.getStatus());

        if (event.getStatus() != PaymentStatus.SUCCESS) {
            return;
        }

        UUID onboardingId = event.getOnboardingId();
        SchoolOnboarding onboarding = null;

        if (onboardingId != null) {
            onboarding = onboardingRepository.findById(onboardingId).orElse(null);
        } else if (event.getSchoolId() != null) {
            onboarding = onboardingRepository.findBySchoolId(event.getSchoolId()).orElse(null);
        }

        if (onboarding == null) {
            log.debug("Workflow: No onboarding record associated with payment transaction: {}", event.getTransactionCode());
            return;
        }

        // Idempotency / sequencing: only an onboarding awaiting payment can be advanced by a payment.
        // A settled payment never skips verification or plan selection.
        if (onboarding.getStatus() != OnboardingStatus.PAYMENT_PENDING) {
            log.info("Workflow: Onboarding [{}] at status [{}] is not awaiting payment; payment {} not applied to workflow",
                    onboarding.getOnboardingCode(), onboarding.getStatus(), event.getTransactionCode());
            return;
        }

        String prevStatus = onboarding.getStatus().name();
        onboarding.setStatus(OnboardingStatus.PAYMENT_COMPLETED);
        onboarding.setCurrentStep(OnboardingStep.APPROVAL);
        onboarding.addCompletedStep(OnboardingStep.PAYMENT.name());
        SchoolOnboarding updated = onboardingRepository.save(onboarding);

        recordActivity(updated, "PAYMENT_COMPLETED", prevStatus, OnboardingStatus.PAYMENT_COMPLETED.name(),
                Map.of("transactionCode", event.getTransactionCode(),
                        "amount", event.getAmount() != null ? event.getAmount() : 0,
                        "provider", event.getPaymentProvider() != null ? event.getPaymentProvider().name() : "MANUAL"));

        log.info("Workflow: Onboarding [{}] advanced to PAYMENT_COMPLETED", updated.getOnboardingCode());
    }

    // -------------------------------------------------------------------------
    // 4. Approval Gating
    // -------------------------------------------------------------------------

    @Override
    @Transactional(transactionManager = "masterTransactionManager")
    public WorkflowApprovalResponse approveOnboarding(UUID onboardingId, WorkflowApproveRequest request) {
        log.info("Workflow: Approving onboarding ID: [{}]", onboardingId);

        SchoolOnboarding onboarding = findOnboardingOrThrow(onboardingId);

        if (onboarding.getStatus() == OnboardingStatus.ACTIVE) {
            throw new BusinessException(ApiResponseCode.BUSINESS_ERROR, "School onboarding is already active");
        }
        if (onboarding.getStatus() == OnboardingStatus.APPROVED) {
            log.info("Workflow: Onboarding [{}] already approved (idempotent)", onboarding.getOnboardingCode());
            return WorkflowApprovalResponse.builder()
                    .onboardingId(onboarding.getId())
                    .onboardingCode(onboarding.getOnboardingCode())
                    .onboardingStatus(onboarding.getStatus())
                    .nextStep(onboarding.getCurrentStep())
                    .notes("Already approved")
                    .approvedAt(Instant.now())
                    .build();
        }

        // Prerequisite: payment settled (PAYMENT_COMPLETED) or explicitly waived (PAYMENT_WAIVED).
        onboarding.getStatus().validateTransitionTo(OnboardingStatus.APPROVED);

        UUID schoolId = onboarding.getSchool() != null ? onboarding.getSchool().getId() : null;
        if (schoolId == null) {
            throw new BusinessException(ApiResponseCode.BUSINESS_ERROR, "Onboarding has no associated school");
        }

        String prevStatus = onboarding.getStatus().name();
        onboarding.setStatus(OnboardingStatus.APPROVED);
        onboarding.setCurrentStep(OnboardingStep.TENANT_PROVISIONING);
        onboarding.addCompletedStep(OnboardingStep.APPROVAL.name());
        SchoolOnboarding updated = onboardingRepository.save(onboarding);

        Map<String, Object> meta = new HashMap<>();
        if (request != null && request.getNotes() != null) {
            meta.put("notes", request.getNotes());
        }
        // request.approvedBy is deprecated and ignored: the approver is the authenticated user.
        recordActivity(updated, "ONBOARDING_APPROVED", prevStatus, OnboardingStatus.APPROVED.name(), meta);

        return WorkflowApprovalResponse.builder()
                .onboardingId(updated.getId())
                .onboardingCode(updated.getOnboardingCode())
                .onboardingStatus(updated.getStatus())
                .nextStep(updated.getCurrentStep())
                .notes(request != null ? request.getNotes() : null)
                .approvedAt(Instant.now())
                .build();
    }

    // -------------------------------------------------------------------------
    // 4b. Authorized Payment Waiver
    // -------------------------------------------------------------------------

    @Override
    @Transactional(transactionManager = "masterTransactionManager")
    public PaymentWaiverResponse waivePayment(UUID onboardingId, PaymentWaiverRequest request) {
        log.info("Workflow: Payment waiver requested for onboarding ID: [{}]", onboardingId);

        if (request == null || request.getReason() == null || request.getReason().isBlank()) {
            throw new IllegalArgumentException("A waiver reason is required");
        }
        UUID actorId = OnboardingActor.currentUserId();
        if (actorId == null) {
            // Defence in depth: the endpoint is permission-guarded; a waiver must always be attributable.
            throw new org.springframework.security.access.AccessDeniedException("Payment waiver requires an authenticated approver");
        }

        SchoolOnboarding onboarding = findOnboardingOrThrow(onboardingId);
        if (onboarding.getStatus() == OnboardingStatus.PAYMENT_WAIVED) {
            throw new InvalidStateTransitionException("SchoolOnboarding", onboarding.getStatus().name(), OnboardingStatus.PAYMENT_WAIVED.name());
        }
        // Only after plan selection (PLAN_SELECTED or PAYMENT_PENDING); never from an unverified onboarding.
        onboarding.getStatus().validateTransitionTo(OnboardingStatus.PAYMENT_WAIVED);

        String prevStatus = onboarding.getStatus().name();
        onboarding.setStatus(OnboardingStatus.PAYMENT_WAIVED);
        onboarding.setCurrentStep(OnboardingStep.APPROVAL);
        onboarding.addCompletedStep(OnboardingStep.PAYMENT.name());
        SchoolOnboarding updated = onboardingRepository.save(onboarding);

        Map<String, Object> meta = new HashMap<>();
        meta.put("reason", request.getReason());
        if (request.getReference() != null) {
            meta.put("reference", request.getReference());
        }
        recordActivity(updated, "PAYMENT_WAIVED", prevStatus, OnboardingStatus.PAYMENT_WAIVED.name(), meta);

        return PaymentWaiverResponse.builder()
                .onboardingId(updated.getId())
                .onboardingCode(updated.getOnboardingCode())
                .onboardingStatus(updated.getStatus())
                .nextStep(updated.getCurrentStep())
                .waivedBy(actorId)
                .reason(request.getReason())
                .waivedAt(Instant.now())
                .build();
    }

    // -------------------------------------------------------------------------
    // 5. Tenant Database Provisioning & Flyway Migrations
    // -------------------------------------------------------------------------

    @Override
    public WorkflowProvisionResponse provisionTenant(UUID onboardingId, WorkflowProvisionRequest request) {
        log.info("Workflow: Starting tenant provisioning for onboarding ID: [{}]", onboardingId);

        SchoolOnboarding onboarding = findOnboardingOrThrow(onboardingId);

        if (onboarding.getStatus() == OnboardingStatus.ACTIVE) {
            throw new BusinessException(ApiResponseCode.BUSINESS_ERROR, "School onboarding is already active");
        }
        // APPROVED, PROVISIONING_FAILED / FAILED (retry) or PROVISIONING (resume after a crash).
        onboarding.getStatus().validateTransitionTo(OnboardingStatus.PROVISIONING);

        UUID schoolId = onboarding.getSchool() != null ? onboarding.getSchool().getId() : null;
        if (schoolId == null) {
            throw new BusinessException(ApiResponseCode.BUSINESS_ERROR, "Onboarding has no associated school");
        }

        // 1. Ensure Tenant Entity Exists. The DB location is server configuration only (never from the request);
        //    WorkflowProvisionRequest.databaseHost/databasePort are rejected at the API boundary.
        String dbHost = tenantDbHost;
        int dbPort = tenantDbPort;

        Tenant tenant = tenantRepository.findBySchoolId(schoolId)
                .orElseGet(() -> {
                    String tenantCode = CodeGenerator.tenantCode();
                    String dbName = "vidya_tenant_" + tenantCode;
                    Tenant newTenant = Tenant.create(
                            schoolId,
                            tenantCode,
                            dbHost,
                            dbPort,
                            dbName,
                            "TENANT_DB_USER",
                            "TENANT_DB_PASS"
                    );
                    return tenantRepository.save(newTenant);
                });

        // 2. Advance status to PROVISIONING
        String prevStatus = onboarding.getStatus().name();
        onboarding.setStatus(OnboardingStatus.PROVISIONING);
        onboarding.setCurrentStep(OnboardingStep.TENANT_PROVISIONING);
        // Re-assign: this method is not transactional, so the saved (merged) instance carries the new @Version.
        onboarding = onboardingRepository.save(onboarding);

        recordActivity(onboarding, "TENANT_PROVISIONING_STARTED", prevStatus, OnboardingStatus.PROVISIONING.name(),
                Map.of("tenantCode", tenant.getTenantCode(), "dbName", tenant.getDatabaseName()));

        // 3. Execute Provisioning Steps (DB Creation, Flyway, Seed)
        TenantProvisioning provisioningRecord;
        try {
            provisioningRecord = startOrResumeProvisioning(tenant.getId());

            // Reload records
            tenant = tenantRepository.findById(tenant.getId()).orElse(tenant);
            provisioningRecord = tenantProvisioningRepository.findByTenantId(tenant.getId()).orElse(provisioningRecord);

            onboarding.setStatus(OnboardingStatus.PROVISIONED);
            onboarding.setCurrentStep(OnboardingStep.ADMIN_CREATION);
            onboarding.addCompletedStep(OnboardingStep.TENANT_PROVISIONING.name());
            SchoolOnboarding saved = onboardingRepository.save(onboarding);

            recordActivity(saved, "TENANT_PROVISIONING_COMPLETED", OnboardingStatus.PROVISIONING.name(), OnboardingStatus.PROVISIONED.name(),
                    Map.of("tenantCode", tenant.getTenantCode(), "dbName", tenant.getDatabaseName()));

            log.info("Workflow: Tenant provisioning completed successfully for onboarding [{}]", saved.getOnboardingCode());

            return WorkflowProvisionResponse.builder()
                    .onboardingId(saved.getId())
                    .onboardingCode(saved.getOnboardingCode())
                    .tenantId(tenant.getId())
                    .tenantCode(tenant.getTenantCode())
                    .databaseName(tenant.getDatabaseName())
                    .tenantStatus(tenant.getStatus())
                    .provisioningCode(provisioningRecord != null ? provisioningRecord.getProvisioningCode() : null)
                    .provisioningStatus(provisioningRecord != null ? provisioningRecord.getStatus() : "COMPLETED")
                    .onboardingStatus(saved.getStatus())
                    .nextStep(saved.getCurrentStep())
                    .completedAt(Instant.now())
                    .build();

        } catch (Exception e) {
            log.error("Workflow: Tenant provisioning failed for onboarding [{}]: {}", onboarding.getOnboardingCode(), e.getMessage(), e);

            // Reload to avoid writing over concurrent changes with a stale @Version.
            SchoolOnboarding failed = onboardingRepository.findById(onboarding.getId()).orElse(onboarding);
            failed.setStatus(OnboardingStatus.PROVISIONING_FAILED);
            failed = onboardingRepository.save(failed);

            recordActivity(failed, "TENANT_PROVISIONING_FAILED", OnboardingStatus.PROVISIONING.name(), OnboardingStatus.PROVISIONING_FAILED.name(),
                    Map.of("error", e.getMessage() != null ? e.getMessage() : "Unknown provisioning failure"));

            if (e instanceof TenantProvisioningException) {
                throw (TenantProvisioningException) e;
            }
            throw new TenantProvisioningException(tenant.getTenantCode(), "PROVISIONING", e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // 6. Initial School Administrator Provisioning
    // -------------------------------------------------------------------------

    @Override
    @Transactional(transactionManager = "masterTransactionManager")
    public InitialSchoolAdminResponse createInitialAdmin(UUID onboardingId, InitialSchoolAdminRequest request) {
        log.info("Workflow: Creating initial administrator for onboarding ID: [{}]", onboardingId);

        SchoolOnboarding onboarding = findOnboardingOrThrow(onboardingId);
        if (onboarding.getStatus() != OnboardingStatus.PROVISIONED) {
            throw new InvalidStateTransitionException("SchoolOnboarding", onboarding.getStatus().name(), "ADMIN_CREATION");
        }
        UUID schoolId = onboarding.getSchool() != null ? onboarding.getSchool().getId() : null;
        if (schoolId == null) {
            throw new BusinessException(ApiResponseCode.BUSINESS_ERROR, "Onboarding has no associated school");
        }

        InitialSchoolAdminResponse adminResponse = adminProvisioningService.provisionInitialAdmin(schoolId, onboarding.getId(), request);

        onboarding.setCurrentStep(OnboardingStep.ACTIVATION);
        onboarding.addCompletedStep(OnboardingStep.ADMIN_CREATION.name());
        SchoolOnboarding updated = onboardingRepository.save(onboarding);

        recordActivity(updated, "ADMIN_CREATED", null, null,
                Map.of("adminUserId", String.valueOf(adminResponse.getUserId()),
                        "userCode", adminResponse.getUserCode(),
                        "email", adminResponse.getEmail(),
                        "role", adminResponse.getRole().name()));

        return adminResponse;
    }

    // -------------------------------------------------------------------------
    // 7. Final School Activation
    // -------------------------------------------------------------------------

    @Override
    @Transactional(transactionManager = "masterTransactionManager")
    public WorkflowActivationResponse activateSchool(UUID onboardingId, WorkflowActivateRequest request) {
        log.info("Workflow: Finalizing activation for onboarding ID: [{}]", onboardingId);

        SchoolOnboarding onboarding = findOnboardingOrThrow(onboardingId);

        // Idempotency: If already active, return state immediately
        if (onboarding.getStatus() == OnboardingStatus.ACTIVE) {
            log.info("Workflow: Onboarding [{}] is already ACTIVE (idempotent)", onboarding.getOnboardingCode());
            return buildActivationResponse(onboarding, "School onboarding is already active");
        }

        UUID schoolId = onboarding.getSchool() != null ? onboarding.getSchool().getId() : null;
        if (schoolId == null) {
            throw new BusinessException(ApiResponseCode.BUSINESS_ERROR, "Onboarding has no associated school");
        }

        // Only a PROVISIONED onboarding can be activated (DRAFT/unpaid/unapproved cannot reach this state).
        onboarding.getStatus().validateTransitionTo(OnboardingStatus.ACTIVE);

        School school = schoolRepository.findById(schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("School", schoolId));

        // 1. Verify Tenant Provisioning is Completed (a missing record is NOT treated as completed)
        Tenant tenant = tenantRepository.findBySchoolId(schoolId)
                .orElseThrow(() -> new BusinessException(ApiResponseCode.BUSINESS_ERROR, "Tenant not found. Provision tenant before activation."));

        TenantProvisioning provisioning = tenantProvisioningRepository.findByTenantId(tenant.getId()).orElse(null);
        if (provisioning == null || !provisioning.isCompleted()) {
            throw new BusinessException(ApiResponseCode.BUSINESS_ERROR,
                    "Tenant provisioning is not completed. Current status: "
                            + (provisioning != null ? provisioning.getStatus() : "NOT_STARTED"));
        }

        // 2. Ensure Initial Admin is Provisioned
        InitialSchoolAdminResponse adminResponse = adminProvisioningService.provisionInitialAdmin(
                schoolId, onboarding.getId(), request != null ? request.getAdminRequest() : null);

        // 3. Complete Activation across all entities
        tenant.markActive();
        tenantRepository.save(tenant);

        school.setTenantId(tenant.getId());
        school.setStatus(SchoolStatus.ACTIVE);
        schoolRepository.save(school);

        String prevStatus = onboarding.getStatus().name();
        onboarding.setStatus(OnboardingStatus.ACTIVE);
        onboarding.setCurrentStep(OnboardingStep.ACTIVATION);
        onboarding.addCompletedStep(OnboardingStep.ACTIVATION.name());
        onboarding.setCompletedAt(Instant.now());
        SchoolOnboarding updatedOnboarding = onboardingRepository.save(onboarding);

        // Audit Trail
        recordActivity(updatedOnboarding, "SCHOOL_ACTIVATED", prevStatus, OnboardingStatus.ACTIVE.name(),
                Map.of("schoolCode", school.getSchoolCode(),
                        "tenantCode", tenant.getTenantCode(),
                        "adminEmail", adminResponse.getEmail()));

        log.info("Workflow: ✅ School [{}] and Tenant [{}] successfully ACTIVATED",
                school.getSchoolName(), tenant.getTenantCode());

        return buildActivationResponse(updatedOnboarding, "School onboarding successfully activated and tenant operational");
    }

    // -------------------------------------------------------------------------
    // 8. Workflow Failure Recovery & Safe Retry
    // -------------------------------------------------------------------------

    @Override
    public WorkflowActivationResponse retryWorkflow(UUID onboardingId, WorkflowRetryRequest request) {
        log.info("Workflow: Retrying onboarding workflow for ID: [{}]", onboardingId);

        SchoolOnboarding onboarding = findOnboardingOrThrow(onboardingId);

        if (onboarding.getStatus() == OnboardingStatus.ACTIVE) {
            log.info("Workflow: Onboarding [{}] is already ACTIVE — nothing to retry", onboarding.getOnboardingCode());
            // Via the proxy: activateSchool is idempotent for ACTIVE and builds the response inside a transaction.
            return self().activateSchool(onboarding.getId(), null);
        }

        UUID schoolId = onboarding.getSchool() != null ? onboarding.getSchool().getId() : null;
        if (schoolId == null) {
            throw new BusinessException(ApiResponseCode.BUSINESS_ERROR, "Onboarding has no associated school");
        }

        // Retry is a technical recovery only: it never skips verification, payment or approval.
        OnboardingStatus status = onboarding.getStatus();
        if (status == OnboardingStatus.APPROVED || status == OnboardingStatus.PROVISIONING_FAILED
                || status == OnboardingStatus.FAILED || status == OnboardingStatus.PROVISIONING) {
            // Re-runs provisioning from the last completed step (no double payment, no duplicate tenant).
            provisionTenant(onboarding.getId(), null);
        } else if (status != OnboardingStatus.PROVISIONED) {
            throw new InvalidStateTransitionException("SchoolOnboarding", status.name(), "RETRY");
        }

        // Complete activation (through the proxy: own transaction)
        return self().activateSchool(onboarding.getId(), new WorkflowActivateRequest(null, request != null ? request.getNotes() : "Retried"));
    }

    // -------------------------------------------------------------------------
    // 9. Workflow Progress & Multi-Entity State Consistency Report
    // -------------------------------------------------------------------------

    @Override
    @Transactional(transactionManager = "masterTransactionManager", readOnly = true)
    public SchoolOnboardingProgressResponse getWorkflowProgress(UUID onboardingId) {
        SchoolOnboarding onboarding = findOnboardingOrThrow(onboardingId);
        School school = onboarding.getSchool();
        UUID schoolId = school != null ? school.getId() : null;

        SchoolSubscription subscription = null;
        PaymentTransaction paymentTx = null;
        Tenant tenant = null;
        TenantProvisioning provisioning = null;
        PlatformUser adminUser = null;

        if (schoolId != null) {
            List<SchoolSubscription> subs = subscriptionRepository.findBySchoolId(schoolId);
            if (!subs.isEmpty()) subscription = subs.get(0);

            org.springframework.data.domain.Page<PaymentTransaction> txPage = paymentTransactionRepository.findBySchoolId(schoolId, org.springframework.data.domain.PageRequest.of(0, 1));
            if (txPage != null && txPage.getContent() != null && !txPage.getContent().isEmpty()) {
                paymentTx = txPage.getContent().get(0);
            }

            tenant = tenantRepository.findBySchoolId(schoolId).orElse(null);
            if (tenant != null) {
                provisioning = tenantProvisioningRepository.findByTenantId(tenant.getId()).orElse(null);
            }

            List<PlatformUser> admins = platformUserRepository.findBySchoolId(schoolId);
            if (admins != null && !admins.isEmpty()) adminUser = admins.get(0);
        }

        return SchoolOnboardingProgressResponse.builder()
                .onboardingId(onboarding.getId())
                .onboardingCode(onboarding.getOnboardingCode())
                .source(onboarding.getSource())
                .onboardingStatus(onboarding.getStatus())
                .currentStep(onboarding.getCurrentStep())
                .completedSteps(onboarding.getCompletedSteps())
                .schoolId(schoolId)
                .schoolCode(school != null ? school.getSchoolCode() : null)
                .schoolName(school != null ? school.getSchoolName() : null)
                .schoolStatus(school != null ? school.getStatus() : null)
                .subscriptionId(subscription != null ? subscription.getId() : null)
                .subscriptionCode(subscription != null ? subscription.getSubscriptionCode() : null)
                .planCode(subscription != null ? subscription.getPlanCode() : null)
                .subscriptionStatus(subscription != null ? subscription.getStatus() : null)
                .subscriptionFinalAmount(subscription != null ? subscription.getFinalAmount() : null)
                .transactionCode(paymentTx != null ? paymentTx.getTransactionCode() : null)
                .paymentStatus(paymentTx != null ? paymentTx.getStatus() : null)
                .paymentProvider(paymentTx != null && paymentTx.getPaymentProvider() != null ? paymentTx.getPaymentProvider().name() : null)
                .tenantId(tenant != null ? tenant.getId() : null)
                .tenantCode(tenant != null ? tenant.getTenantCode() : null)
                .databaseName(tenant != null ? tenant.getDatabaseName() : null)
                .tenantStatus(tenant != null ? tenant.getStatus() : null)
                .provisioningStatus(provisioning != null ? provisioning.getStatus() : null)
                .provisioningLastCompletedStep(provisioning != null ? provisioning.getLastCompletedStep() : null)
                .adminUserCode(adminUser != null ? adminUser.getUserCode() : null)
                .adminEmail(adminUser != null ? adminUser.getEmail() : null)
                .startedAt(onboarding.getStartedAt())
                .submittedAt(onboarding.getSubmittedAt())
                .completedAt(onboarding.getCompletedAt())
                .build();
    }

    // -------------------------------------------------------------------------
    // Helper Methods
    // -------------------------------------------------------------------------

    private SchoolOnboarding findOnboardingOrThrow(UUID id) {
        return onboardingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SchoolOnboarding", id));
    }

    /**
     * Starts provisioning, or resumes it when a previous attempt FAILED (resetting the record via
     * {@link TenantProvisioningService#retryProvisioning(UUID)}), then executes the remaining steps.
     * Completed steps are skipped by the engine, so this is safe to call repeatedly.
     */
    private TenantProvisioning startOrResumeProvisioning(UUID tenantId) {
        TenantProvisioning existing = tenantProvisioningRepository.findByTenantId(tenantId).orElse(null);
        TenantProvisioning record = (existing != null && existing.isFailed())
                ? tenantProvisioningService.retryProvisioning(tenantId)
                : tenantProvisioningService.startProvisioning(tenantId);
        tenantProvisioningService.executeProvisioning(tenantId);
        return record;
    }

    /** Most recently created subscription for the school (repository order is not guaranteed). */
    private SchoolSubscription latestSubscription(List<SchoolSubscription> subs) {
        return subs.stream()
                .max(Comparator.comparing(SchoolSubscription::getCreatedAt,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(subs.get(0));
    }

    /**
     * Records an audit activity. The actor is resolved exclusively from the SecurityContext
     * ({@link OnboardingActor}); request-supplied identities are never used.
     */
    private void recordActivity(SchoolOnboarding onboarding,
                                String action,
                                String previousStatus,
                                String newStatus,
                                Map<String, Object> metadata) {
        OnboardingActivity activity = new OnboardingActivity(
                onboarding,
                action,
                previousStatus,
                newStatus,
                OnboardingActor.currentUserId(),
                OnboardingActor.currentPerformerType(),
                metadata
        );
        activityRepository.save(activity);
    }

    private WorkflowActivationResponse buildActivationResponse(SchoolOnboarding onboarding, String message) {
        School school = onboarding.getSchool();
        UUID schoolId = school != null ? school.getId() : null;

        Tenant tenant = schoolId != null ? tenantRepository.findBySchoolId(schoolId).orElse(null) : null;
        List<SchoolSubscription> subs = schoolId != null ? subscriptionRepository.findBySchoolId(schoolId) : List.of();
        SchoolSubscription subscription = (subs != null && !subs.isEmpty()) ? subs.get(0) : null;

        PaymentTransaction paymentTx = null;
        if (schoolId != null) {
            org.springframework.data.domain.Page<PaymentTransaction> txPage = paymentTransactionRepository.findBySchoolId(schoolId, org.springframework.data.domain.PageRequest.of(0, 1));
            if (txPage != null && txPage.getContent() != null && !txPage.getContent().isEmpty()) {
                paymentTx = txPage.getContent().get(0);
            }
        }

        List<PlatformUser> admins = schoolId != null ? platformUserRepository.findBySchoolId(schoolId) : List.of();
        PlatformUser adminUser = (admins != null && !admins.isEmpty()) ? admins.get(0) : null;

        InitialSchoolAdminResponse adminResponse = adminUser != null ? InitialSchoolAdminResponse.builder()
                .userId(adminUser.getId())
                .userCode(adminUser.getUserCode())
                .email(adminUser.getEmail())
                .fullName(adminUser.getFullName())
                .phone(adminUser.getPhone())
                .role(adminUser.getPlatformRole())
                .schoolId(adminUser.getSchoolId())
                .status(adminUser.getStatus())
                .createdAt(adminUser.getCreatedAt())
                .build() : null;

        return WorkflowActivationResponse.builder()
                .onboardingId(onboarding.getId())
                .onboardingCode(onboarding.getOnboardingCode())
                .schoolId(schoolId)
                .schoolCode(school != null ? school.getSchoolCode() : null)
                .schoolName(school != null ? school.getSchoolName() : null)
                .schoolStatus(school != null ? school.getStatus() : null)
                .tenantId(tenant != null ? tenant.getId() : null)
                .tenantCode(tenant != null ? tenant.getTenantCode() : null)
                .databaseName(tenant != null ? tenant.getDatabaseName() : null)
                .tenantStatus(tenant != null ? tenant.getStatus() : null)
                .subscriptionId(subscription != null ? subscription.getId() : null)
                .subscriptionCode(subscription != null ? subscription.getSubscriptionCode() : null)
                .subscriptionStatus(subscription != null ? subscription.getStatus() : null)
                .transactionCode(paymentTx != null ? paymentTx.getTransactionCode() : null)
                .paymentStatus(paymentTx != null ? paymentTx.getStatus() : null)
                .admin(adminResponse)
                .onboardingStatus(onboarding.getStatus())
                .activatedAt(onboarding.getCompletedAt() != null ? onboarding.getCompletedAt() : Instant.now())
                .message(message)
                .build();
    }
}
