package com.vidya.master.onboarding.orchestration;

import com.vidya.common.api.ApiResponse;
import com.vidya.common.api.ApiResponseCode;
import com.vidya.master.onboarding.orchestration.dto.CompleteOnboardingRequest;
import com.vidya.master.onboarding.orchestration.dto.OnboardingActivationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/onboarding")
@RequiredArgsConstructor
@Tag(name = "Onboarding Orchestration", description = "End-to-end full pipeline activation linking onboarding, billing, payments, and automated tenant DB provisioning")
public class OnboardingOrchestrationController {

    private final OnboardingOrchestrationService orchestrationService;

    @PostMapping("/{id}/activate")
    @Operation(summary = "Complete onboarding workflow and trigger tenant provisioning & activation")
    public ResponseEntity<ApiResponse<OnboardingActivationResponse>> completeOnboardingAndActivate(
            @PathVariable UUID id,
            @Valid @RequestBody CompleteOnboardingRequest request) {
        OnboardingActivationResponse response = orchestrationService.completeOnboardingAndActivate(id, request);
        return ResponseEntity.ok(ApiResponse.success(
                ApiResponseCode.ONBOARDING_APPROVED,
                "Onboarding completed, tenant provisioned, and school activated successfully",
                response
        ));
    }
}
