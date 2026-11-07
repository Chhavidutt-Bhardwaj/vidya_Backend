package com.vidya.master.onboarding.orchestration;

import com.vidya.common.api.ApiResponseCode;
import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.InvalidStateTransitionException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.master.onboarding.entity.OnboardingStatus;
import com.vidya.master.onboarding.entity.SchoolOnboarding;
import com.vidya.master.onboarding.orchestration.dto.CompleteOnboardingRequest;
import com.vidya.master.onboarding.orchestration.dto.OnboardingActivationResponse;
import com.vidya.master.onboarding.repository.SchoolOnboardingRepository;
import com.vidya.master.workflow.dto.WorkflowActivateRequest;
import com.vidya.master.workflow.dto.WorkflowActivationResponse;
import com.vidya.master.workflow.service.SchoolOnboardingWorkflowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * "One-click" activation endpoint ({@code POST /api/v1/onboarding/{id}/activate}).
 *
 * <p><strong>Changed in onboarding I0.</strong> This endpoint previously created a subscription,
 * initiated and self-confirmed a payment, provisioned a tenant on a hard-coded host and activated
 * the school from almost any status — bypassing verification, payment and approval. It now only
 * finishes an onboarding that has already been <em>approved</em>: it (re)runs tenant provisioning
 * when needed and activates, delegating to the guarded {@link SchoolOnboardingWorkflowService}
 * so there is a single implementation of each transition.
 *
 * <p>Plan selection and payment must go through {@code /select-plan}, {@code /payment/initiate}
 * (or an authorized {@code /payment/waive}) and {@code /approve} first. The plan/payment fields of
 * {@link CompleteOnboardingRequest} are deprecated and ignored.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OnboardingOrchestrationServiceImpl implements OnboardingOrchestrationService {

    /** States from which provisioning must (re)run before activation. */
    private static final Set<OnboardingStatus> NEEDS_PROVISIONING = EnumSet.of(
            OnboardingStatus.APPROVED,
            OnboardingStatus.PROVISIONING,
            OnboardingStatus.PROVISIONING_FAILED,
            OnboardingStatus.FAILED);

    private final SchoolOnboardingRepository onboardingRepository;
    private final SchoolOnboardingWorkflowService workflowService;

    @Override
    public OnboardingActivationResponse completeOnboardingAndActivate(UUID onboardingId, CompleteOnboardingRequest request) {
        log.info("Activation requested for onboarding ID: {}", onboardingId);

        SchoolOnboarding onboarding = onboardingRepository.findById(onboardingId)
                .orElseThrow(() -> new ResourceNotFoundException("SchoolOnboarding", onboardingId));

        OnboardingStatus status = onboarding.getStatus();
        if (status == OnboardingStatus.ACTIVE) {
            throw new BusinessException(ApiResponseCode.BUSINESS_ERROR, "School onboarding is already completed and active");
        }
        if (!NEEDS_PROVISIONING.contains(status) && status != OnboardingStatus.PROVISIONED) {
            // DRAFT, SUBMITTED, VERIFIED, PLAN_SELECTED, PAYMENT_PENDING, PAYMENT_COMPLETED, REJECTED, ...
            throw new InvalidStateTransitionException("SchoolOnboarding", status.name(), OnboardingStatus.ACTIVE.name());
        }

        if (request != null && (request.getPlanId() != null || request.getPaymentProvider() != null
                || request.getConfirmedBy() != null)) {
            log.warn("Onboarding {}: deprecated plan/payment fields on /activate are ignored", onboarding.getOnboardingCode());
        }

        if (NEEDS_PROVISIONING.contains(status)) {
            workflowService.provisionTenant(onboardingId, null);
        }

        String notes = request != null ? request.getPaymentNotes() : null;
        WorkflowActivationResponse activation = workflowService.activateSchool(
                onboardingId, new WorkflowActivateRequest(null, notes));

        return OnboardingActivationResponse.builder()
                .onboardingId(activation.getOnboardingId())
                .onboardingCode(activation.getOnboardingCode())
                .schoolId(activation.getSchoolId())
                .schoolCode(activation.getSchoolCode())
                .tenantId(activation.getTenantId())
                .tenantCode(activation.getTenantCode())
                .databaseName(activation.getDatabaseName())
                .subscriptionId(activation.getSubscriptionId())
                .subscriptionCode(activation.getSubscriptionCode())
                .transactionCode(activation.getTransactionCode())
                .status(activation.getOnboardingStatus())
                .message(activation.getMessage())
                .activatedAt(activation.getActivatedAt() != null ? activation.getActivatedAt() : Instant.now())
                .build();
    }
}
