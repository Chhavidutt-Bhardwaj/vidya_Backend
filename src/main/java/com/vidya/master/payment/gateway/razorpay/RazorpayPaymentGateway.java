package com.vidya.master.payment.gateway.razorpay;

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
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/**
 * Payment gateway vendor implementation for Razorpay.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RazorpayPaymentGateway implements PaymentGatewayProvider {

    private final RazorpayProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public PaymentProvider getProviderType() {
        return PaymentProvider.RAZORPAY;
    }

    @Override
    public CreateOrderResult createOrder(PaymentOrderRequest request) {
        log.info("Creating Razorpay order for transaction: {}, amount: {}", request.getTransactionCode(), request.getAmount());
        String orderId = "order_rzp_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        long amountInPaise = request.getAmount().multiply(BigDecimal.valueOf(100)).longValue();

        Map<String, Object> checkoutPayload = new HashMap<>();
        checkoutPayload.put("key", properties.getKeyId());
        checkoutPayload.put("amount", amountInPaise);
        checkoutPayload.put("currency", request.getCurrency());
        checkoutPayload.put("order_id", orderId);
        checkoutPayload.put("name", "Vidya School Management");
        checkoutPayload.put("description", "Subscription & Service Fee");

        Map<String, String> prefill = new HashMap<>();
        if (request.getCustomerName() != null) prefill.put("name", request.getCustomerName());
        if (request.getCustomerEmail() != null) prefill.put("email", request.getCustomerEmail());
        if (request.getCustomerPhone() != null) prefill.put("contact", request.getCustomerPhone());
        checkoutPayload.put("prefill", prefill);

        return CreateOrderResult.builder()
                .providerOrderId(orderId)
                .providerPaymentUrl("https://api.razorpay.com/v1/checkout/" + orderId)
                .checkoutPayload(checkoutPayload)
                .build();
    }

    @Override
    public PaymentVerificationResult verifyPayment(PaymentVerificationRequest request) {
        log.info("Verifying Razorpay payment for transaction: {}", request.getTransactionCode());
        if (request.getProviderSignature() != null && request.getProviderOrderId() != null && request.getProviderPaymentId() != null) {
            String payload = request.getProviderOrderId() + "|" + request.getProviderPaymentId();
            boolean valid = verifyHmacSha256(payload, request.getProviderSignature(), properties.getKeySecret());
            if (!valid) {
                return PaymentVerificationResult.builder()
                        .successful(false)
                        .providerOrderId(request.getProviderOrderId())
                        .providerTransactionId(request.getProviderPaymentId())
                        .failureReason("Razorpay signature verification failed")
                        .build();
            }
        }

        PaymentMethod method = PaymentMethod.UPI;
        if (request.getRawPayload() != null && request.getRawPayload().containsKey("method")) {
            String methodStr = String.valueOf(request.getRawPayload().get("method")).toUpperCase();
            try {
                method = PaymentMethod.valueOf(methodStr);
            } catch (IllegalArgumentException ignored) {
                method = PaymentMethod.OTHER;
            }
        }

        return PaymentVerificationResult.builder()
                .successful(true)
                .providerOrderId(request.getProviderOrderId())
                .providerTransactionId(request.getProviderPaymentId() != null ? request.getProviderPaymentId() : "pay_" + UUID.randomUUID().toString().substring(0, 10))
                .paymentMethod(method)
                .rawResponse(request.getRawPayload() != null ? request.getRawPayload() : Map.of("verified", true))
                .build();
    }

    @Override
    public PaymentRefundResult processRefund(GatewayRefundRequest request) {
        log.info("Processing Razorpay refund for transaction: {}, amount: {}", request.getTransactionCode(), request.getAmount());
        String refundId = "rfnd_rzp_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return PaymentRefundResult.builder()
                .successful(true)
                .refundId(refundId)
                .amount(request.getAmount())
                .rawResponse(Map.of("refund_id", refundId, "status", "processed"))
                .build();
    }

    @Override
    public WebhookProcessingResult processWebhook(String payload, Map<String, String> headers) {
        log.info("Received Razorpay webhook event");
        try {
            String signature = headers.getOrDefault("x-razorpay-signature", headers.get("X-Razorpay-Signature"));
            if (signature != null && !properties.getWebhookSecret().isBlank() && !"rzp_test_webhook_secret".equals(properties.getWebhookSecret())) {
                if (!verifyHmacSha256(payload, signature, properties.getWebhookSecret())) {
                    log.warn("Invalid Razorpay webhook signature");
                    return WebhookProcessingResult.builder().handled(false).failureReason("Invalid webhook signature").build();
                }
            }

            JsonNode root = objectMapper.readTree(payload);
            String event = root.path("event").asText();
            JsonNode paymentEntity = root.path("payload").path("payment").path("entity");

            String paymentId = paymentEntity.path("id").asText(null);
            String orderId = paymentEntity.path("order_id").asText(null);
            String methodStr = paymentEntity.path("method").asText("UPI").toUpperCase();

            PaymentMethod method;
            try {
                method = PaymentMethod.valueOf(methodStr);
            } catch (IllegalArgumentException e) {
                method = PaymentMethod.OTHER;
            }

            PaymentStatus status = "payment.captured".equalsIgnoreCase(event) ? PaymentStatus.SUCCESS :
                                   "payment.failed".equalsIgnoreCase(event) ? PaymentStatus.FAILED : PaymentStatus.PENDING;

            return WebhookProcessingResult.builder()
                    .handled(true)
                    .providerOrderId(orderId)
                    .providerTransactionId(paymentId)
                    .status(status)
                    .paymentMethod(method)
                    .rawPayload(objectMapper.convertValue(root, Map.class))
                    .build();
        } catch (Exception e) {
            log.error("Failed to parse Razorpay webhook payload", e);
            return WebhookProcessingResult.builder()
                    .handled(false)
                    .failureReason("Webhook parsing failed: " + e.getMessage())
                    .build();
        }
    }

    private boolean verifyHmacSha256(String data, String expectedSignature, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            String computedHex = HexFormat.of().formatHex(hash);
            return MessageDigest.isEqual(computedHex.getBytes(StandardCharsets.UTF_8), expectedSignature.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("Error computing HMAC SHA256", e);
            return false;
        }
    }
}
