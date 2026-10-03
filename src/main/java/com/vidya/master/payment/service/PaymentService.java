package com.vidya.master.payment.service;

import com.vidya.common.api.PagedApiResponse;
import com.vidya.master.payment.dto.InitiatePaymentRequest;
import com.vidya.master.payment.dto.InitiatePaymentResponse;
import com.vidya.master.payment.dto.ManualPaymentConfirmRequest;
import com.vidya.master.payment.dto.PaymentRefundRequest;
import com.vidya.master.payment.dto.PaymentRefundResponse;
import com.vidya.master.payment.dto.PaymentTransactionResponse;
import com.vidya.master.payment.dto.VerifyPaymentRequest;
import com.vidya.master.payment.entity.PaymentProvider;
import com.vidya.master.payment.gateway.model.WebhookProcessingResult;
import org.springframework.data.domain.Pageable;

import java.util.Map;
import java.util.UUID;

public interface PaymentService {

    InitiatePaymentResponse initiatePayment(InitiatePaymentRequest request);

    PaymentTransactionResponse verifyPayment(VerifyPaymentRequest request);

    PaymentTransactionResponse confirmManualPayment(ManualPaymentConfirmRequest request);

    WebhookProcessingResult processWebhook(PaymentProvider provider, String payload, Map<String, String> headers);

    PaymentRefundResponse refundPayment(PaymentRefundRequest request);

    PaymentTransactionResponse getTransactionByCode(String transactionCode);

    PagedApiResponse<PaymentTransactionResponse> getTransactionsBySchool(UUID schoolId, Pageable pageable);
}
