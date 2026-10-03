package com.vidya.master.workflow.dto;

import com.vidya.master.subscription.entity.BillingCycle;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
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
@Schema(description = "Request payload for selecting a subscription plan for an onboarding school")
public class PlanSelectionRequest {

    @NotNull(message = "Plan ID is required")
    @Schema(description = "Subscription Plan UUID", example = "e0000000-0000-0000-0000-000000000001")
    private UUID planId;

    @Schema(description = "Billing cycle (optional, defaults to plan billing cycle)", example = "ANNUAL")
    private BillingCycle billingCycle;

    @Schema(description = "Discount amount to apply", example = "500.00")
    private BigDecimal discountAmount;

    @Schema(description = "Whether the subscription should auto renew", example = "true")
    private Boolean autoRenew;

    @Schema(description = "Whether to start with a trial period if available", example = "false")
    private Boolean isTrial;
}
