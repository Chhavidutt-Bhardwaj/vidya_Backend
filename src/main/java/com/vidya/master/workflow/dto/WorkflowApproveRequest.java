package com.vidya.master.workflow.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request payload for approving verified and paid school onboarding")
public class WorkflowApproveRequest {

    @Schema(description = "Approval notes or remarks", example = "All documents and initial fee payment verified successfully")
    private String notes;

    @Schema(description = "UUID of approving platform administrator")
    private UUID approvedBy;
}
