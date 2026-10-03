package com.vidya.master.onboarding.orchestration;

import com.vidya.common.api.ApiResponseCode;
import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.InvalidStateTransitionException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.common.util.CodeGenerator;
import com.vidya.master.onboarding.entity.OnboardingActivity;
import com.vidya.master.onboarding.entity.OnboardingStatus;
import com.vidya.master.onboarding.entity.PerformerType;
import com.vidya.master.onboarding.entity.SchoolOnboarding;
import com.vidya.master.onboarding.orchestration.dto.CompleteOnboardingRequest;
import com.vidya.master.onboarding.orchestration.dto.OnboardingActivationResponse;
import com.vidya.master.onboarding.repository.OnboardingActivityRepository;
import com.vidya.master.onboarding.repository.SchoolOnboardingRepository;
import com.vidya.master.payment.dto.InitiatePaymentRequest;
import com.vidya.master.payment.dto.InitiatePaymentResponse;
import com.vidya.master.payment.dto.ManualPaymentConfirmRequest;
import com.vidya.master.payment.dto.PaymentTransactionResponse;
import com.vidya.master.payment.entity.PaymentMethod;
import com.vidya.master.payment.entity.PaymentProvider;
import com.vidya.master.payment.service.PaymentService;
import com.vidya.master.school.entity.School;
import com.vidya.master.school.entity.SchoolStatus;
import com.vidya.master.school.repository.SchoolRepository;
import com.vidya.master.subscription.dto.SchoolSubscriptionResponse;
import com.vidya.master.subscription.dto.SubscribeSchoolRequest;
import com.vidya.master.subscription.service.SchoolSubscriptionService;
import com.vidya.master.tenant.entity.Tenant;
import com.vidya.master.tenant.repository.TenantRepository;
import com.vidya.tenant.provisioning.TenantProvisioningService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OnboardingOrchestrationServiceImpl implements OnboardingOrchestrationService {

    private final SchoolOnboardingRepository onboardingRepository;
    private final SchoolRepository schoolRepository;
    private final SchoolSubscriptionService subscriptionService;
    private final PaymentService paymentService;
    private final TenantRepository tenantRepository;
    private final TenantProvisioningService tenantProvisioningService;
    private final OnboardingActivityRepository activityRepository;

    @Override
    public OnboardingActivationResponse completeOnboardingAndActivate(UUID onboardingId, CompleteOnboardingRequest request) {
        log.info("Starting complete onboarding activation pipeline for onboarding ID: {}", onboardingId);

        // 1. Validate Onboarding Application
        SchoolOnboarding onboarding = onboardingRepository.findById(onboardingId)
                .orElseThrow(() -> new ResourceNotFoundException("SchoolOnboarding", onboardingId));

        if (onboarding.getStatus() == OnboardingStatus.ACTIVE) {
            throw new BusinessException(ApiResponseCode.BUSINESS_ERROR, "School onboarding is already completed and active");
        }
        if (onboarding.getStatus() == OnboardingStatus.REJECTED || onboarding.getStatus() == OnboardingStatus.CANCELLED || onboarding.getStatus() == OnboardingStatus.FAILED) {
            throw new InvalidStateTransitionException("SchoolOnboarding", onboarding.getStatus().name(), OnboardingStatus.ACTIVE.name());
        }

        UUID schoolId = onboarding.getSchool() != null ? onboarding.getSchool().getId() : null;
        if (schoolId == null) {
            throw new BusinessException(ApiResponseCode.BUSINESS_ERROR, "Onboarding record does not have an associated school");
        }

        School school = schoolRepository.findById(schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("School", schoolId));

        String previousStatus = onboarding.getStatus().name();

        // 2. Select & Lock Subscription Plan
        log.info("Attaching subscription plan: {} to school: {}", request.getPlanId(), schoolId);
        SubscribeSchoolRequest subRequest = new SubscribeSchoolRequest(
                schoolId,
                request.getPlanId(),
                request.getBillingCycle(),
                request.getDiscountAmount() != null ? request.getDiscountAmount() : BigDecimal.ZERO,
                true,
                false
        );
        SchoolSubscriptionResponse subscription = subscriptionService.subscribeSchool(subRequest);

        // 3. Process Payment
        String idempotencyKey = request.getIdempotencyKey() != null
                ? request.getIdempotencyKey()
                : "ONB-PAY-" + onboarding.getOnboardingCode() + "-" + System.currentTimeMillis();

        InitiatePaymentRequest payRequest = InitiatePaymentRequest.builder()
                .schoolId(schoolId)
                .subscriptionId(subscription.id())
                .onboardingId(onboarding.getId())
                .amount(subscription.finalAmount())
                .currency(subscription.currency())
                .paymentProvider(request.getPaymentProvider())
                .paymentMethod(request.getPaymentMethod())
                .idempotencyKey(idempotencyKey)
                .customerName(school.getSchoolName())
                .customerEmail(school.getEmail())
                .customerPhone(school.getPhone())
                .build();

        InitiatePaymentResponse payInitiateRes = paymentService.initiatePayment(payRequest);
        String txnCode = payInitiateRes.getTransactionCode();

        // Settle manual payment immediately if selected
        if (request.getPaymentProvider() == PaymentProvider.MANUAL) {
            ManualPaymentConfirmRequest confirmRequest = ManualPaymentConfirmRequest.builder()
                    .transactionCode(txnCode)
                    .paymentMethod(request.getPaymentMethod() != null ? request.getPaymentMethod() : PaymentMethod.NEFT)
                    .referenceNumber(request.getReferenceNumber() != null ? request.getReferenceNumber() : "MANUAL-ONB-" + CodeGenerator.generate(6))
                    .confirmedBy(request.getConfirmedBy() != null ? request.getConfirmedBy() : "system")
                    .notes(request.getPaymentNotes() != null ? request.getPaymentNotes() : "Onboarding manual fee settlement")
                    .build();
            PaymentTransactionResponse confirmedPay = paymentService.confirmManualPayment(confirmRequest);
            log.info("Manual payment settled for transaction: {}", confirmedPay.getTransactionCode());
        }

        // 4. Provision PostgreSQL Database for Tenant
        Tenant tenant = tenantRepository.findBySchoolId(schoolId)
                .orElseGet(() -> {
                    String tenantCode = CodeGenerator.tenantCode();
                    String dbName = "vidya_tenant_" + tenantCode;
                    Tenant newTenant = Tenant.create(
                            schoolId,
                            tenantCode,
                            "localhost",
                            5432,
                            dbName,
                            "TENANT_DB_USER",
                            "TENANT_DB_PASS"
                    );
                    return tenantRepository.save(newTenant);
                });

        log.info("Starting automated tenant provisioning for tenantCode: {}", tenant.getTenantCode());
        tenantProvisioningService.startProvisioning(tenant.getId());
        tenantProvisioningService.executeProvisioning(tenant.getId());
        tenant.markActive();
        tenantRepository.save(tenant);

        // 5. Activate School & Onboarding records
        school.setTenantId(tenant.getId());
        school.setStatus(SchoolStatus.ACTIVE);
        schoolRepository.save(school);

        onboarding.setStatus(OnboardingStatus.ACTIVE);
        onboarding.setCompletedAt(Instant.now());
        onboardingRepository.save(onboarding);

        // 6. Record Audit Log Entry
        Map<String, Object> auditMetadata = new HashMap<>();
        auditMetadata.put("tenantCode", tenant.getTenantCode());
        auditMetadata.put("databaseName", tenant.getDatabaseName());
        auditMetadata.put("subscriptionCode", subscription.subscriptionCode());
        auditMetadata.put("transactionCode", txnCode);
        auditMetadata.put("amount", subscription.finalAmount());

        OnboardingActivity activity = new OnboardingActivity(
                onboarding,
                "ONBOARDING_ACTIVATED",
                previousStatus,
                OnboardingStatus.ACTIVE.name(),
                null,
                PerformerType.SYSTEM,
                auditMetadata
        );
        activityRepository.save(activity);

        log.info("✅ Pipeline successfully completed for School: [{}], Tenant: [{}]",
                school.getSchoolName(), tenant.getTenantCode());

        return OnboardingActivationResponse.builder()
                .onboardingId(onboarding.getId())
                .onboardingCode(onboarding.getOnboardingCode())
                .schoolId(school.getId())
                .schoolCode(school.getSchoolCode())
                .tenantId(tenant.getId())
                .tenantCode(tenant.getTenantCode())
                .databaseName(tenant.getDatabaseName())
                .subscriptionId(subscription.id())
                .subscriptionCode(subscription.subscriptionCode())
                .transactionCode(txnCode)
                .status(OnboardingStatus.ACTIVE)
                .message("School onboarding successfully completed and tenant provisioned")
                .activatedAt(Instant.now())
                .build();
    }
}
