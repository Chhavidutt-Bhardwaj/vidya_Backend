package com.vidya.master.payment.gateway.manual;

import com.vidya.master.payment.entity.PaymentMethod;
import com.vidya.master.payment.entity.PaymentProvider;
import com.vidya.master.payment.gateway.PaymentGatewayProvider;
import com.vidya.master.payment.gateway.model.CreateOrderResult;
import com.vidya.master.payment.gateway.model.GatewayRefundRequest;
import com.vidya.master.payment.gateway.model.PaymentOrderRequest;
import com.vidya.master.payment.gateway.model.PaymentRefundResult;
import com.vidya.master.payment.gateway.model.PaymentVerificationRequest;
import com.vidya.master.payment.gateway.model.PaymentVerificationResult;
import com.vidya.master.payment.gateway.model.WebhookProcessingResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Payment gateway vendor implementation for offline/manual payments (Cheque, NEFT, RTGS, Cash, Bank Transfer).
 */
@Slf4j
@Component
public class ManualPaymentGateway implements PaymentGatewayProvider {

    @Override
    public PaymentProvider getProviderType() {
        return PaymentProvider.MANUAL;
    }

    @Override
    public CreateOrderResult createOrder(PaymentOrderRequest request) {
        log.info("Creating manual payment reference for transaction: {}, amount: {}", request.getTransactionCode(), request.getAmount());
        String orderId = "MANUAL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        Map<String, Object> instructions = new HashMap<>();
        instructions.put("order_id", orderId);
        instructions.put("amount", request.getAmount());
        instructions.put("currency", request.getCurrency());
        instructions.put("instructions", "Please submit bank transfer or cheque reference to platform administrator.");

        return CreateOrderResult.builder()
                .providerOrderId(orderId)
                .providerPaymentUrl(null)
                .checkoutPayload(instructions)
                .build();
    }

    @Override
    public PaymentVerificationResult verifyPayment(PaymentVerificationRequest request) {
        log.info("Verifying manual payment record for transaction: {}", request.getTransactionCode());
        String refId = request.getProviderPaymentId() != null
                ? request.getProviderPaymentId()
                : "MAN-REF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        return PaymentVerificationResult.builder()
                .successful(true)
                .providerOrderId(request.getProviderOrderId())
                .providerTransactionId(refId)
                .paymentMethod(PaymentMethod.NEFT)
                .rawResponse(request.getRawPayload() != null ? request.getRawPayload() : Map.of("manual_verified", true))
                .build();
    }

    @Override
    public PaymentRefundResult processRefund(GatewayRefundRequest request) {
        log.info("Recording manual refund for transaction: {}, amount: {}", request.getTransactionCode(), request.getAmount());
        String refundId = "MAN-REFUND-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return PaymentRefundResult.builder()
                .successful(true)
                .refundId(refundId)
                .amount(request.getAmount())
                .rawResponse(Map.of("manual_refund_id", refundId, "reason", request.getReason()))
                .build();
    }

    @Override
    public WebhookProcessingResult processWebhook(String payload, Map<String, String> headers) {
        log.info("Manual gateway does not receive inbound webhooks");
        return WebhookProcessingResult.builder()
                .handled(false)
                .failureReason("Manual gateway does not support webhook processing")
                .build();
    }
}
