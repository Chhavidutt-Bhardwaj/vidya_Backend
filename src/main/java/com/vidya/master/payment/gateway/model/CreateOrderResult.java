package com.vidya.master.payment.gateway.model;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@Getter
@Builder
public class CreateOrderResult {
    private String providerOrderId;
    private String providerPaymentUrl;
    private Map<String, Object> checkoutPayload;
}
