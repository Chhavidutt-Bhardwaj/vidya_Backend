package com.vidya.master.payment.dto;

import com.vidya.master.payment.entity.PaymentMethod;
import com.vidya.master.payment.entity.PaymentProvider;
import com.vidya.master.payment.entity.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentTransactionResponse {
    private UUID id;
    private String transactionCode;
    private UUID schoolId;
    private UUID subscriptionId;
    private UUID onboardingId;
    private BigDecimal amount;
    private String currency;
    private PaymentStatus status;
    private PaymentProvider paymentProvider;
    private String providerTransactionId;
    private String providerOrderId;
    private String idempotencyKey;
    private PaymentMethod paymentMethod;
    private String failureReason;
    private Instant initiatedAt;
    private Instant completedAt;
    private Map<String, Object> rawCallback;
    private Instant createdAt;
    private Instant updatedAt;
}
