package com.vidya.master.onboarding.orchestration;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.InvalidStateTransitionException;
import com.vidya.master.onboarding.entity.OnboardingSource;
import com.vidya.master.onboarding.entity.OnboardingStatus;
import com.vidya.master.onboarding.entity.SchoolOnboarding;
import com.vidya.master.onboarding.orchestration.dto.CompleteOnboardingRequest;
import com.vidya.master.onboarding.orchestration.dto.OnboardingActivationResponse;
import com.vidya.master.onboarding.repository.SchoolOnboardingRepository;
import com.vidya.master.payment.entity.PaymentProvider;
import com.vidya.master.school.entity.School;
import com.vidya.master.school.entity.SchoolBoard;
import com.vidya.master.school.entity.SchoolStatus;
import com.vidya.master.school.entity.SchoolType;
import com.vidya.master.subscription.entity.BillingCycle;
import com.vidya.master.workflow.dto.WorkflowActivationResponse;
import com.vidya.master.workflow.service.SchoolOnboardingWorkflowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@code /activate} contract since onboarding I0: it only finishes an APPROVED onboarding
 * (provision if needed, then activate) by delegating to the guarded workflow service. It never
 * subscribes, takes or self-confirms a payment, or activates an unapproved onboarding.
 */
@ExtendWith(MockitoExtension.class)
class OnboardingOrchestrationServiceTest {

    @Mock
    private SchoolOnboardingRepository onboardingRepository;

    @Mock
    private SchoolOnboardingWorkflowService workflowService;

    @InjectMocks
    private OnboardingOrchestrationServiceImpl orchestrationService;

    private UUID onboardingId;
    private UUID schoolId;
    private SchoolOnboarding onboarding;

    @BeforeEach
    void setUp() {
        onboardingId = UUID.randomUUID();
        schoolId = UUID.randomUUID();

        School school = new School(
                "SCH-12345678",
                "Greenwood High",
                "Greenwood Education Trust",
                "REG-12345",
                SchoolBoard.CBSE,
                SchoolType.PRIMARY,
                "admin@greenwood.edu.in",
                "9876543210",
                "https://greenwood.edu.in",
                SchoolStatus.PENDING
        );
        school.setId(schoolId);

        onboarding = new SchoolOnboarding("ONB-12345678", school, OnboardingSource.DIRECT, null, null);
        onboarding.setId(onboardingId);
        onboarding.setStatus(OnboardingStatus.APPROVED);
    }

    private WorkflowActivationResponse activated() {
        return WorkflowActivationResponse.builder()
                .onboardingId(onboardingId)
                .onboardingCode("ONB-12345678")
                .schoolId(schoolId)
                .schoolCode("SCH-12345678")
                .tenantId(UUID.randomUUID())
                .tenantCode("gw123456")
                .databaseName("vidya_tenant_gw123456")
                .subscriptionCode("SUB-99999")
                .transactionCode("TXN-PAY-001")
                .onboardingStatus(OnboardingStatus.ACTIVE)
                .message("School onboarding successfully activated and tenant operational")
                .activatedAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("completeOnboardingAndActivate provisions and activates an APPROVED onboarding via the workflow")
    void completeOnboardingAndActivate_Success() {
        when(onboardingRepository.findById(onboardingId)).thenReturn(Optional.of(onboarding));
        when(workflowService.activateSchool(eq(onboardingId), any())).thenReturn(activated());

        OnboardingActivationResponse response = orchestrationService.completeOnboardingAndActivate(onboardingId, null);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OnboardingStatus.ACTIVE);
        assertThat(response.getSchoolCode()).isEqualTo("SCH-12345678");
        assertThat(response.getSubscriptionCode()).isEqualTo("SUB-99999");
        assertThat(response.getTransactionCode()).isEqualTo("TXN-PAY-001");

        verify(workflowService).provisionTenant(eq(onboardingId), isNull());
        verify(workflowService).activateSchool(eq(onboardingId), any());
    }

    @Test
    @DisplayName("an already PROVISIONED onboarding is activated without re-provisioning")
    void completeOnboardingAndActivate_AlreadyProvisioned_SkipsProvisioning() {
        onboarding.setStatus(OnboardingStatus.PROVISIONED);
        when(onboardingRepository.findById(onboardingId)).thenReturn(Optional.of(onboarding));
        when(workflowService.activateSchool(eq(onboardingId), any())).thenReturn(activated());

        orchestrationService.completeOnboardingAndActivate(onboardingId, null);

        verify(workflowService, never()).provisionTenant(any(), any());
        verify(workflowService).activateSchool(eq(onboardingId), any());
    }

    @Test
    @DisplayName("deprecated plan/payment fields are ignored — no subscription or self-confirmed payment")
    void completeOnboardingAndActivate_DeprecatedPaymentFields_Ignored() {
        when(onboardingRepository.findById(onboardingId)).thenReturn(Optional.of(onboarding));
        when(workflowService.activateSchool(eq(onboardingId), any())).thenReturn(activated());

        CompleteOnboardingRequest legacy = CompleteOnboardingRequest.builder()
                .planId(UUID.randomUUID())
                .billingCycle(BillingCycle.ANNUAL)
                .paymentProvider(PaymentProvider.MANUAL)
                .confirmedBy("someone-else")
                .build();

        orchestrationService.completeOnboardingAndActivate(onboardingId, legacy);

        verify(workflowService, never()).selectPlan(any(), any());
        verify(workflowService, never()).initiatePayment(any(), any());
        verify(workflowService, never()).waivePayment(any(), any());
    }

    @ParameterizedTest(name = "activation from {0} is rejected")
    @EnumSource(value = OnboardingStatus.class, names = {
            "DRAFT", "SUBMITTED", "VERIFICATION_PENDING", "VERIFIED", "PLAN_SELECTED",
            "PAYMENT_PENDING", "PAYMENT_COMPLETED", "PAYMENT_WAIVED", "APPROVAL_PENDING",
            "REJECTED", "CANCELLED"})
    @DisplayName("I0: onboardings that are not approved cannot be activated")
    void completeOnboardingAndActivate_NotApproved_Rejected(OnboardingStatus status) {
        onboarding.setStatus(status);
        when(onboardingRepository.findById(onboardingId)).thenReturn(Optional.of(onboarding));

        assertThatThrownBy(() -> orchestrationService.completeOnboardingAndActivate(onboardingId, null))
                .isInstanceOf(InvalidStateTransitionException.class);
        verifyNoInteractions(workflowService);
    }

    @Test
    @DisplayName("an ACTIVE onboarding is reported as already active")
    void completeOnboardingAndActivate_AlreadyActive() {
        onboarding.setStatus(OnboardingStatus.ACTIVE);
        when(onboardingRepository.findById(onboardingId)).thenReturn(Optional.of(onboarding));

        assertThatThrownBy(() -> orchestrationService.completeOnboardingAndActivate(onboardingId, null))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(workflowService);
    }
}
