package com.vidya.master.onboarding.orchestration.dto;

import com.vidya.master.payment.entity.PaymentMethod;
import com.vidya.master.payment.entity.PaymentProvider;
import com.vidya.master.subscription.entity.BillingCycle;
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
/**
 * Body of {@code POST /api/v1/onboarding/{id}/activate}. The body itself is optional.
 *
 * <p><strong>Deprecated fields (onboarding I0):</strong> {@code planId}, {@code billingCycle},
 * {@code discountAmount}, {@code paymentProvider}, {@code paymentMethod}, {@code idempotencyKey},
 * {@code referenceNumber} and {@code confirmedBy} are accepted for backward compatibility but
 * <em>ignored</em>. Activation no longer subscribes or takes payment; use {@code /select-plan},
 * {@code /payment/initiate} (or {@code /payment/waive}) and {@code /approve} first.
 * {@code paymentNotes} is recorded as the activation note.
 */
public class CompleteOnboardingRequest {

    @Deprecated
    private UUID planId;

    @Deprecated
    private BillingCycle billingCycle;

    @Deprecated
    private BigDecimal discountAmount;

    @Deprecated
    private PaymentProvider paymentProvider;

    @Deprecated
    private PaymentMethod paymentMethod;

    @Deprecated
    private String idempotencyKey;

    @Deprecated
    private String referenceNumber;

    /** @deprecated Ignored: the actor is always the authenticated user. */
    @Deprecated
    private String confirmedBy;

    /** Free-text activation note. */
    private String paymentNotes;
}
