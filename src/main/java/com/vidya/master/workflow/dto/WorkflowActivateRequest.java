package com.vidya.master.workflow.dto;

import com.vidya.master.workflow.admin.dto.InitialSchoolAdminRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request payload for activating school and finalizing onboarding")
public class WorkflowActivateRequest {

    @Schema(description = "Optional custom admin details (if omitted, extracted from onboarding data)")
    private InitialSchoolAdminRequest adminRequest;

    @Schema(description = "Additional activation notes or remarks", example = "School onboarding verified, provisioned and activated")
    private String notes;
}
