package com.vidya.master.onboarding.orchestration;

import com.vidya.master.onboarding.entity.OnboardingSource;
import com.vidya.master.onboarding.entity.OnboardingStatus;
import com.vidya.master.onboarding.entity.SchoolOnboarding;
import com.vidya.master.onboarding.orchestration.dto.CompleteOnboardingRequest;
import com.vidya.master.onboarding.orchestration.dto.OnboardingActivationResponse;
import com.vidya.master.onboarding.repository.OnboardingActivityRepository;
import com.vidya.master.onboarding.repository.SchoolOnboardingRepository;
import com.vidya.master.payment.dto.InitiatePaymentResponse;
import com.vidya.master.payment.dto.PaymentTransactionResponse;
import com.vidya.master.payment.entity.PaymentMethod;
import com.vidya.master.payment.entity.PaymentProvider;
import com.vidya.master.payment.entity.PaymentStatus;
import com.vidya.master.payment.service.PaymentService;
import com.vidya.master.school.entity.School;
import com.vidya.master.school.entity.SchoolBoard;
import com.vidya.master.school.entity.SchoolStatus;
import com.vidya.master.school.entity.SchoolType;
import com.vidya.master.school.repository.SchoolRepository;
import com.vidya.master.subscription.dto.SchoolSubscriptionResponse;
import com.vidya.master.subscription.entity.BillingCycle;
import com.vidya.master.subscription.entity.SubscriptionStatus;
import com.vidya.master.subscription.service.SchoolSubscriptionService;
import com.vidya.master.tenant.entity.Tenant;
import com.vidya.master.tenant.repository.TenantRepository;
import com.vidya.tenant.provisioning.TenantProvisioningService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OnboardingOrchestrationServiceTest {

    @Mock
    private SchoolOnboardingRepository onboardingRepository;

    @Mock
    private SchoolRepository schoolRepository;

    @Mock
    private SchoolSubscriptionService subscriptionService;

    @Mock
    private PaymentService paymentService;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private TenantProvisioningService tenantProvisioningService;

    @Mock
    private OnboardingActivityRepository activityRepository;

    @InjectMocks
    private OnboardingOrchestrationServiceImpl orchestrationService;

    private UUID onboardingId;
    private UUID schoolId;
    private UUID planId;
    private SchoolOnboarding onboarding;
    private School school;

    @BeforeEach
    void setUp() {
        onboardingId = UUID.randomUUID();
        schoolId = UUID.randomUUID();
        planId = UUID.randomUUID();

        school = new School(
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

        onboarding = new SchoolOnboarding(
                "ONB-12345678",
                school,
                OnboardingSource.DIRECT,
                null,
                null
        );
        onboarding.setId(onboardingId);
        onboarding.setStatus(OnboardingStatus.APPROVED);
    }

    @Test
    @DisplayName("completeOnboardingAndActivate should execute complete pipeline and return activation response")
    void completeOnboardingAndActivate_Success() {
        CompleteOnboardingRequest request = CompleteOnboardingRequest.builder()
                .planId(planId)
                .billingCycle(BillingCycle.ANNUAL)
                .paymentProvider(PaymentProvider.MANUAL)
                .paymentMethod(PaymentMethod.NEFT)
                .referenceNumber("NEFT-UTR-999")
                .confirmedBy("superadmin@vidya.io")
                .build();

        SchoolSubscriptionResponse sampleSubscription = new SchoolSubscriptionResponse(
                UUID.randomUUID(),
                "SUB-99999",
                schoolId,
                planId,
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
                null,
                Instant.now(),
                Instant.now()
        );

        InitiatePaymentResponse sampleInitiatePayment = InitiatePaymentResponse.builder()
                .transactionCode("TXN-PAY-001")
                .schoolId(schoolId)
                .amount(new BigDecimal("59000.00"))
                .currency("INR")
                .status(PaymentStatus.INITIATED)
                .paymentProvider(PaymentProvider.MANUAL)
                .build();

        PaymentTransactionResponse sampleConfirmedPayment = PaymentTransactionResponse.builder()
                .transactionCode("TXN-PAY-001")
                .status(PaymentStatus.SUCCESS)
                .paymentMethod(PaymentMethod.NEFT)
                .providerTransactionId("NEFT-UTR-999")
                .build();

        Tenant sampleTenant = Tenant.create(
                schoolId,
                "gw123456",
                "localhost",
                5432,
                "vidya_tenant_gw123456",
                "TENANT_DB_USER",
                "TENANT_DB_PASS"
        );

        when(onboardingRepository.findById(onboardingId)).thenReturn(Optional.of(onboarding));
        when(schoolRepository.findById(schoolId)).thenReturn(Optional.of(school));
        when(subscriptionService.subscribeSchool(any())).thenReturn(sampleSubscription);
        when(paymentService.initiatePayment(any())).thenReturn(sampleInitiatePayment);
        when(paymentService.confirmManualPayment(any())).thenReturn(sampleConfirmedPayment);
        when(tenantRepository.findBySchoolId(schoolId)).thenReturn(Optional.of(sampleTenant));
        when(tenantRepository.save(any())).thenReturn(sampleTenant);
        when(schoolRepository.save(any())).thenReturn(school);
        when(onboardingRepository.save(any())).thenReturn(onboarding);

        OnboardingActivationResponse response = orchestrationService.completeOnboardingAndActivate(onboardingId, request);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OnboardingStatus.ACTIVE);
        assertThat(response.getSchoolCode()).isEqualTo("SCH-12345678");
        assertThat(response.getSubscriptionCode()).isEqualTo("SUB-99999");
        assertThat(response.getTransactionCode()).isEqualTo("TXN-PAY-001");

        verify(tenantProvisioningService).startProvisioning(sampleTenant.getId());
        verify(tenantProvisioningService).executeProvisioning(sampleTenant.getId());
        verify(activityRepository).save(any());
    }
}
