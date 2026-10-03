package com.vidya.master.subscription.service;

import com.vidya.common.exception.InvalidStateTransitionException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.common.util.CodeGenerator;
import com.vidya.master.school.repository.SchoolRepository;
import com.vidya.master.subscription.dto.SchoolSubscriptionResponse;
import com.vidya.master.subscription.dto.SubscribeSchoolRequest;
import com.vidya.master.subscription.entity.BillingCycle;
import com.vidya.master.subscription.entity.SchoolSubscription;
import com.vidya.master.subscription.entity.SubscriptionPlan;
import com.vidya.master.subscription.entity.SubscriptionStatus;
import com.vidya.master.subscription.mapper.SubscriptionMapper;
import com.vidya.master.subscription.repository.SchoolSubscriptionRepository;
import com.vidya.master.subscription.repository.SubscriptionPlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Implementation of {@link SchoolSubscriptionService} managing school subscriptions,
 * plan billing snapshots, and status transitions.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(transactionManager = "masterTransactionManager")
public class SchoolSubscriptionServiceImpl implements SchoolSubscriptionService {

    private static final BigDecimal GST_RATE = new BigDecimal("0.18"); // 18% GST

    private final SchoolRepository schoolRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final SchoolSubscriptionRepository schoolSubscriptionRepository;
    private final SubscriptionMapper subscriptionMapper;

    @Override
    public SchoolSubscriptionResponse subscribeSchool(SubscribeSchoolRequest request) {
        log.info("Subscribing school [{}] to plan [{}]", request.schoolId(), request.planId());

        if (!schoolRepository.existsById(request.schoolId())) {
            throw new ResourceNotFoundException("School", request.schoolId());
        }

        SubscriptionPlan plan = subscriptionPlanRepository.findById(request.planId())
                .orElseThrow(() -> new ResourceNotFoundException("SubscriptionPlan", request.planId()));

        BillingCycle cycle = request.billingCycle() != null ? request.billingCycle() : plan.getBillingCycle();
        LocalDate startDate = LocalDate.now();
        LocalDate endDate = calculateEndDate(startDate, cycle);

        LocalDate trialEndDate = null;
        SubscriptionStatus initialStatus = SubscriptionStatus.ACTIVE;
        if (request.isTrial() && plan.getTrialDays() > 0) {
            trialEndDate = startDate.plusDays(plan.getTrialDays());
            initialStatus = SubscriptionStatus.TRIAL;
        }

        BigDecimal baseAmount = plan.getBasePrice();
        BigDecimal discount = request.discountAmount() != null ? request.discountAmount() : BigDecimal.ZERO;
        BigDecimal taxableAmount = baseAmount.subtract(discount).max(BigDecimal.ZERO);
        BigDecimal taxAmount = taxableAmount.multiply(GST_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal finalAmount = taxableAmount.add(taxAmount).setScale(2, RoundingMode.HALF_UP);

        String subscriptionCode = generateUniqueSubscriptionCode();

        SchoolSubscription subscription = new SchoolSubscription(
                subscriptionCode,
                request.schoolId(),
                plan,
                plan.getPlanCode(),
                plan.getVersion(),
                startDate,
                endDate,
                cycle,
                baseAmount,
                discount,
                taxAmount,
                finalAmount,
                plan.getCurrency(),
                initialStatus,
                request.autoRenew(),
                trialEndDate
        );

        SchoolSubscription saved = schoolSubscriptionRepository.save(subscription);
        log.info("Subscription created with ID: [{}] and code: [{}]", saved.getId(), saved.getSubscriptionCode());
        return subscriptionMapper.toSubscriptionResponse(saved);
    }

    @Override
    @Transactional(transactionManager = "masterTransactionManager", readOnly = true)
    public SchoolSubscriptionResponse getActiveSubscriptionForSchool(UUID schoolId) {
        List<SchoolSubscription> active = schoolSubscriptionRepository.findActiveSubscriptionsBySchoolId(schoolId);
        if (active.isEmpty()) {
            throw new ResourceNotFoundException("Active Subscription for school", schoolId);
        }
        return subscriptionMapper.toSubscriptionResponse(active.get(0));
    }

    @Override
    @Transactional(transactionManager = "masterTransactionManager", readOnly = true)
    public Page<SchoolSubscriptionResponse> getSubscriptionHistory(UUID schoolId, Pageable pageable) {
        Page<SchoolSubscription> page = schoolSubscriptionRepository.findBySchoolId(schoolId, pageable);
        return page.map(subscriptionMapper::toSubscriptionResponse);
    }

    @Override
    public SchoolSubscriptionResponse cancelSubscription(UUID subscriptionId) {
        log.info("Cancelling subscription: [{}]", subscriptionId);
        SchoolSubscription subscription = schoolSubscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("SchoolSubscription", subscriptionId));

        if (subscription.getStatus() == SubscriptionStatus.CANCELLED || subscription.getStatus() == SubscriptionStatus.EXPIRED) {
            throw new InvalidStateTransitionException("SchoolSubscription", subscription.getStatus().name(), "CANCELLED");
        }

        subscription.setStatus(SubscriptionStatus.CANCELLED);
        subscription.setAutoRenew(false);
        SchoolSubscription updated = schoolSubscriptionRepository.save(subscription);
        return subscriptionMapper.toSubscriptionResponse(updated);
    }

    @Override
    public SchoolSubscriptionResponse activateSubscription(UUID subscriptionId) {
        log.info("Activating subscription: [{}]", subscriptionId);
        SchoolSubscription subscription = schoolSubscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("SchoolSubscription", subscriptionId));

        subscription.setStatus(SubscriptionStatus.ACTIVE);
        SchoolSubscription updated = schoolSubscriptionRepository.save(subscription);
        return subscriptionMapper.toSubscriptionResponse(updated);
    }

    private LocalDate calculateEndDate(LocalDate start, BillingCycle cycle) {
        return switch (cycle) {
            case MONTHLY -> start.plusMonths(1);
            case QUARTERLY -> start.plusMonths(3);
            case ANNUAL -> start.plusYears(1);
            case ONE_TIME -> null;
        };
    }

    private String generateUniqueSubscriptionCode() {
        String code;
        int attempts = 0;
        do {
            code = CodeGenerator.subscriptionCode();
            attempts++;
            if (attempts > 10) {
                throw new IllegalStateException("Failed to generate unique subscription code after 10 attempts");
            }
        } while (schoolSubscriptionRepository.findBySubscriptionCode(code).isPresent());
        return code;
    }
}
