package com.vidya.master.payment.gateway.model;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.Map;

@Getter
@Builder
public class PaymentRefundResult {
    private boolean successful;
    private String refundId;
    private BigDecimal amount;
    private String failureReason;
    private Map<String, Object> rawResponse;
}
