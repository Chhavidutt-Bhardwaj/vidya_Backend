package com.vidya.master.payment.dto;

import com.vidya.master.payment.entity.PaymentProvider;
import com.vidya.master.payment.entity.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InitiatePaymentResponse {
    private String transactionCode;
    private UUID schoolId;
    private BigDecimal amount;
    private String currency;
    private PaymentStatus status;
    private PaymentProvider paymentProvider;
    private String providerOrderId;
    private String providerPaymentUrl;
    private Map<String, Object> checkoutPayload;
    private String idempotencyKey;
}
