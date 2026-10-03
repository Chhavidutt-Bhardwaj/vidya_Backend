package com.vidya.master.payment.gateway.model;

import com.vidya.master.payment.entity.PaymentMethod;
import com.vidya.master.payment.entity.PaymentStatus;
import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@Getter
@Builder
public class WebhookProcessingResult {
    private boolean handled;
    private String transactionCode;
    private String providerOrderId;
    private String providerTransactionId;
    private PaymentStatus status;
    private PaymentMethod paymentMethod;
    private String failureReason;
    private Map<String, Object> rawPayload;
}
