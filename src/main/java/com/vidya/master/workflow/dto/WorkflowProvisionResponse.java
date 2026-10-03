package com.vidya.master.workflow.dto;

import com.vidya.master.onboarding.entity.OnboardingStatus;
import com.vidya.master.onboarding.entity.OnboardingStep;
import com.vidya.master.tenant.entity.TenantStatus;
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
@Schema(description = "Response payload after triggering or completing tenant provisioning")
public class WorkflowProvisionResponse {

    @Schema(description = "Onboarding UUID")
    private UUID onboardingId;

    @Schema(description = "Onboarding Code", example = "ONB-A1B2C3D4")
    private String onboardingCode;

    @Schema(description = "Tenant UUID")
    private UUID tenantId;

    @Schema(description = "Tenant Code", example = "gw123456")
    private String tenantCode;

    @Schema(description = "Provisioned Database Name", example = "vidya_tenant_gw123456")
    private String databaseName;

    @Schema(description = "Tenant Status", example = "PROVISIONED")
    private TenantStatus tenantStatus;

    @Schema(description = "Tenant Provisioning Code", example = "PRV-A1B2C3D4")
    private String provisioningCode;

    @Schema(description = "Tenant Provisioning Status", example = "COMPLETED")
    private String provisioningStatus;

    @Schema(description = "Current Onboarding Status", example = "PROVISIONED")
    private OnboardingStatus onboardingStatus;

    @Schema(description = "Next Expected Step in Pipeline", example = "ADMIN_CREATION")
    private OnboardingStep nextStep;

    @Schema(description = "Timestamp when provisioning completed")
    private Instant completedAt;
}
