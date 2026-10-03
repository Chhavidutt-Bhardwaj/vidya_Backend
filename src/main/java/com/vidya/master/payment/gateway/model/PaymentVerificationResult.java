package com.vidya.master.payment.gateway.model;

import com.vidya.master.payment.entity.PaymentMethod;
import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@Getter
@Builder
public class PaymentVerificationResult {
    private boolean successful;
    private String providerTransactionId;
    private String providerOrderId;
    private PaymentMethod paymentMethod;
    private String failureReason;
    private Map<String, Object> rawResponse;
}
