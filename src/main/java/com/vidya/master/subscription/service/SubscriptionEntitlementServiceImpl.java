package com.vidya.master.subscription.service;

import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.master.school.entity.School;
import com.vidya.master.school.repository.SchoolRepository;
import com.vidya.master.subscription.dto.CalculatePlanQuoteRequest;
import com.vidya.master.subscription.dto.CalculatePlanQuoteResponse;
import com.vidya.master.subscription.dto.ModuleFeatureGroupResponse;
import com.vidya.master.subscription.dto.ModuleFeatureItemResponse;
import com.vidya.master.subscription.dto.QuoteItemBreakdown;
import com.vidya.master.subscription.dto.SchoolEntitlementResponse;
import com.vidya.master.subscription.dto.SchoolFeatureOverrideRequest;
import com.vidya.master.subscription.dto.SchoolFeatureOverrideResponse;
import com.vidya.master.subscription.entity.BillingCycle;
import com.vidya.master.subscription.entity.FeatureStatus;
import com.vidya.master.subscription.entity.PlanTier;
import com.vidya.master.subscription.entity.SchoolSubscription;
import com.vidya.master.subscription.entity.SchoolSubscriptionFeatureOverride;
import com.vidya.master.subscription.entity.SubscriptionFeature;
import com.vidya.master.subscription.entity.SubscriptionPlan;
import com.vidya.master.subscription.entity.SubscriptionPlanFeature;
import com.vidya.master.subscription.repository.SchoolSubscriptionFeatureOverrideRepository;
import com.vidya.master.subscription.repository.SchoolSubscriptionRepository;
import com.vidya.master.subscription.repository.SubscriptionFeatureRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(transactionManager = "masterTransactionManager")
public class SubscriptionEntitlementServiceImpl implements SubscriptionEntitlementService {

    private static final BigDecimal GST_RATE = new BigDecimal("0.18");

    private final SchoolRepository schoolRepository;
    private final SchoolSubscriptionRepository schoolSubscriptionRepository;
    private final SubscriptionFeatureRepository subscriptionFeatureRepository;
    private final SchoolSubscriptionFeatureOverrideRepository overrideRepository;

    @Override
    @Transactional(transactionManager = "masterTransactionManager", readOnly = true)
    public SchoolEntitlementResponse getSchoolEntitlements(UUID schoolId) {
        School school = schoolRepository.findById(schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("School", schoolId));

        List<SchoolSubscription> activeSubs = schoolSubscriptionRepository.findActiveSubscriptionsBySchoolId(schoolId);
        SchoolSubscription activeSub = activeSubs.isEmpty() ? null : activeSubs.get(0);
        SubscriptionPlan plan = activeSub != null ? activeSub.getPlan() : null;

        Map<String, SubscriptionPlanFeature> planFeatureMap = new HashMap<>();
        if (plan != null && plan.getFeatures() != null) {
            for (SubscriptionPlanFeature pf : plan.getFeatures()) {
                if (pf.getFeature() != null) {
                    planFeatureMap.put(pf.getFeature().getFeatureCode(), pf);
                }
            }
        }

        List<SchoolSubscriptionFeatureOverride> overrides = overrideRepository.findBySchoolId(schoolId);
        Map<String, SchoolSubscriptionFeatureOverride> overrideMap = overrides.stream()
                .collect(Collectors.toMap(SchoolSubscriptionFeatureOverride::getFeatureCode, o -> o, (a, b) -> a));

        List<SubscriptionFeature> allFeatures = subscriptionFeatureRepository.findAllByOrderByDisplayOrderAsc();

        // Group features by parent module
        Map<String, List<ModuleFeatureItemResponse>> moduleMap = new LinkedHashMap<>();
        for (SubscriptionFeature feat : allFeatures) {
            String parentModule = feat.getParentModuleCode() != null ? feat.getParentModuleCode() : feat.getCategory().name();

            boolean isEnabled = false;
            Integer limit = null;

            if (overrideMap.containsKey(feat.getFeatureCode())) {
                SchoolSubscriptionFeatureOverride ovr = overrideMap.get(feat.getFeatureCode());
                isEnabled = ovr.isEnabled();
                limit = ovr.getCustomUsageLimit();
            } else if (planFeatureMap.containsKey(feat.getFeatureCode())) {
                SubscriptionPlanFeature pf = planFeatureMap.get(feat.getFeatureCode());
                isEnabled = pf.isIncluded();
                limit = pf.getUsageLimit();
            }

            ModuleFeatureItemResponse item = new ModuleFeatureItemResponse(
                    feat.getId(),
                    feat.getFeatureCode(),
                    feat.getName(),
                    feat.getDescription(),
                    feat.getCategory(),
                    feat.getSubCategory(),
                    feat.getBasePrice(),
                    feat.isAddon(),
                    feat.getDisplayOrder(),
                    isEnabled,
                    limit
            );

            moduleMap.computeIfAbsent(parentModule, k -> new ArrayList<>()).add(item);
        }

        List<ModuleFeatureGroupResponse> moduleGroups = new ArrayList<>();
        for (Map.Entry<String, List<ModuleFeatureItemResponse>> entry : moduleMap.entrySet()) {
            String moduleCode = entry.getKey();
            List<ModuleFeatureItemResponse> items = entry.getValue();
            boolean isAnyEnabled = items.stream().anyMatch(ModuleFeatureItemResponse::isEnabled);
            BigDecimal modulePrice = items.stream()
                    .map(ModuleFeatureItemResponse::basePrice)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            moduleGroups.add(new ModuleFeatureGroupResponse(
                    moduleCode,
                    formatModuleName(moduleCode),
                    "Module bundle for " + formatModuleName(moduleCode),
                    modulePrice,
                    isAnyEnabled,
                    items
            ));
        }

        List<SchoolFeatureOverrideResponse> overrideResponses = overrides.stream()
                .map(this::toOverrideResponse)
                .toList();

        return new SchoolEntitlementResponse(
                schoolId,
                activeSub != null ? activeSub.getId() : null,
                plan != null ? plan.getPlanCode() : "NO_ACTIVE_PLAN",
                plan != null ? plan.getName() : "No Active Subscription",
                plan != null ? plan.getTier() : PlanTier.CUSTOM,
                plan != null && plan.isCustom(),
                moduleGroups,
                overrideResponses
        );
    }

    @Override
    @Transactional(transactionManager = "masterTransactionManager", readOnly = true)
    public boolean isFeatureEnabled(UUID schoolId, String featureCode) {
        Optional<SchoolSubscriptionFeatureOverride> override =
                overrideRepository.findBySchoolIdAndFeatureCode(schoolId, featureCode.trim().toUpperCase());
        if (override.isPresent()) {
            return override.get().isEnabled();
        }

        List<SchoolSubscription> activeSubs = schoolSubscriptionRepository.findActiveSubscriptionsBySchoolId(schoolId);
        if (activeSubs.isEmpty()) {
            return false;
        }

        SubscriptionPlan plan = activeSubs.get(0).getPlan();
        if (plan == null || plan.getFeatures() == null) {
            return false;
        }

        return plan.getFeatures().stream()
                .filter(pf -> pf.getFeature() != null && pf.getFeature().getFeatureCode().equalsIgnoreCase(featureCode))
                .findFirst()
                .map(SubscriptionPlanFeature::isIncluded)
                .orElse(false);
    }

    @Override
    @Transactional(transactionManager = "masterTransactionManager", readOnly = true)
    public boolean isModuleEnabled(UUID schoolId, String moduleCode) {
        List<SubscriptionFeature> moduleFeatures = subscriptionFeatureRepository.findByParentModuleCode(moduleCode.trim().toUpperCase());
        if (moduleFeatures.isEmpty()) {
            // Check as direct feature
            return isFeatureEnabled(schoolId, moduleCode);
        }
        return moduleFeatures.stream().anyMatch(f -> isFeatureEnabled(schoolId, f.getFeatureCode()));
    }

    @Override
    public SchoolFeatureOverrideResponse overrideSchoolFeature(UUID schoolId, SchoolFeatureOverrideRequest request, UUID platformUserId) {
        School school = schoolRepository.findById(schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("School", schoolId));

        String code = request.featureCode().trim().toUpperCase();
        SubscriptionFeature feature = subscriptionFeatureRepository.findByFeatureCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("SubscriptionFeature", code));

        List<SchoolSubscription> activeSubs = schoolSubscriptionRepository.findActiveSubscriptionsBySchoolId(schoolId);
        SchoolSubscription activeSub = activeSubs.isEmpty() ? null : activeSubs.get(0);

        SchoolSubscriptionFeatureOverride override = overrideRepository.findBySchoolIdAndFeatureCode(schoolId, code)
                .orElseGet(() -> new SchoolSubscriptionFeatureOverride(
                        school, activeSub, code, request.isEnabled(),
                        request.customUsageLimit(), request.addonPrice(), request.notes(), platformUserId));

        override.setEnabled(request.isEnabled());
        override.setCustomUsageLimit(request.customUsageLimit());
        if (request.addonPrice() != null) {
            override.setAddonPrice(request.addonPrice());
        }
        override.setNotes(request.notes());
        override.setOverriddenBy(platformUserId);

        SchoolSubscriptionFeatureOverride saved = overrideRepository.save(override);
        log.info("Overrode feature [{}] for school [{}] to enabled=[{}]", code, schoolId, saved.isEnabled());
        return toOverrideResponse(saved);
    }

    @Override
    public void removeSchoolFeatureOverride(UUID schoolId, String featureCode) {
        overrideRepository.deleteBySchoolIdAndFeatureCode(schoolId, featureCode.trim().toUpperCase());
        log.info("Reset feature override [{}] for school [{}] to plan defaults", featureCode, schoolId);
    }

    @Override
    @Transactional(transactionManager = "masterTransactionManager", readOnly = true)
    public CalculatePlanQuoteResponse calculatePlanQuote(CalculatePlanQuoteRequest request) {
        PlanTier tier = request.tier() != null ? request.tier() : PlanTier.CUSTOM;
        BillingCycle cycle = request.billingCycle();

        BigDecimal tierBasePriceMonthly = switch (tier) {
            case FREE -> BigDecimal.ZERO;
            case STARTER, TIER_1 -> new BigDecimal("1999.00");
            case PRO, TIER_2 -> new BigDecimal("4999.00");
            case MAX, TIER_3 -> new BigDecimal("9999.00");
            case CUSTOM -> BigDecimal.ZERO;
        };

        // Billing cycle multiplier: Annual offers 2 months discount (x10 instead of x12)
        BigDecimal cycleMultiplier = switch (cycle) {
            case MONTHLY -> BigDecimal.ONE;
            case QUARTERLY -> new BigDecimal("3.00");
            case ANNUAL -> new BigDecimal("10.00");
            case ONE_TIME -> BigDecimal.ONE;
        };

        BigDecimal basePrice = tierBasePriceMonthly.multiply(cycleMultiplier).setScale(2, RoundingMode.HALF_UP);
        List<QuoteItemBreakdown> items = new ArrayList<>();

        if (basePrice.compareTo(BigDecimal.ZERO) > 0) {
            items.add(new QuoteItemBreakdown(
                    tier.name(),
                    tier.name() + " Package Base Fee",
                    "BASE_PLAN",
                    basePrice,
                    "Base price for " + cycle.name() + " cycle"
            ));
        }

        // Add-ons / Selected Modular Features
        BigDecimal addonsTotal = BigDecimal.ZERO;
        if (request.selectedFeatureCodes() != null && !request.selectedFeatureCodes().isEmpty()) {
            for (String fCode : request.selectedFeatureCodes()) {
                Optional<SubscriptionFeature> featOpt = subscriptionFeatureRepository.findByFeatureCode(fCode.trim().toUpperCase());
                if (featOpt.isPresent()) {
                    SubscriptionFeature f = featOpt.get();
                    BigDecimal featureCyclePrice = f.getBasePrice().multiply(cycleMultiplier).setScale(2, RoundingMode.HALF_UP);
                    addonsTotal = addonsTotal.add(featureCyclePrice);
                    items.add(new QuoteItemBreakdown(
                            f.getFeatureCode(),
                            f.getName(),
                            "ADDON_FEATURE",
                            featureCyclePrice,
                            f.getParentModuleCode() + " / " + f.getSubCategory()
                    ));
                }
            }
        }

        // Volume scaling (e.g. if students > 500, nominal scaling per 100 students)
        if (request.studentCount() != null && request.studentCount() > 500) {
            int extraBlocks = (request.studentCount() - 500) / 100;
            BigDecimal volumeCharge = new BigDecimal(extraBlocks * 150).multiply(cycleMultiplier);
            if (volumeCharge.compareTo(BigDecimal.ZERO) > 0) {
                addonsTotal = addonsTotal.add(volumeCharge);
                items.add(new QuoteItemBreakdown(
                        "STUDENT_CAPACITY_BLOCKS",
                        "Additional Student Capacity (" + (extraBlocks * 100) + " seats)",
                        "CAPACITY_ADDON",
                        volumeCharge,
                        "₹150/100 students per month"
                ));
            }
        }

        BigDecimal grossAmount = basePrice.add(addonsTotal);

        // Discount
        BigDecimal discRate = request.discountPercentage() != null ? request.discountPercentage() : BigDecimal.ZERO;
        BigDecimal discountAmount = grossAmount.multiply(discRate.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP))
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal taxableAmount = grossAmount.subtract(discountAmount).max(BigDecimal.ZERO);
        BigDecimal taxAmount = taxableAmount.multiply(GST_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal netPayable = taxableAmount.add(taxAmount).setScale(2, RoundingMode.HALF_UP);

        return new CalculatePlanQuoteResponse(
                tier,
                cycle,
                basePrice,
                addonsTotal,
                grossAmount,
                discountAmount,
                taxableAmount,
                GST_RATE.multiply(new BigDecimal("100")),
                taxAmount,
                netPayable,
                items
        );
    }

    @Override
    @Transactional(transactionManager = "masterTransactionManager", readOnly = true)
    public List<ModuleFeatureGroupResponse> getModularFeatureCatalog() {
        List<SubscriptionFeature> allFeatures = subscriptionFeatureRepository.findAllByOrderByDisplayOrderAsc();

        Map<String, List<ModuleFeatureItemResponse>> moduleMap = new LinkedHashMap<>();
        for (SubscriptionFeature feat : allFeatures) {
            String parentModule = feat.getParentModuleCode() != null ? feat.getParentModuleCode() : feat.getCategory().name();

            ModuleFeatureItemResponse item = new ModuleFeatureItemResponse(
                    feat.getId(),
                    feat.getFeatureCode(),
                    feat.getName(),
                    feat.getDescription(),
                    feat.getCategory(),
                    feat.getSubCategory(),
                    feat.getBasePrice(),
                    feat.isAddon(),
                    feat.getDisplayOrder(),
                    feat.getStatus() == FeatureStatus.ACTIVE,
                    null
            );

            moduleMap.computeIfAbsent(parentModule, k -> new ArrayList<>()).add(item);
        }

        List<ModuleFeatureGroupResponse> groups = new ArrayList<>();
        for (Map.Entry<String, List<ModuleFeatureItemResponse>> entry : moduleMap.entrySet()) {
            String moduleCode = entry.getKey();
            List<ModuleFeatureItemResponse> items = entry.getValue();
            BigDecimal modulePrice = items.stream()
                    .map(ModuleFeatureItemResponse::basePrice)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            groups.add(new ModuleFeatureGroupResponse(
                    moduleCode,
                    formatModuleName(moduleCode),
                    "Complete modular suite for " + formatModuleName(moduleCode),
                    modulePrice,
                    true,
                    items
            ));
        }
        return groups;
    }

    private SchoolFeatureOverrideResponse toOverrideResponse(SchoolSubscriptionFeatureOverride ovr) {
        Optional<SubscriptionFeature> featOpt = subscriptionFeatureRepository.findByFeatureCode(ovr.getFeatureCode());
        String name = featOpt.map(SubscriptionFeature::getName).orElse(ovr.getFeatureCode());
        String module = featOpt.map(SubscriptionFeature::getParentModuleCode).orElse(null);
        String subCat = featOpt.map(SubscriptionFeature::getSubCategory).orElse(null);

        return new SchoolFeatureOverrideResponse(
                ovr.getId(),
                ovr.getSchool().getId(),
                ovr.getFeatureCode(),
                name,
                module,
                subCat,
                ovr.isEnabled(),
                ovr.getCustomUsageLimit(),
                ovr.getAddonPrice(),
                ovr.getNotes(),
                ovr.getOverriddenBy(),
                ovr.getUpdatedAt()
        );
    }

    private String formatModuleName(String moduleCode) {
        String[] parts = moduleCode.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(" ");
            }
        }
        return sb.toString().trim();
    }
}
