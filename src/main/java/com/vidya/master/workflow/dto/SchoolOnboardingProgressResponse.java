package com.vidya.master.workflow.dto;

import com.vidya.master.onboarding.entity.OnboardingSource;
import com.vidya.master.onboarding.entity.OnboardingStatus;
import com.vidya.master.onboarding.entity.OnboardingStep;
import com.vidya.master.payment.entity.PaymentStatus;
import com.vidya.master.school.entity.SchoolStatus;
import com.vidya.master.subscription.entity.SubscriptionStatus;
import com.vidya.master.tenant.entity.TenantStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "End-to-end progress and state consistency report across all 6 linked domain entities")
public class SchoolOnboardingProgressResponse {

    @Schema(description = "Onboarding ID")
    private UUID onboardingId;

    @Schema(description = "Onboarding Code", example = "ONB-A1B2C3D4")
    private String onboardingCode;

    @Schema(description = "Acquisition Source", example = "DIRECT")
    private OnboardingSource source;

    @Schema(description = "Current Onboarding Status", example = "ACTIVE")
    private OnboardingStatus onboardingStatus;

    @Schema(description = "Current Step in Pipeline", example = "ACTIVATION")
    private OnboardingStep currentStep;

    @Schema(description = "Completed Step Names")
    private List<String> completedSteps;

    // School Details
    @Schema(description = "School ID")
    private UUID schoolId;

    @Schema(description = "School Code", example = "SCH-A1B2C3D4")
    private String schoolCode;

    @Schema(description = "School Name", example = "Greenwood High")
    private String schoolName;

    @Schema(description = "School Status", example = "ACTIVE")
    private SchoolStatus schoolStatus;

    // Subscription Details
    @Schema(description = "Subscription ID")
    private UUID subscriptionId;

    @Schema(description = "Subscription Code", example = "SUB-A1B2C3D4")
    private String subscriptionCode;

    @Schema(description = "Plan Code Snapshot", example = "PREMIUM")
    private String planCode;

    @Schema(description = "Subscription Status", example = "ACTIVE")
    private SubscriptionStatus subscriptionStatus;

    @Schema(description = "Final Amount", example = "53098.82")
    private BigDecimal subscriptionFinalAmount;

    // Payment Details
    @Schema(description = "Transaction Code", example = "TXN-A1B2C3D4")
    private String transactionCode;

    @Schema(description = "Payment Status", example = "SUCCESS")
    private PaymentStatus paymentStatus;

    @Schema(description = "Payment Provider", example = "RAZORPAY")
    private String paymentProvider;

    // Tenant Details
    @Schema(description = "Tenant ID")
    private UUID tenantId;

    @Schema(description = "Tenant Code", example = "gw123456")
    private String tenantCode;

    @Schema(description = "Database Name", example = "vidya_tenant_gw123456")
    private String databaseName;

    @Schema(description = "Tenant Status", example = "ACTIVE")
    private TenantStatus tenantStatus;

    @Schema(description = "Provisioning Status", example = "COMPLETED")
    private String provisioningStatus;

    @Schema(description = "Provisioning Last Completed Step", example = "INITIALIZATION_RUNNING")
    private String provisioningLastCompletedStep;

    // Admin Details
    @Schema(description = "Admin User Code", example = "USR-A1B2C3D4")
    private String adminUserCode;

    @Schema(description = "Admin Email", example = "principal@greenwood.edu.in")
    private String adminEmail;

    // Timestamps
    @Schema(description = "Started At")
    private Instant startedAt;

    @Schema(description = "Submitted At")
    private Instant submittedAt;

    @Schema(description = "Completed At")
    private Instant completedAt;
}
