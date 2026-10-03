package com.vidya.master.workflow.dto;

import com.vidya.master.onboarding.entity.OnboardingStatus;
import com.vidya.master.payment.entity.PaymentStatus;
import com.vidya.master.school.entity.SchoolStatus;
import com.vidya.master.subscription.entity.SubscriptionStatus;
import com.vidya.master.tenant.entity.TenantStatus;
import com.vidya.master.workflow.admin.dto.InitialSchoolAdminResponse;
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
@Schema(description = "Response payload after successful end-to-end pipeline activation")
public class WorkflowActivationResponse {

    @Schema(description = "Onboarding UUID")
    private UUID onboardingId;

    @Schema(description = "Onboarding Code", example = "ONB-A1B2C3D4")
    private String onboardingCode;

    @Schema(description = "School UUID")
    private UUID schoolId;

    @Schema(description = "School Code", example = "SCH-A1B2C3D4")
    private String schoolCode;

    @Schema(description = "School Name", example = "Greenwood High")
    private String schoolName;

    @Schema(description = "School Status", example = "ACTIVE")
    private SchoolStatus schoolStatus;

    @Schema(description = "Tenant UUID")
    private UUID tenantId;

    @Schema(description = "Tenant Code", example = "gw123456")
    private String tenantCode;

    @Schema(description = "Tenant Database Name", example = "vidya_tenant_gw123456")
    private String databaseName;

    @Schema(description = "Tenant Status", example = "ACTIVE")
    private TenantStatus tenantStatus;

    @Schema(description = "Subscription UUID")
    private UUID subscriptionId;

    @Schema(description = "Subscription Code", example = "SUB-A1B2C3D4")
    private String subscriptionCode;

    @Schema(description = "Subscription Status", example = "ACTIVE")
    private SubscriptionStatus subscriptionStatus;

    @Schema(description = "Payment Transaction Code", example = "TXN-A1B2C3D4")
    private String transactionCode;

    @Schema(description = "Payment Status", example = "SUCCESS")
    private PaymentStatus paymentStatus;

    @Schema(description = "Created Initial School Administrator details")
    private InitialSchoolAdminResponse admin;

    @Schema(description = "Final Onboarding Status", example = "ACTIVE")
    private OnboardingStatus onboardingStatus;

    @Schema(description = "Activation completion timestamp")
    private Instant activatedAt;

    @Schema(description = "Success message")
    private String message;
}
