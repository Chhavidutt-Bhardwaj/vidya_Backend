package com.vidya.master.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRefundResponse {
    private String transactionCode;
    private String refundId;
    private BigDecimal amount;
    private String status;
    private Instant refundedAt;
    private String message;
}
