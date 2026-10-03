package com.vidya.master.workflow.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request payload for retrying a failed onboarding workflow step")
public class WorkflowRetryRequest {

    @Schema(description = "Optional retry notes or explanation", example = "Database connectivity restored, retrying provisioning")
    private String notes;
}
