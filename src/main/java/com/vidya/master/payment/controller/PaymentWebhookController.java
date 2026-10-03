package com.vidya.master.payment.controller;

import com.vidya.common.api.ApiResponse;
import com.vidya.common.api.ApiResponseCode;
import com.vidya.master.payment.entity.PaymentProvider;
import com.vidya.master.payment.gateway.model.WebhookProcessingResult;
import com.vidya.master.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/webhooks/payments")
@RequiredArgsConstructor
@Tag(name = "Payment Webhooks", description = "Inbound asynchronous webhook endpoints for payment gateways")
public class PaymentWebhookController {

    private final PaymentService paymentService;

    @PostMapping("/razorpay")
    @Operation(summary = "Inbound webhook receiver for Razorpay payment events")
    public ResponseEntity<ApiResponse<WebhookProcessingResult>> handleRazorpayWebhook(
            @RequestBody String payload,
            @RequestHeader Map<String, String> headers) {
        WebhookProcessingResult result = paymentService.processWebhook(PaymentProvider.RAZORPAY, payload, headers);
        return ResponseEntity.ok(ApiResponse.success(ApiResponseCode.PAYMENT_WEBHOOK_RECEIVED, "Razorpay webhook processed", result));
    }

    @PostMapping("/cashfree")
    @Operation(summary = "Inbound webhook receiver for Cashfree payment events")
    public ResponseEntity<ApiResponse<WebhookProcessingResult>> handleCashfreeWebhook(
            @RequestBody String payload,
            @RequestHeader Map<String, String> headers) {
        WebhookProcessingResult result = paymentService.processWebhook(PaymentProvider.CASHFREE, payload, headers);
        return ResponseEntity.ok(ApiResponse.success(ApiResponseCode.PAYMENT_WEBHOOK_RECEIVED, "Cashfree webhook processed", result));
    }
}
