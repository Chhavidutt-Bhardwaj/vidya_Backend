package com.vidya.master.onboarding.controller;

import com.vidya.common.api.ApiResponse;
import com.vidya.common.api.ApiResponseCode;
import com.vidya.common.api.PagedApiResponse;
import com.vidya.master.onboarding.dto.InitiateOnboardingRequest;
import com.vidya.master.onboarding.dto.OnboardingDetailResponse;
import com.vidya.master.onboarding.dto.OnboardingResponse;
import com.vidya.master.onboarding.dto.RejectOnboardingRequest;
import com.vidya.master.onboarding.dto.SaveStepDataRequest;
import com.vidya.master.onboarding.dto.VerifyOnboardingRequest;
import com.vidya.master.onboarding.entity.OnboardingSource;
import com.vidya.master.onboarding.entity.OnboardingStatus;
import com.vidya.master.onboarding.service.OnboardingService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.prepost.PostAuthorize;

import java.util.UUID;

/**
 * REST Controller for the school onboarding wizard workflow.
 */
@RestController
@RequestMapping("/api/v1/onboarding")
@RequiredArgsConstructor
public class OnboardingController {

    private final OnboardingService onboardingService;

    @PreAuthorize("hasAuthority('ONBOARDING_CREATE') and @tenantAuthorizationService.canInitiateOnboarding(authentication, #request.communityId(), #request.communityMemberId())")
    @PostMapping
    public ResponseEntity<ApiResponse<OnboardingResponse>> initiateOnboarding(
            @Valid @RequestBody InitiateOnboardingRequest request) {
        OnboardingResponse response = onboardingService.initiateOnboarding(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        ApiResponseCode.ONBOARDING_CREATED,
                        "School onboarding initiated successfully",
                        response
                ));
    }

    @PreAuthorize("hasAuthority('ONBOARDING_READ') and @tenantAuthorizationService.canAccessOnboarding(authentication, #id)")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OnboardingResponse>> getOnboardingById(@PathVariable UUID id) {
        OnboardingResponse response = onboardingService.getOnboardingById(id);
        return ResponseEntity.ok(ApiResponse.success(
                ApiResponseCode.ONBOARDING_FOUND,
                "Onboarding process retrieved successfully",
                response
        ));
    }

    @PreAuthorize("hasAuthority('ONBOARDING_READ')")
    @PostAuthorize("@tenantAuthorizationService.canAccessOnboarding(authentication, returnObject.body.data.id())")
    @GetMapping("/code/{code}")
    public ResponseEntity<ApiResponse<OnboardingResponse>> getOnboardingByCode(@PathVariable String code) {
        OnboardingResponse response = onboardingService.getOnboardingByCode(code);
        return ResponseEntity.ok(ApiResponse.success(
                ApiResponseCode.ONBOARDING_FOUND,
                "Onboarding process retrieved successfully",
                response
        ));
    }

    @PreAuthorize("hasAuthority('ONBOARDING_READ') and @tenantAuthorizationService.canAccessOnboarding(authentication, #id)")
    @GetMapping("/{id}/detail")
    public ResponseEntity<ApiResponse<OnboardingDetailResponse>> getOnboardingDetail(@PathVariable UUID id) {
        OnboardingDetailResponse response = onboardingService.getOnboardingDetail(id);
        return ResponseEntity.ok(ApiResponse.success(
                ApiResponseCode.ONBOARDING_FOUND,
                "Onboarding details retrieved successfully",
                response
        ));
    }

    @PreAuthorize("hasAuthority('ONBOARDING_UPDATE') and @tenantAuthorizationService.canAccessOnboarding(authentication, #id)")
    @PutMapping("/{id}/steps")
    public ResponseEntity<ApiResponse<OnboardingResponse>> saveStepData(
            @PathVariable UUID id,
            @Valid @RequestBody SaveStepDataRequest request) {
        OnboardingResponse response = onboardingService.saveStepData(id, request);
        return ResponseEntity.ok(ApiResponse.success(
                ApiResponseCode.ONBOARDING_STEP_SAVED,
                "Onboarding step data saved successfully",
                response
        ));
    }

    @PreAuthorize("hasAuthority('ONBOARDING_UPDATE') and @tenantAuthorizationService.canAccessOnboarding(authentication, #id)")
    @PostMapping("/{id}/submit")
    public ResponseEntity<ApiResponse<OnboardingResponse>> submitOnboarding(@PathVariable UUID id) {
        OnboardingResponse response = onboardingService.submitOnboarding(id);
        return ResponseEntity.ok(ApiResponse.success(
                ApiResponseCode.ONBOARDING_SUBMITTED,
                "Onboarding application submitted for verification",
                response
        ));
    }

    @PreAuthorize("hasAuthority('ONBOARDING_VERIFY') and @tenantAuthorizationService.canAccessOnboarding(authentication, #id)")
    @PostMapping("/{id}/verify")
    public ResponseEntity<ApiResponse<OnboardingResponse>> verifyOnboarding(
            @PathVariable UUID id,
            @Valid @RequestBody VerifyOnboardingRequest request) {
        OnboardingResponse response = onboardingService.verifyOnboarding(id, request);
        return ResponseEntity.ok(ApiResponse.success(
                ApiResponseCode.ONBOARDING_VERIFIED,
                "Onboarding verification status updated",
                response
        ));
    }

    @PreAuthorize("hasAuthority('ONBOARDING_APPROVE') and @tenantAuthorizationService.canAccessOnboarding(authentication, #id)")
    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<OnboardingResponse>> approveOnboarding(
            @PathVariable UUID id,
            @RequestParam(required = false) String notes) {
        OnboardingResponse response = onboardingService.approveOnboarding(id, notes);
        return ResponseEntity.ok(ApiResponse.success(
                ApiResponseCode.ONBOARDING_APPROVED,
                "Onboarding application approved",
                response
        ));
    }

    @PreAuthorize("hasAuthority('ONBOARDING_APPROVE') and @tenantAuthorizationService.canAccessOnboarding(authentication, #id)")
    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<OnboardingResponse>> rejectOnboarding(
            @PathVariable UUID id,
            @Valid @RequestBody RejectOnboardingRequest request) {
        OnboardingResponse response = onboardingService.rejectOnboarding(id, request);
        return ResponseEntity.ok(ApiResponse.success(
                ApiResponseCode.ONBOARDING_REJECTED,
                "Onboarding application rejected",
                response
        ));
    }

    @PreAuthorize("hasAuthority('ONBOARDING_READ') and ((hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')) or (#communityId != null and @tenantAuthorizationService.isCommunityMember(authentication, #communityId)))")
    @GetMapping
    public ResponseEntity<PagedApiResponse<OnboardingResponse>> getAllOnboardings(
            @RequestParam(required = false) OnboardingStatus status,
            @RequestParam(required = false) OnboardingSource source,
            @RequestParam(required = false) UUID communityId,
            @PageableDefault(size = 20) Pageable pageable) {

        Page<OnboardingResponse> page = onboardingService.getAllOnboardings(pageable, status, source, communityId);
        return ResponseEntity.ok(PagedApiResponse.of(
                ApiResponseCode.ONBOARDING_FOUND,
                "Onboarding applications retrieved successfully",
                page.getContent(),
                page
        ));
    }
}
