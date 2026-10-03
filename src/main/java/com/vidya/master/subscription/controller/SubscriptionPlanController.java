package com.vidya.master.subscription.controller;

import com.vidya.common.api.ApiResponse;
import com.vidya.common.api.ApiResponseCode;
import com.vidya.common.api.PagedApiResponse;
import com.vidya.master.subscription.dto.CreateSubscriptionPlanRequest;
import com.vidya.master.subscription.dto.SubscriptionFeatureResponse;
import com.vidya.master.subscription.dto.SubscriptionPlanResponse;
import com.vidya.master.subscription.entity.PlanStatus;
import com.vidya.master.subscription.service.SubscriptionPlanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST Controller for subscription plan catalog and feature management.
 */
@RestController
@RequestMapping("/api/v1/subscription-plans")
@RequiredArgsConstructor
public class SubscriptionPlanController {

    private final SubscriptionPlanService subscriptionPlanService;

    @GetMapping("/catalog")
    public ResponseEntity<ApiResponse<List<SubscriptionPlanResponse>>> getPublicCatalog() {
        List<SubscriptionPlanResponse> plans = subscriptionPlanService.getActivePublicPlans();
        return ResponseEntity.ok(ApiResponse.success(
                ApiResponseCode.PLANS_FOUND,
                "Public plan catalog retrieved successfully",
                plans
        ));
    }

    @GetMapping("/features")
    public ResponseEntity<ApiResponse<List<SubscriptionFeatureResponse>>> getAllFeatures() {
        List<SubscriptionFeatureResponse> features = subscriptionPlanService.getAllFeatures();
        return ResponseEntity.ok(ApiResponse.success(
                ApiResponseCode.FEATURES_FOUND,
                "Feature catalog retrieved successfully",
                features
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SubscriptionPlanResponse>> getPlanById(@PathVariable UUID id) {
        SubscriptionPlanResponse response = subscriptionPlanService.getPlanById(id);
        return ResponseEntity.ok(ApiResponse.success(
                ApiResponseCode.PLAN_FOUND,
                "Subscription plan retrieved successfully",
                response
        ));
    }

    @GetMapping("/code/{planCode}")
    public ResponseEntity<ApiResponse<SubscriptionPlanResponse>> getActivePlanByCode(@PathVariable String planCode) {
        SubscriptionPlanResponse response = subscriptionPlanService.getActivePlanByCode(planCode);
        return ResponseEntity.ok(ApiResponse.success(
                ApiResponseCode.PLAN_FOUND,
                "Active subscription plan retrieved successfully",
                response
        ));
    }

    @GetMapping
    public ResponseEntity<PagedApiResponse<SubscriptionPlanResponse>> getAllPlans(
            @RequestParam(required = false) PlanStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<SubscriptionPlanResponse> page = subscriptionPlanService.getAllPlans(pageable, status);
        return ResponseEntity.ok(PagedApiResponse.of(
                ApiResponseCode.PLANS_FOUND,
                "Subscription plans retrieved successfully",
                page.getContent(),
                page
        ));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SubscriptionPlanResponse>> createPlan(
            @Valid @RequestBody CreateSubscriptionPlanRequest request) {
        SubscriptionPlanResponse response = subscriptionPlanService.createPlan(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        ApiResponseCode.PLAN_CREATED,
                        "Subscription plan created successfully",
                        response
                ));
    }

    @PatchMapping("/{id}/publish")
    public ResponseEntity<ApiResponse<SubscriptionPlanResponse>> publishPlan(@PathVariable UUID id) {
        SubscriptionPlanResponse response = subscriptionPlanService.publishPlan(id);
        return ResponseEntity.ok(ApiResponse.success(
                ApiResponseCode.PLAN_UPDATED,
                "Subscription plan published successfully",
                response
        ));
    }

    @PatchMapping("/{id}/deprecate")
    public ResponseEntity<ApiResponse<SubscriptionPlanResponse>> deprecatePlan(@PathVariable UUID id) {
        SubscriptionPlanResponse response = subscriptionPlanService.deprecatePlan(id);
        return ResponseEntity.ok(ApiResponse.success(
                ApiResponseCode.PLAN_UPDATED,
                "Subscription plan deprecated successfully",
                response
        ));
    }
}
