package com.vidya.master.subscription.controller;

import com.vidya.common.api.ApiResponse;
import com.vidya.common.api.ApiResponseCode;
import com.vidya.common.api.PagedApiResponse;
import com.vidya.master.subscription.dto.SchoolSubscriptionResponse;
import com.vidya.master.subscription.dto.SubscribeSchoolRequest;
import com.vidya.master.subscription.service.SchoolSubscriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.vidya.master.subscription.dto.SchoolEntitlementResponse;
import com.vidya.master.subscription.dto.SchoolFeatureOverrideRequest;
import com.vidya.master.subscription.dto.SchoolFeatureOverrideResponse;
import com.vidya.master.subscription.service.SubscriptionEntitlementService;
import com.vidya.security.user.PlatformUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;

import java.util.UUID;

/**
 * REST Controller for managing school-level subscriptions.
 */
@RestController
@RequestMapping("/api/v1/schools/{schoolId}/subscriptions")
@RequiredArgsConstructor
public class SchoolSubscriptionController {

    private final SchoolSubscriptionService schoolSubscriptionService;
    private final SubscriptionEntitlementService entitlementService;

    @PostMapping
    public ResponseEntity<ApiResponse<SchoolSubscriptionResponse>> subscribeSchool(
            @PathVariable UUID schoolId,
            @Valid @RequestBody SubscribeSchoolRequest request) {

        // Enforce path schoolId consistency
        SubscribeSchoolRequest consistentRequest = new SubscribeSchoolRequest(
                schoolId,
                request.planId(),
                request.billingCycle(),
                request.discountAmount(),
                request.autoRenew(),
                request.isTrial()
        );

        SchoolSubscriptionResponse response = schoolSubscriptionService.subscribeSchool(consistentRequest);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        ApiResponseCode.SUBSCRIPTION_CREATED,
                        "School subscribed to plan successfully",
                        response
                ));
    }

    @GetMapping("/active")
    public ResponseEntity<ApiResponse<SchoolSubscriptionResponse>> getActiveSubscription(
            @PathVariable UUID schoolId) {
        SchoolSubscriptionResponse response = schoolSubscriptionService.getActiveSubscriptionForSchool(schoolId);
        return ResponseEntity.ok(ApiResponse.success(
                ApiResponseCode.SUBSCRIPTION_FOUND,
                "Active subscription retrieved successfully",
                response
        ));
    }

    @GetMapping("/entitlements")
    public ResponseEntity<ApiResponse<SchoolEntitlementResponse>> getSchoolEntitlements(
            @PathVariable UUID schoolId) {
        SchoolEntitlementResponse response = entitlementService.getSchoolEntitlements(schoolId);
        return ResponseEntity.ok(ApiResponse.success(
                ApiResponseCode.ENTITLEMENTS_FOUND,
                "School modular entitlements tree retrieved successfully",
                response
        ));
    }

    @PutMapping("/feature-overrides")
    public ResponseEntity<ApiResponse<SchoolFeatureOverrideResponse>> overrideFeature(
            @PathVariable UUID schoolId,
            @Valid @RequestBody SchoolFeatureOverrideRequest request,
            @AuthenticationPrincipal PlatformUserDetails userDetails) {
        UUID userId = userDetails != null ? userDetails.getUserId() : null;
        SchoolFeatureOverrideResponse response = entitlementService.overrideSchoolFeature(schoolId, request, userId);
        return ResponseEntity.ok(ApiResponse.success(
                ApiResponseCode.FEATURE_OVERRIDDEN,
                "Feature override applied successfully for school",
                response
        ));
    }

    @DeleteMapping("/feature-overrides/{featureCode}")
    public ResponseEntity<ApiResponse<Void>> removeFeatureOverride(
            @PathVariable UUID schoolId,
            @PathVariable String featureCode) {
        entitlementService.removeSchoolFeatureOverride(schoolId, featureCode);
        return ResponseEntity.ok(ApiResponse.success(
                ApiResponseCode.FEATURE_OVERRIDE_REMOVED,
                "Feature override removed and restored to plan defaults",
                null
        ));
    }

    @GetMapping
    public ResponseEntity<PagedApiResponse<SchoolSubscriptionResponse>> getSubscriptionHistory(
            @PathVariable UUID schoolId,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<SchoolSubscriptionResponse> page = schoolSubscriptionService.getSubscriptionHistory(schoolId, pageable);
        return ResponseEntity.ok(PagedApiResponse.of(
                ApiResponseCode.SUBSCRIPTION_FOUND,
                "Subscription history retrieved successfully",
                page.getContent(),
                page
        ));
    }

    @PostMapping("/{subscriptionId}/cancel")
    public ResponseEntity<ApiResponse<SchoolSubscriptionResponse>> cancelSubscription(
            @PathVariable UUID schoolId,
            @PathVariable UUID subscriptionId) {
        SchoolSubscriptionResponse response = schoolSubscriptionService.cancelSubscription(subscriptionId);
        return ResponseEntity.ok(ApiResponse.success(
                ApiResponseCode.SUBSCRIPTION_FOUND,
                "Subscription cancelled successfully",
                response
        ));
    }
}
