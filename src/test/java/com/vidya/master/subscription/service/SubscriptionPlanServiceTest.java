package com.vidya.master.subscription.service;

import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.master.subscription.dto.CreateSubscriptionPlanRequest;
import com.vidya.master.subscription.dto.PlanFeatureRequest;
import com.vidya.master.subscription.dto.SubscriptionPlanResponse;
import com.vidya.master.subscription.entity.BillingCycle;
import com.vidya.master.subscription.entity.FeatureCategory;
import com.vidya.master.subscription.entity.PlanStatus;
import com.vidya.master.subscription.entity.SubscriptionFeature;
import com.vidya.master.subscription.entity.SubscriptionPlan;
import com.vidya.master.subscription.mapper.SubscriptionMapper;
import com.vidya.master.subscription.repository.SubscriptionFeatureRepository;
import com.vidya.master.subscription.repository.SubscriptionPlanFeatureRepository;
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
class SubscriptionPlanServiceTest {

    @Mock
    private SubscriptionPlanRepository subscriptionPlanRepository;

    @Mock
    private SubscriptionFeatureRepository subscriptionFeatureRepository;

    @Mock
    private SubscriptionPlanFeatureRepository subscriptionPlanFeatureRepository;

    @Spy
    private SubscriptionMapper subscriptionMapper = new SubscriptionMapper();

    @InjectMocks
    private SubscriptionPlanServiceImpl subscriptionPlanService;

    private SubscriptionPlan samplePlan;
    private SubscriptionFeature sampleFeature;
    private UUID planId;
    private UUID featureId;

    @BeforeEach
    void setUp() {
        planId = UUID.randomUUID();
        featureId = UUID.randomUUID();

        samplePlan = new SubscriptionPlan(
                "STANDARD",
                1,
                "Standard Plan",
                "Description",
                BillingCycle.MONTHLY,
                new BigDecimal("4999.00"),
                "INR",
                14,
                PlanStatus.ACTIVE,
                LocalDate.now()
        );
        samplePlan.setId(planId);

        sampleFeature = new SubscriptionFeature(
                "ATTENDANCE",
                "Attendance Tracking",
                "Daily attendance",
                FeatureCategory.CORE
        );
        sampleFeature.setId(featureId);
    }

    @Test
    @DisplayName("createPlan should increment version and link features")
    void createPlan_Success() {
        PlanFeatureRequest featReq = new PlanFeatureRequest(featureId, true, 500);
        CreateSubscriptionPlanRequest request = new CreateSubscriptionPlanRequest(
                "STANDARD", "Standard Plan", "Desc", BillingCycle.MONTHLY,
                new BigDecimal("4999.00"), "INR", 14, LocalDate.now(), List.of(featReq));

        when(subscriptionPlanRepository.findMaxVersionByPlanCode("STANDARD")).thenReturn(1);
        when(subscriptionPlanRepository.save(any(SubscriptionPlan.class))).thenAnswer(inv -> {
            SubscriptionPlan p = inv.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });
        when(subscriptionFeatureRepository.findById(featureId)).thenReturn(Optional.of(sampleFeature));

        SubscriptionPlanResponse response = subscriptionPlanService.createPlan(request);

        assertThat(response).isNotNull();
        assertThat(response.planCode()).isEqualTo("STANDARD");
        assertThat(response.version()).isEqualTo(2); // incremented from 1
        verify(subscriptionPlanRepository).save(any(SubscriptionPlan.class));
        verify(subscriptionPlanFeatureRepository).save(any());
    }

    @Test
    @DisplayName("getPlanById should return plan with mapped features")
    void getPlanById_Success() {
        when(subscriptionPlanRepository.findById(planId)).thenReturn(Optional.of(samplePlan));

        SubscriptionPlanResponse response = subscriptionPlanService.getPlanById(planId);

        assertThat(response).isNotNull();
        assertThat(response.planCode()).isEqualTo("STANDARD");
        assertThat(response.basePrice()).isEqualByComparingTo("4999.00");
    }

    @Test
    @DisplayName("getPlanById with unknown ID should throw ResourceNotFoundException")
    void getPlanById_NotFound_ThrowsException() {
        UUID unknownId = UUID.randomUUID();
        when(subscriptionPlanRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> subscriptionPlanService.getPlanById(unknownId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("publishPlan should transition status to ACTIVE")
    void publishPlan_Success() {
        samplePlan.setStatus(PlanStatus.DRAFT);
        when(subscriptionPlanRepository.findById(planId)).thenReturn(Optional.of(samplePlan));
        when(subscriptionPlanRepository.save(any(SubscriptionPlan.class))).thenReturn(samplePlan);

        SubscriptionPlanResponse response = subscriptionPlanService.publishPlan(planId);

        assertThat(response).isNotNull();
        assertThat(samplePlan.getStatus()).isEqualTo(PlanStatus.ACTIVE);
    }

    @Test
    @DisplayName("deprecatePlan should transition status to DEPRECATED")
    void deprecatePlan_Success() {
        when(subscriptionPlanRepository.findById(planId)).thenReturn(Optional.of(samplePlan));
        when(subscriptionPlanRepository.save(any(SubscriptionPlan.class))).thenReturn(samplePlan);

        SubscriptionPlanResponse response = subscriptionPlanService.deprecatePlan(planId);

        assertThat(response).isNotNull();
        assertThat(samplePlan.getStatus()).isEqualTo(PlanStatus.DEPRECATED);
        assertThat(samplePlan.getEffectiveTo()).isEqualTo(LocalDate.now());
    }
}
