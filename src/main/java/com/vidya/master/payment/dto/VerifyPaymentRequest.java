package com.vidya.master.payment.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerifyPaymentRequest {

    @NotBlank(message = "Transaction code is required")
    private String transactionCode;

    private String providerPaymentId;

    private String providerOrderId;

    private String providerSignature;

    private Map<String, Object> rawPayload;
}
