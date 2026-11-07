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

    /**
     * @deprecated Ignored since onboarding I0. The approver is always the authenticated user
     * taken from the SecurityContext; client-supplied identities are never trusted for auditing.
     */
    @Deprecated
    @Schema(description = "DEPRECATED — ignored. The approver is the authenticated user.", deprecated = true)
    private UUID approvedBy;
}
