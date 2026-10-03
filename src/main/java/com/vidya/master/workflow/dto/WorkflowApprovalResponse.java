package com.vidya.master.workflow.dto;

import com.vidya.master.onboarding.entity.OnboardingStatus;
import com.vidya.master.onboarding.entity.OnboardingStep;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response payload after onboarding approval")
public class WorkflowApprovalResponse {

    @Schema(description = "Onboarding UUID")
    private UUID onboardingId;

    @Schema(description = "Onboarding Code", example = "ONB-A1B2C3D4")
    private String onboardingCode;

    @Schema(description = "Onboarding Status", example = "APPROVED")
    private OnboardingStatus onboardingStatus;

    @Schema(description = "Next Expected Step in Pipeline", example = "TENANT_PROVISIONING")
    private OnboardingStep nextStep;

    @Schema(description = "Approval Notes")
    private String notes;

    @Schema(description = "Timestamp of approval")
    private Instant approvedAt;
}
