package com.vidya.master.onboarding.orchestration.dto;

import com.vidya.master.payment.entity.PaymentMethod;
import com.vidya.master.payment.entity.PaymentProvider;
import com.vidya.master.subscription.entity.BillingCycle;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompleteOnboardingRequest {

    @NotNull(message = "Plan ID is required")
    private UUID planId;

    @NotNull(message = "Billing cycle is required")
    private BillingCycle billingCycle;

    private BigDecimal discountAmount;

    @NotNull(message = "Payment provider is required")
    private PaymentProvider paymentProvider;

    private PaymentMethod paymentMethod;

    private String idempotencyKey;

    // For manual payment confirmation
    private String referenceNumber;
    private String confirmedBy;
    private String paymentNotes;
}
