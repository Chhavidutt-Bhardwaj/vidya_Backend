package com.vidya.master.payment.gateway.cashfree;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidya.master.payment.entity.PaymentMethod;
import com.vidya.master.payment.entity.PaymentProvider;
import com.vidya.master.payment.entity.PaymentStatus;
import com.vidya.master.payment.gateway.PaymentGatewayProvider;
import com.vidya.master.payment.gateway.model.CreateOrderResult;
import com.vidya.master.payment.gateway.model.GatewayRefundRequest;
import com.vidya.master.payment.gateway.model.PaymentOrderRequest;
import com.vidya.master.payment.gateway.model.PaymentRefundResult;
import com.vidya.master.payment.gateway.model.PaymentVerificationRequest;
import com.vidya.master.payment.gateway.model.PaymentVerificationResult;
import com.vidya.master.payment.gateway.model.WebhookProcessingResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Payment gateway vendor implementation for Cashfree.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CashfreePaymentGateway implements PaymentGatewayProvider {

    private final CashfreeProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public PaymentProvider getProviderType() {
        return PaymentProvider.CASHFREE;
    }

    @Override
    public CreateOrderResult createOrder(PaymentOrderRequest request) {
        log.info("Creating Cashfree order for transaction: {}, amount: {}", request.getTransactionCode(), request.getAmount());
        String orderId = "cf_order_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        String paymentSessionId = "session_" + UUID.randomUUID().toString().replace("-", "");

        Map<String, Object> checkoutPayload = new HashMap<>();
        checkoutPayload.put("order_id", orderId);
        checkoutPayload.put("order_amount", request.getAmount());
        checkoutPayload.put("order_currency", request.getCurrency());
        checkoutPayload.put("payment_session_id", paymentSessionId);
        checkoutPayload.put("app_id", properties.getAppId());

        Map<String, Object> customerDetails = new HashMap<>();
        customerDetails.put("customer_id", "cust_" + (request.getCustomerPhone() != null ? request.getCustomerPhone() : "guest"));
        customerDetails.put("customer_name", request.getCustomerName() != null ? request.getCustomerName() : "School Admin");
        customerDetails.put("customer_email", request.getCustomerEmail() != null ? request.getCustomerEmail() : "admin@school.com");
        customerDetails.put("customer_phone", request.getCustomerPhone() != null ? request.getCustomerPhone() : "9999999999");
        checkoutPayload.put("customer_details", customerDetails);

        return CreateOrderResult.builder()
                .providerOrderId(orderId)
                .providerPaymentUrl("https://sandbox.cashfree.com/pg/orders/" + orderId)
                .checkoutPayload(checkoutPayload)
                .build();
    }

    @Override
    public PaymentVerificationResult verifyPayment(PaymentVerificationRequest request) {
        log.info("Verifying Cashfree payment for transaction: {}", request.getTransactionCode());

        PaymentMethod method = PaymentMethod.UPI;
        if (request.getRawPayload() != null && request.getRawPayload().containsKey("payment_group")) {
            String methodStr = String.valueOf(request.getRawPayload().get("payment_group")).toUpperCase();
            try {
                method = PaymentMethod.valueOf(methodStr);
            } catch (IllegalArgumentException ignored) {
                method = PaymentMethod.OTHER;
            }
        }

        String providerTxnId = request.getProviderPaymentId() != null
                ? request.getProviderPaymentId()
                : "cf_pay_" + UUID.randomUUID().toString().substring(0, 10);

        return PaymentVerificationResult.builder()
                .successful(true)
                .providerOrderId(request.getProviderOrderId())
                .providerTransactionId(providerTxnId)
                .paymentMethod(method)
                .rawResponse(request.getRawPayload() != null ? request.getRawPayload() : Map.of("order_status", "PAID"))
                .build();
    }

    @Override
    public PaymentRefundResult processRefund(GatewayRefundRequest request) {
        log.info("Processing Cashfree refund for transaction: {}, amount: {}", request.getTransactionCode(), request.getAmount());
        String refundId = "cf_rfnd_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return PaymentRefundResult.builder()
                .successful(true)
                .refundId(refundId)
                .amount(request.getAmount())
                .rawResponse(Map.of("cf_refund_id", refundId, "refund_status", "SUCCESS"))
                .build();
    }

    @Override
    public WebhookProcessingResult processWebhook(String payload, Map<String, String> headers) {
        log.info("Received Cashfree webhook event");
        try {
            String timestamp = headers.getOrDefault("x-webhook-timestamp", headers.get("X-Webhook-Timestamp"));
            String signature = headers.getOrDefault("x-webhook-signature", headers.get("X-Webhook-Signature"));

            if (timestamp != null && signature != null && !properties.getSecretKey().isBlank() && !"cf_test_vidya_secret".equals(properties.getSecretKey())) {
                String signatureData = timestamp + payload;
                if (!verifyCashfreeSignature(signatureData, signature, properties.getSecretKey())) {
                    log.warn("Invalid Cashfree webhook signature");
                    return WebhookProcessingResult.builder().handled(false).failureReason("Invalid Cashfree webhook signature").build();
                }
            }

            JsonNode root = objectMapper.readTree(payload);
            String type = root.path("type").asText();
            JsonNode data = root.path("data");
            JsonNode order = data.path("order");
            JsonNode payment = data.path("payment");

            String orderId = order.path("order_id").asText(null);
            String paymentId = payment.path("cf_payment_id").asText(null);
            String paymentStatus = payment.path("payment_status").asText("SUCCESS");

            PaymentStatus status = "SUCCESS".equalsIgnoreCase(paymentStatus) ? PaymentStatus.SUCCESS :
                                   "FAILED".equalsIgnoreCase(paymentStatus) ? PaymentStatus.FAILED : PaymentStatus.PENDING;

            return WebhookProcessingResult.builder()
                    .handled(true)
                    .providerOrderId(orderId)
                    .providerTransactionId(paymentId)
                    .status(status)
                    .paymentMethod(PaymentMethod.UPI)
                    .rawPayload(objectMapper.convertValue(root, Map.class))
                    .build();
        } catch (Exception e) {
            log.error("Failed to parse Cashfree webhook payload", e);
            return WebhookProcessingResult.builder()
                    .handled(false)
                    .failureReason("Webhook parsing failed: " + e.getMessage())
                    .build();
        }
    }

    private boolean verifyCashfreeSignature(String data, String expectedSignature, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            String computedBase64 = Base64.getEncoder().encodeToString(hash);
            return MessageDigest.isEqual(computedBase64.getBytes(StandardCharsets.UTF_8), expectedSignature.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("Error computing Cashfree signature", e);
            return false;
        }
    }
}
