package com.vidya.master.workflow.dto;

import com.vidya.master.onboarding.entity.OnboardingStatus;
import com.vidya.master.onboarding.entity.OnboardingStep;
import com.vidya.master.subscription.entity.BillingCycle;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response payload after selecting a subscription plan")
public class PlanSelectionResponse {

    @Schema(description = "Onboarding UUID")
    private UUID onboardingId;

    @Schema(description = "Onboarding Code", example = "ONB-A1B2C3D4")
    private String onboardingCode;

    @Schema(description = "School UUID")
    private UUID schoolId;

    @Schema(description = "School Code", example = "SCH-A1B2C3D4")
    private String schoolCode;

    @Schema(description = "Subscription UUID")
    private UUID subscriptionId;

    @Schema(description = "Subscription Code", example = "SUB-A1B2C3D4")
    private String subscriptionCode;

    @Schema(description = "Plan Code", example = "PREMIUM")
    private String planCode;

    @Schema(description = "Plan Version Snapshot", example = "1")
    private Integer planVersion;

    @Schema(description = "Billing Cycle", example = "ANNUAL")
    private BillingCycle billingCycle;

    @Schema(description = "Base amount before discounts", example = "49999.00")
    private BigDecimal baseAmount;

    @Schema(description = "Applied discount amount", example = "5000.00")
    private BigDecimal discountAmount;

    @Schema(description = "Applied tax amount (GST 18%)", example = "8099.82")
    private BigDecimal taxAmount;

    @Schema(description = "Final amount payable", example = "53098.82")
    private BigDecimal finalAmount;

    @Schema(description = "Currency", example = "INR")
    private String currency;

    @Schema(description = "Current Onboarding Status", example = "PLAN_SELECTED")
    private OnboardingStatus onboardingStatus;

    @Schema(description = "Next Expected Step in Pipeline", example = "PAYMENT")
    private OnboardingStep nextStep;
}
