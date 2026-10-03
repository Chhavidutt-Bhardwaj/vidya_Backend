package com.vidya.master.payment.mapper;

import com.vidya.master.payment.dto.InitiatePaymentResponse;
import com.vidya.master.payment.dto.PaymentTransactionResponse;
import com.vidya.master.payment.entity.PaymentTransaction;
import com.vidya.master.payment.gateway.model.CreateOrderResult;
import org.springframework.stereotype.Component;

@Component
public class PaymentMapper {

    public PaymentTransactionResponse toResponse(PaymentTransaction tx) {
        if (tx == null) return null;
        return PaymentTransactionResponse.builder()
                .id(tx.getId())
                .transactionCode(tx.getTransactionCode())
                .schoolId(tx.getSchoolId())
                .subscriptionId(tx.getSubscriptionId())
                .onboardingId(tx.getOnboardingId())
                .amount(tx.getAmount())
                .currency(tx.getCurrency())
                .status(tx.getStatus())
                .paymentProvider(tx.getPaymentProvider())
                .providerTransactionId(tx.getProviderTransactionId())
                .providerOrderId(tx.getProviderOrderId())
                .idempotencyKey(tx.getIdempotencyKey())
                .paymentMethod(tx.getPaymentMethod())
                .failureReason(tx.getFailureReason())
                .initiatedAt(tx.getInitiatedAt())
                .completedAt(tx.getCompletedAt())
                .rawCallback(tx.getRawCallback())
                .createdAt(tx.getCreatedAt())
                .updatedAt(tx.getUpdatedAt())
                .build();
    }

    public InitiatePaymentResponse toInitiateResponse(PaymentTransaction tx, CreateOrderResult orderResult) {
        if (tx == null) return null;
        return InitiatePaymentResponse.builder()
                .transactionCode(tx.getTransactionCode())
                .schoolId(tx.getSchoolId())
                .amount(tx.getAmount())
                .currency(tx.getCurrency())
                .status(tx.getStatus())
                .paymentProvider(tx.getPaymentProvider())
                .providerOrderId(orderResult != null ? orderResult.getProviderOrderId() : tx.getProviderOrderId())
                .providerPaymentUrl(orderResult != null ? orderResult.getProviderPaymentUrl() : null)
                .checkoutPayload(orderResult != null ? orderResult.getCheckoutPayload() : null)
                .idempotencyKey(tx.getIdempotencyKey())
                .build();
    }
}
