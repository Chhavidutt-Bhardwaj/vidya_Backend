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

    /**
     * @deprecated Since onboarding I0 the tenant database host is server configuration
     * ({@code vidya.provisioning.tenant-db.host}). Supplying a value is rejected with HTTP 400.
     */
    @Deprecated
    @jakarta.validation.constraints.Null(message = "databaseHost cannot be supplied by clients; it is server-configured")
    @Schema(description = "DEPRECATED — must be omitted. Server-configured; any value is rejected.", deprecated = true)
    private String databaseHost;

    /**
     * @deprecated Since onboarding I0 the tenant database port is server configuration
     * ({@code vidya.provisioning.tenant-db.port}). Supplying a value is rejected with HTTP 400.
     */
    @Deprecated
    @jakarta.validation.constraints.Null(message = "databasePort cannot be supplied by clients; it is server-configured")
    @Schema(description = "DEPRECATED — must be omitted. Server-configured; any value is rejected.", deprecated = true)
    private Integer databasePort;
}
