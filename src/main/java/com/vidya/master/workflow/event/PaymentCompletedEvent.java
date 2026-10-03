package com.vidya.master.workflow.event;

import com.vidya.master.payment.entity.PaymentMethod;
import com.vidya.master.payment.entity.PaymentProvider;
import com.vidya.master.payment.entity.PaymentStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Domain application event published when a payment transaction is completed.
 */
@Getter
@Builder
public class PaymentCompletedEvent {
    private final String transactionCode;
    private final UUID schoolId;
    private final UUID subscriptionId;
    private final UUID onboardingId;
    private final BigDecimal amount;
    private final String currency;
    private final PaymentProvider paymentProvider;
    private final PaymentMethod paymentMethod;
    private final PaymentStatus status;
    private final String providerTransactionId;
    private final Instant completedAt;
}
