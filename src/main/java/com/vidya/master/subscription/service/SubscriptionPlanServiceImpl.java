package com.vidya.master.subscription.service;

import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.master.subscription.dto.CreateSubscriptionPlanRequest;
import com.vidya.master.subscription.dto.PlanFeatureRequest;
import com.vidya.master.subscription.dto.SubscriptionFeatureResponse;
import com.vidya.master.subscription.dto.SubscriptionPlanResponse;
import com.vidya.master.subscription.entity.FeatureStatus;
import com.vidya.master.subscription.entity.PlanStatus;
import com.vidya.master.subscription.entity.SubscriptionFeature;
import com.vidya.master.subscription.entity.SubscriptionPlan;
import com.vidya.master.subscription.entity.SubscriptionPlanFeature;
import com.vidya.master.subscription.mapper.SubscriptionMapper;
import com.vidya.master.subscription.repository.SubscriptionFeatureRepository;
import com.vidya.master.subscription.repository.SubscriptionPlanFeatureRepository;
import com.vidya.master.subscription.repository.SubscriptionPlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Implementation of {@link SubscriptionPlanService} managing versioned plans
 * and feature catalogs.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(transactionManager = "masterTransactionManager")
public class SubscriptionPlanServiceImpl implements SubscriptionPlanService {

    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final SubscriptionFeatureRepository subscriptionFeatureRepository;
    private final SubscriptionPlanFeatureRepository subscriptionPlanFeatureRepository;
    private final SubscriptionMapper subscriptionMapper;

    @Override
    public SubscriptionPlanResponse createPlan(CreateSubscriptionPlanRequest request) {
        log.info("Creating subscription plan: [{}] with code: [{}]", request.name(), request.planCode());

        Integer maxVersion = subscriptionPlanRepository.findMaxVersionByPlanCode(request.planCode().toUpperCase().trim());
        int newVersion = (maxVersion == null) ? 1 : maxVersion + 1;

        SubscriptionPlan plan = new SubscriptionPlan(
                request.planCode().toUpperCase().trim(),
                newVersion,
                request.name().trim(),
                request.description(),
                request.billingCycle(),
                request.basePrice(),
                request.currency() != null ? request.currency() : "INR",
                request.trialDays(),
                PlanStatus.DRAFT,
                request.effectiveFrom() != null ? request.effectiveFrom() : LocalDate.now()
        );

        SubscriptionPlan savedPlan = subscriptionPlanRepository.save(plan);

        if (request.features() != null && !request.features().isEmpty()) {
            for (PlanFeatureRequest featReq : request.features()) {
                SubscriptionFeature feature = subscriptionFeatureRepository.findById(featReq.featureId())
                        .orElseThrow(() -> new ResourceNotFoundException("SubscriptionFeature", featReq.featureId()));

                SubscriptionPlanFeature planFeature = new SubscriptionPlanFeature(
                        savedPlan,
                        feature,
                        featReq.included(),
                        featReq.usageLimit()
                );
                savedPlan.addFeature(planFeature);
                subscriptionPlanFeatureRepository.save(planFeature);
            }
        }

        log.info("Subscription plan created with ID: [{}], code: [{}] v{}",
                savedPlan.getId(), savedPlan.getPlanCode(), savedPlan.getVersion());
        return subscriptionMapper.toPlanResponse(savedPlan);
    }

    @Override
    @Transactional(transactionManager = "masterTransactionManager", readOnly = true)
    public SubscriptionPlanResponse getPlanById(UUID id) {
        SubscriptionPlan plan = subscriptionPlanRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SubscriptionPlan", id));
        return subscriptionMapper.toPlanResponse(plan);
    }

    @Override
    @Transactional(transactionManager = "masterTransactionManager", readOnly = true)
    public SubscriptionPlanResponse getActivePlanByCode(String planCode) {
        List<SubscriptionPlan> activePlans = subscriptionPlanRepository.findActiveByPlanCode(planCode.toUpperCase().trim());
        if (activePlans.isEmpty()) {
            throw new ResourceNotFoundException("Active SubscriptionPlan", planCode);
        }
        return subscriptionMapper.toPlanResponse(activePlans.get(0));
    }

    @Override
    @Transactional(transactionManager = "masterTransactionManager", readOnly = true)
    public List<SubscriptionPlanResponse> getActivePublicPlans() {
        List<SubscriptionPlan> plans = subscriptionPlanRepository.findActivePublicPlans();
        return plans.stream().map(subscriptionMapper::toPlanResponse).toList();
    }

    @Override
    @Transactional(transactionManager = "masterTransactionManager", readOnly = true)
    public Page<SubscriptionPlanResponse> getAllPlans(Pageable pageable, PlanStatus status) {
        Page<SubscriptionPlan> page = (status != null)
                ? subscriptionPlanRepository.findByStatus(status, pageable)
                : subscriptionPlanRepository.findAll(pageable);
        return page.map(subscriptionMapper::toPlanResponse);
    }

    @Override
    public SubscriptionPlanResponse publishPlan(UUID id) {
        log.info("Publishing subscription plan: [{}]", id);
        SubscriptionPlan plan = subscriptionPlanRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SubscriptionPlan", id));
        plan.setStatus(PlanStatus.ACTIVE);
        SubscriptionPlan updated = subscriptionPlanRepository.save(plan);
        return subscriptionMapper.toPlanResponse(updated);
    }

    @Override
    public SubscriptionPlanResponse deprecatePlan(UUID id) {
        log.info("Deprecating subscription plan: [{}]", id);
        SubscriptionPlan plan = subscriptionPlanRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SubscriptionPlan", id));
        plan.setStatus(PlanStatus.DEPRECATED);
        plan.setEffectiveTo(LocalDate.now());
        SubscriptionPlan updated = subscriptionPlanRepository.save(plan);
        return subscriptionMapper.toPlanResponse(updated);
    }

    @Override
    @Transactional(transactionManager = "masterTransactionManager", readOnly = true)
    public List<SubscriptionFeatureResponse> getAllFeatures() {
        return subscriptionFeatureRepository.findAll()
                .stream()
                .map(subscriptionMapper::toFeatureResponse)
                .toList();
    }
}
