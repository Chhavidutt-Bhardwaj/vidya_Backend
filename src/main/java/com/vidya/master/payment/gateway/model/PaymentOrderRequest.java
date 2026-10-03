package com.vidya.master.payment.gateway.model;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.Map;

@Getter
@Builder
public class PaymentOrderRequest {
    private String transactionCode;
    private BigDecimal amount;
    private String currency;
    private String customerName;
    private String customerEmail;
    private String customerPhone;
    private Map<String, String> notes;
}
