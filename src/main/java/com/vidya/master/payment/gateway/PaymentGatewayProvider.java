package com.vidya.master.payment.gateway;

import com.vidya.master.payment.entity.PaymentProvider;
import com.vidya.master.payment.gateway.model.CreateOrderResult;
import com.vidya.master.payment.gateway.model.GatewayRefundRequest;
import com.vidya.master.payment.gateway.model.PaymentOrderRequest;
import com.vidya.master.payment.gateway.model.PaymentRefundResult;
import com.vidya.master.payment.gateway.model.PaymentVerificationRequest;
import com.vidya.master.payment.gateway.model.PaymentVerificationResult;
import com.vidya.master.payment.gateway.model.WebhookProcessingResult;

import java.util.Map;

/**
 * Common abstraction implemented by all payment gateway integrations.
 */
public interface PaymentGatewayProvider {

    /**
     * Identifies which provider type this gateway implementation handles.
     */
    PaymentProvider getProviderType();

    /**
     * Creates an order with the payment provider.
     */
    CreateOrderResult createOrder(PaymentOrderRequest request);

    /**
     * Verifies payment completion using provider signatures and transaction tokens.
     */
    PaymentVerificationResult verifyPayment(PaymentVerificationRequest request);

    /**
     * Initiates a refund with the provider.
     */
    PaymentRefundResult processRefund(GatewayRefundRequest request);

    /**
     * Processes inbound asynchronous webhook events from the payment gateway.
     */
    WebhookProcessingResult processWebhook(String payload, Map<String, String> headers);
}
