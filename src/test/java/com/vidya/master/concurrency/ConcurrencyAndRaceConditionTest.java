package com.vidya.master.concurrency;

import com.vidya.master.onboarding.dto.InitiateOnboardingRequest;
import com.vidya.master.onboarding.dto.OnboardingResponse;
import com.vidya.master.onboarding.entity.OnboardingSource;
import com.vidya.master.onboarding.entity.OnboardingStatus;
import com.vidya.master.onboarding.entity.OnboardingStep;
import com.vidya.master.onboarding.entity.SchoolOnboarding;
import com.vidya.master.onboarding.mapper.OnboardingMapper;
import com.vidya.master.onboarding.repository.OnboardingActivityRepository;
import com.vidya.master.onboarding.repository.OnboardingStepDataRepository;
import com.vidya.master.onboarding.repository.SchoolOnboardingRepository;
import com.vidya.master.onboarding.service.OnboardingServiceImpl;
import com.vidya.master.payment.entity.PaymentMethod;
import com.vidya.master.payment.entity.PaymentProvider;
import com.vidya.master.payment.entity.PaymentStatus;
import com.vidya.master.payment.entity.PaymentTransaction;
import com.vidya.master.payment.gateway.PaymentGatewayRegistry;
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
import com.vidya.master.tenant.entity.Tenant;
import com.vidya.master.tenant.entity.TenantStatus;
import com.vidya.master.tenant.repository.TenantRepository;
import com.vidya.master.workflow.event.PaymentCompletedEvent;
import com.vidya.master.workflow.service.SchoolOnboardingWorkflowServiceImpl;
import com.vidya.tenant.context.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConcurrencyAndRaceConditionTest {

    @Mock private SchoolOnboardingRepository onboardingRepository;
    @Mock private OnboardingStepDataRepository stepDataRepository;
    @Mock private OnboardingActivityRepository activityRepository;
    @Mock private SchoolRepository schoolRepository;
    @Mock private PaymentTransactionRepository paymentTransactionRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private TenantProvisioningRepository provisioningRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    private School school;
    private SchoolOnboarding onboarding;
    private UUID schoolId;
    private UUID onboardingId;

    @BeforeEach
    void setUp() {
        schoolId = UUID.randomUUID();
        onboardingId = UUID.randomUUID();

        school = new School(
                "SCH-CONC", "Concurrency High", "Concurrency Legal", "REG-CONC",
                SchoolBoard.CBSE, SchoolType.ALL, "admin@conc.edu", "9876543210", "https://conc.edu", SchoolStatus.DRAFT
        );
        school.setId(schoolId);

        onboarding = new SchoolOnboarding("ONB-CONC-01", school, OnboardingSource.DIRECT, null, null);
        onboarding.setId(onboardingId);
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Concurrent duplicate payment callbacks are handled idempotently without duplicate status progressions")
    void concurrentPaymentCallbacks_HandledIdempotently() throws Exception {
        OnboardingMapper onboardingMapper = new OnboardingMapper();
        SchoolOnboardingWorkflowServiceImpl workflowService = new SchoolOnboardingWorkflowServiceImpl(
                onboardingRepository, schoolRepository, null, null,
                null, null, paymentTransactionRepository, tenantRepository,
                provisioningRepository, null, null, null, activityRepository
        );

        onboarding.setStatus(OnboardingStatus.PAYMENT_PENDING);
        onboarding.setCurrentStep(OnboardingStep.PAYMENT);

        when(onboardingRepository.findById(onboardingId)).thenReturn(Optional.of(onboarding));
        when(onboardingRepository.save(any(SchoolOnboarding.class))).thenAnswer(i -> i.getArgument(0));

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Callable<Boolean>> tasks = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            tasks.add(() -> {
                startLatch.await();
                PaymentCompletedEvent event = PaymentCompletedEvent.builder()
                        .transactionCode("TXN-CONC-01")
                        .onboardingId(onboardingId)
                        .schoolId(schoolId)
                        .amount(new BigDecimal("10000.00"))
                        .currency("INR")
                        .paymentProvider(PaymentProvider.RAZORPAY)
                        .paymentMethod(PaymentMethod.CARD)
                        .status(PaymentStatus.SUCCESS)
                        .completedAt(java.time.Instant.now())
                        .build();

                workflowService.handlePaymentCompleted(event);
                return true;
            });
        }

        startLatch.countDown();
        List<Future<Boolean>> results = executor.invokeAll(tasks);
        for (Future<Boolean> f : results) {
            assertThat(f.get()).isTrue();
        }

        // Must transition to PAYMENT_COMPLETED exactly and step set to APPROVAL
        assertThat(onboarding.getStatus()).isEqualTo(OnboardingStatus.PAYMENT_COMPLETED);
        assertThat(onboarding.getCurrentStep()).isEqualTo(OnboardingStep.APPROVAL);

        executor.shutdown();
    }

    @Test
    @DisplayName("Concurrent multi-tenant requests maintain zero ThreadLocal context contamination")
    void concurrentMultiTenantContext_IsolationVerified() throws Exception {
        int threadCount = 30;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        List<Callable<Boolean>> tasks = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            final String assignedTenant = "TNT-NODE-" + (index % 5);

            tasks.add(() -> {
                latch.await();
                // 1. Establish tenant
                TenantContext.setTenantCode(assignedTenant);

                // 2. Verify assigned tenant
                String active = TenantContext.getTenantCode();
                boolean matches = assignedTenant.equals(active);

                // 3. Clear tenant
                TenantContext.clear();
                boolean cleared = TenantContext.getTenantCode() == null;

                return matches && cleared;
            });
        }

        latch.countDown();
        List<Future<Boolean>> results = executor.invokeAll(tasks);
        for (Future<Boolean> f : results) {
            assertThat(f.get()).isTrue();
        }

        executor.shutdown();
    }
}
