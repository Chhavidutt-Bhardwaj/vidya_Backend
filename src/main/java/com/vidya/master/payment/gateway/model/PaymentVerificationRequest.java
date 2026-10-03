package com.vidya.master.payment.gateway.model;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@Getter
@Builder
public class PaymentVerificationRequest {
    private String transactionCode;
    private String providerPaymentId;
    private String providerOrderId;
    private String providerSignature;
    private Map<String, Object> rawPayload;
}
