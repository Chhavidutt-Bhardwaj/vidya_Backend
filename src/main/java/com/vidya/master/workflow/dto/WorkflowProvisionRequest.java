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
@Schema(description = "Request payload for triggering tenant database provisioning")
public class WorkflowProvisionRequest {

    @Schema(description = "Database Host override (optional, defaults to localhost)", example = "localhost")
    private String databaseHost;

    @Schema(description = "Database Port override (optional, defaults to 5432)", example = "5432")
    private Integer databasePort;
}
