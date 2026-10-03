package com.vidya.master.payment.gateway.model;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class GatewayRefundRequest {
    private String transactionCode;
    private String providerTransactionId;
    private BigDecimal amount;
    private String reason;
}
