package com.vidya.master.subscription.service;

import com.vidya.master.subscription.dto.CreateSubscriptionPlanRequest;
import com.vidya.master.subscription.dto.SubscriptionFeatureResponse;
import com.vidya.master.subscription.dto.SubscriptionPlanResponse;
import com.vidya.master.subscription.entity.PlanStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

/**
 * Service for managing subscription plans and the feature catalog.
 */
public interface SubscriptionPlanService {

    SubscriptionPlanResponse createPlan(CreateSubscriptionPlanRequest request);

    SubscriptionPlanResponse getPlanById(UUID id);

    SubscriptionPlanResponse getActivePlanByCode(String planCode);

    List<SubscriptionPlanResponse> getActivePublicPlans();

    Page<SubscriptionPlanResponse> getAllPlans(Pageable pageable, PlanStatus status);

    SubscriptionPlanResponse publishPlan(UUID id);

    SubscriptionPlanResponse deprecatePlan(UUID id);

    List<SubscriptionFeatureResponse> getAllFeatures();
}
