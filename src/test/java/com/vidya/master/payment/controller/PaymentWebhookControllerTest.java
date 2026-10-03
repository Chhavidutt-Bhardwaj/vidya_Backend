package com.vidya.master.payment.controller;

import com.vidya.common.exception.GlobalExceptionHandler;
import com.vidya.master.payment.entity.PaymentMethod;
import com.vidya.master.payment.entity.PaymentProvider;
import com.vidya.master.payment.entity.PaymentStatus;
import com.vidya.master.payment.gateway.model.WebhookProcessingResult;
import com.vidya.master.payment.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PaymentWebhookControllerTest {

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private PaymentWebhookController paymentWebhookController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(paymentWebhookController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/webhooks/payments/razorpay should process webhook")
    void handleRazorpayWebhook_Success() throws Exception {
        WebhookProcessingResult result = WebhookProcessingResult.builder()
                .handled(true)
                .providerOrderId("order_rzp_123")
                .providerTransactionId("pay_123")
                .status(PaymentStatus.SUCCESS)
                .paymentMethod(PaymentMethod.UPI)
                .build();

        when(paymentService.processWebhook(eq(PaymentProvider.RAZORPAY), any(), any()))
                .thenReturn(result);

        mockMvc.perform(post("/api/v1/webhooks/payments/razorpay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("x-razorpay-signature", "sig123")
                        .content("{\"event\":\"payment.captured\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", equalTo(true)))
                .andExpect(jsonPath("$.data.handled", equalTo(true)))
                .andExpect(jsonPath("$.data.status", equalTo("SUCCESS")));
    }

    @Test
    @DisplayName("POST /api/v1/webhooks/payments/cashfree should process webhook")
    void handleCashfreeWebhook_Success() throws Exception {
        WebhookProcessingResult result = WebhookProcessingResult.builder()
                .handled(true)
                .providerOrderId("cf_order_123")
                .providerTransactionId("cf_pay_123")
                .status(PaymentStatus.SUCCESS)
                .paymentMethod(PaymentMethod.UPI)
                .build();

        when(paymentService.processWebhook(eq(PaymentProvider.CASHFREE), any(), any()))
                .thenReturn(result);

        mockMvc.perform(post("/api/v1/webhooks/payments/cashfree")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("x-webhook-timestamp", "1600000000")
                        .header("x-webhook-signature", "sig123")
                        .content("{\"type\":\"PAYMENT_SUCCESS_WEBHOOK\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", equalTo(true)))
                .andExpect(jsonPath("$.data.handled", equalTo(true)));
    }
}
