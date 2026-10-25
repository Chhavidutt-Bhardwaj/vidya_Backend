package com.vidya.master.subscription.mapper;

import com.vidya.master.subscription.dto.PlanFeatureResponse;
import com.vidya.master.subscription.dto.SchoolSubscriptionResponse;
import com.vidya.master.subscription.dto.SubscriptionFeatureResponse;
import com.vidya.master.subscription.dto.SubscriptionPlanResponse;
import com.vidya.master.subscription.entity.SchoolSubscription;
import com.vidya.master.subscription.entity.SubscriptionFeature;
import com.vidya.master.subscription.entity.SubscriptionPlan;
import com.vidya.master.subscription.entity.SubscriptionPlanFeature;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Maps between Subscription entities and DTO records.
 */
@Component
public class SubscriptionMapper {

    public SubscriptionPlanResponse toPlanResponse(SubscriptionPlan plan) {
        if (plan == null) {
            return null;
        }

        List<PlanFeatureResponse> featureResponses = plan.getFeatures() != null
                ? plan.getFeatures().stream().map(this::toPlanFeatureResponse).toList()
                : Collections.emptyList();

        return new SubscriptionPlanResponse(
                plan.getId(),
                plan.getPlanCode(),
                plan.getVersion(),
                plan.getName(),
                plan.getDescription(),
                plan.getBillingCycle(),
                plan.getBasePrice(),
                plan.getCurrency(),
                plan.getTrialDays(),
                plan.getStatus(),
                plan.getEffectiveFrom(),
                plan.getEffectiveTo(),
                plan.getTier(),
                plan.isCustom(),
                plan.getTargetSchoolId(),
                featureResponses
        );
    }

    public SubscriptionFeatureResponse toFeatureResponse(SubscriptionFeature feature) {
        if (feature == null) {
            return null;
        }
        return new SubscriptionFeatureResponse(
                feature.getId(),
                feature.getFeatureCode(),
                feature.getName(),
                feature.getDescription(),
                feature.getCategory(),
                feature.getStatus(),
                feature.getParentModuleCode(),
                feature.getSubCategory(),
                feature.getBasePrice(),
                feature.isAddon(),
                feature.getDisplayOrder()
        );
    }

    public PlanFeatureResponse toPlanFeatureResponse(SubscriptionPlanFeature planFeature) {
        if (planFeature == null) {
            return null;
        }
        SubscriptionFeature f = planFeature.getFeature();
        return new PlanFeatureResponse(
                f != null ? f.getId() : null,
                f != null ? f.getFeatureCode() : null,
                f != null ? f.getName() : null,
                f != null ? f.getCategory() : null,
                planFeature.isIncluded(),
                planFeature.getUsageLimit()
        );
    }

    public SchoolSubscriptionResponse toSubscriptionResponse(SchoolSubscription subscription) {
        if (subscription == null) {
            return null;
        }
        return new SchoolSubscriptionResponse(
                subscription.getId(),
                subscription.getSubscriptionCode(),
                subscription.getSchoolId(),
                subscription.getPlan() != null ? subscription.getPlan().getId() : null,
                subscription.getPlanCode(),
                subscription.getPlanVersion(),
                subscription.getStartDate(),
                subscription.getEndDate(),
                subscription.getBillingCycle(),
                subscription.getBaseAmount(),
                subscription.getDiscountAmount(),
                subscription.getTaxAmount(),
                subscription.getFinalAmount(),
                subscription.getCurrency(),
                subscription.getStatus(),
                subscription.isAutoRenew(),
                subscription.getTrialEndDate(),
                subscription.getCreatedAt(),
                subscription.getUpdatedAt()
        );
    }
}
