package com.vidya.master.subscription.service;

import com.vidya.common.exception.InvalidStateTransitionException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.master.school.repository.SchoolRepository;
import com.vidya.master.subscription.dto.SchoolSubscriptionResponse;
import com.vidya.master.subscription.dto.SubscribeSchoolRequest;
import com.vidya.master.subscription.entity.BillingCycle;
import com.vidya.master.subscription.entity.PlanStatus;
import com.vidya.master.subscription.entity.SchoolSubscription;
import com.vidya.master.subscription.entity.SubscriptionPlan;
import com.vidya.master.subscription.entity.SubscriptionStatus;
import com.vidya.master.subscription.mapper.SubscriptionMapper;
import com.vidya.master.subscription.repository.SchoolSubscriptionRepository;
import com.vidya.master.subscription.repository.SubscriptionPlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SchoolSubscriptionServiceTest {

    @Mock
    private SchoolRepository schoolRepository;

    @Mock
    private SubscriptionPlanRepository subscriptionPlanRepository;

    @Mock
    private SchoolSubscriptionRepository schoolSubscriptionRepository;

    @Spy
    private SubscriptionMapper subscriptionMapper = new SubscriptionMapper();

    @InjectMocks
    private SchoolSubscriptionServiceImpl schoolSubscriptionService;

    private SubscriptionPlan samplePlan;
    private SchoolSubscription sampleSubscription;
    private UUID schoolId;
    private UUID planId;
    private UUID subscriptionId;

    @BeforeEach
    void setUp() {
        schoolId = UUID.randomUUID();
        planId = UUID.randomUUID();
        subscriptionId = UUID.randomUUID();

        samplePlan = new SubscriptionPlan(
                "STANDARD",
                1,
                "Standard Plan",
                "Description",
                BillingCycle.MONTHLY,
                new BigDecimal("5000.00"),
                "INR",
                14,
                PlanStatus.ACTIVE,
                LocalDate.now()
        );
        samplePlan.setId(planId);

        sampleSubscription = new SchoolSubscription(
                "SUB-12345678",
                schoolId,
                samplePlan,
                "STANDARD",
                1,
                LocalDate.now(),
                LocalDate.now().plusMonths(1),
                BillingCycle.MONTHLY,
                new BigDecimal("5000.00"),
                BigDecimal.ZERO,
                new BigDecimal("900.00"),
                new BigDecimal("5900.00"),
                "INR",
                SubscriptionStatus.ACTIVE,
                true,
                null
        );
        sampleSubscription.setId(subscriptionId);
    }

    @Test
    @DisplayName("subscribeSchool should calculate GST and save subscription")
    void subscribeSchool_Success() {
        SubscribeSchoolRequest request = new SubscribeSchoolRequest(
                schoolId, planId, BillingCycle.MONTHLY, BigDecimal.ZERO, true, false);

        when(schoolRepository.existsById(schoolId)).thenReturn(true);
        when(subscriptionPlanRepository.findById(planId)).thenReturn(Optional.of(samplePlan));
        when(schoolSubscriptionRepository.findBySubscriptionCode(any())).thenReturn(Optional.empty());
        when(schoolSubscriptionRepository.save(any(SchoolSubscription.class))).thenAnswer(inv -> {
            SchoolSubscription s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        SchoolSubscriptionResponse response = schoolSubscriptionService.subscribeSchool(request);

        assertThat(response).isNotNull();
        assertThat(response.baseAmount()).isEqualByComparingTo("5000.00");
        assertThat(response.taxAmount()).isEqualByComparingTo("900.00"); // 18% GST of 5000
        assertThat(response.finalAmount()).isEqualByComparingTo("5900.00");
        assertThat(response.status()).isEqualTo(SubscriptionStatus.ACTIVE);
        verify(schoolSubscriptionRepository).save(any(SchoolSubscription.class));
    }

    @Test
    @DisplayName("subscribeSchool with trial should set TRIAL status and trialEndDate")
    void subscribeSchool_WithTrial_Success() {
        SubscribeSchoolRequest request = new SubscribeSchoolRequest(
                schoolId, planId, BillingCycle.MONTHLY, BigDecimal.ZERO, true, true);

        when(schoolRepository.existsById(schoolId)).thenReturn(true);
        when(subscriptionPlanRepository.findById(planId)).thenReturn(Optional.of(samplePlan));
        when(schoolSubscriptionRepository.findBySubscriptionCode(any())).thenReturn(Optional.empty());
        when(schoolSubscriptionRepository.save(any(SchoolSubscription.class))).thenAnswer(inv -> {
            SchoolSubscription s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        SchoolSubscriptionResponse response = schoolSubscriptionService.subscribeSchool(request);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(SubscriptionStatus.TRIAL);
        assertThat(response.trialEndDate()).isEqualTo(LocalDate.now().plusDays(14));
    }

    @Test
    @DisplayName("getActiveSubscriptionForSchool should return active subscription")
    void getActiveSubscriptionForSchool_Success() {
        when(schoolSubscriptionRepository.findActiveSubscriptionsBySchoolId(schoolId))
                .thenReturn(List.of(sampleSubscription));

        SchoolSubscriptionResponse response = schoolSubscriptionService.getActiveSubscriptionForSchool(schoolId);

        assertThat(response).isNotNull();
        assertThat(response.subscriptionCode()).isEqualTo("SUB-12345678");
        assertThat(response.schoolId()).isEqualTo(schoolId);
    }

    @Test
    @DisplayName("cancelSubscription should set status to CANCELLED")
    void cancelSubscription_Success() {
        when(schoolSubscriptionRepository.findById(subscriptionId)).thenReturn(Optional.of(sampleSubscription));
        when(schoolSubscriptionRepository.save(any(SchoolSubscription.class))).thenReturn(sampleSubscription);

        SchoolSubscriptionResponse response = schoolSubscriptionService.cancelSubscription(subscriptionId);

        assertThat(response).isNotNull();
        assertThat(sampleSubscription.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
        assertThat(sampleSubscription.isAutoRenew()).isFalse();
    }

    @Test
    @DisplayName("cancelSubscription when already cancelled should throw InvalidStateTransitionException")
    void cancelSubscription_AlreadyCancelled_ThrowsException() {
        sampleSubscription.setStatus(SubscriptionStatus.CANCELLED);
        when(schoolSubscriptionRepository.findById(subscriptionId)).thenReturn(Optional.of(sampleSubscription));

        assertThatThrownBy(() -> schoolSubscriptionService.cancelSubscription(subscriptionId))
                .isInstanceOf(InvalidStateTransitionException.class);
    }
}
