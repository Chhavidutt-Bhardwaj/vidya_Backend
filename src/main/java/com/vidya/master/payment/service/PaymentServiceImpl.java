package com.vidya.master.payment.service;

import com.vidya.common.api.ApiResponseCode;
import com.vidya.common.api.PagedApiResponse;
import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.DuplicateResourceException;
import com.vidya.common.exception.InvalidStateTransitionException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.common.util.CodeGenerator;
import com.vidya.master.payment.dto.InitiatePaymentRequest;
import com.vidya.master.payment.dto.InitiatePaymentResponse;
import com.vidya.master.payment.dto.ManualPaymentConfirmRequest;
import com.vidya.master.payment.dto.PaymentRefundRequest;
import com.vidya.master.payment.dto.PaymentRefundResponse;
import com.vidya.master.payment.dto.PaymentTransactionResponse;
import com.vidya.master.payment.dto.VerifyPaymentRequest;
import com.vidya.master.payment.entity.PaymentProvider;
import com.vidya.master.payment.entity.PaymentStatus;
import com.vidya.master.payment.entity.PaymentTransaction;
import com.vidya.master.payment.gateway.PaymentGatewayProvider;
import com.vidya.master.payment.gateway.PaymentGatewayRegistry;
import com.vidya.master.payment.gateway.model.CreateOrderResult;
import com.vidya.master.payment.gateway.model.GatewayRefundRequest;
import com.vidya.master.payment.gateway.model.PaymentOrderRequest;
import com.vidya.master.payment.gateway.model.PaymentRefundResult;
import com.vidya.master.payment.gateway.model.PaymentVerificationRequest;
import com.vidya.master.payment.gateway.model.PaymentVerificationResult;
import com.vidya.master.payment.gateway.model.WebhookProcessingResult;
import com.vidya.master.payment.mapper.PaymentMapper;
import com.vidya.master.payment.repository.PaymentTransactionRepository;
import com.vidya.master.school.repository.SchoolRepository;
import com.vidya.master.workflow.event.PaymentCompletedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class PaymentServiceImpl implements PaymentService {

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final SchoolRepository schoolRepository;
    private final PaymentGatewayRegistry gatewayRegistry;
    private final PaymentMapper paymentMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Autowired
    public PaymentServiceImpl(PaymentTransactionRepository paymentTransactionRepository,
                              SchoolRepository schoolRepository,
                              PaymentGatewayRegistry gatewayRegistry,
                              PaymentMapper paymentMapper,
                              ApplicationEventPublisher eventPublisher) {
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.schoolRepository = schoolRepository;
        this.gatewayRegistry = gatewayRegistry;
        this.paymentMapper = paymentMapper;
        this.eventPublisher = eventPublisher;
    }

    public PaymentServiceImpl(PaymentTransactionRepository paymentTransactionRepository,
                              SchoolRepository schoolRepository,
                              PaymentGatewayRegistry gatewayRegistry,
                              PaymentMapper paymentMapper) {
        this(paymentTransactionRepository, schoolRepository, gatewayRegistry, paymentMapper, null);
    }

    @Override
    @Transactional(transactionManager = "masterTransactionManager")
    public InitiatePaymentResponse initiatePayment(InitiatePaymentRequest request) {
        log.info("Initiating payment for school: {}, amount: {}, provider: {}, idempotencyKey: {}",
                request.getSchoolId(), request.getAmount(), request.getPaymentProvider(), request.getIdempotencyKey());

        if (!schoolRepository.existsById(request.getSchoolId())) {
            throw new ResourceNotFoundException("School", request.getSchoolId());
        }

        // 1. Idempotency Check
        Optional<PaymentTransaction> existingOpt = paymentTransactionRepository.findByIdempotencyKey(request.getIdempotencyKey());
        if (existingOpt.isPresent()) {
            PaymentTransaction existing = existingOpt.get();
            if (!existing.getSchoolId().equals(request.getSchoolId()) || existing.getAmount().compareTo(request.getAmount()) != 0) {
                throw new DuplicateResourceException("PaymentTransaction", "idempotencyKey", request.getIdempotencyKey());
            }
            log.info("Idempotent payment initiation hit for transaction: {}", existing.getTransactionCode());
            return paymentMapper.toInitiateResponse(existing, null);
        }

        // 2. Create Transaction Record
        String txnCode = CodeGenerator.transactionCode();
        PaymentTransaction tx = new PaymentTransaction(
                txnCode,
                request.getSchoolId(),
                request.getSubscriptionId(),
                request.getOnboardingId(),
                request.getAmount(),
                request.getCurrency(),
                request.getPaymentProvider(),
                request.getIdempotencyKey(),
                request.getPaymentMethod()
        );

        PaymentTransaction savedTx = paymentTransactionRepository.save(tx);

        // 3. Delegate Order Creation to Provider
        PaymentGatewayProvider gateway = gatewayRegistry.getGateway(request.getPaymentProvider());
        PaymentOrderRequest orderRequest = PaymentOrderRequest.builder()
                .transactionCode(savedTx.getTransactionCode())
                .amount(savedTx.getAmount())
                .currency(savedTx.getCurrency())
                .customerName(request.getCustomerName())
                .customerEmail(request.getCustomerEmail())
                .customerPhone(request.getCustomerPhone())
                .notes(request.getNotes())
                .build();

        CreateOrderResult orderResult = gateway.createOrder(orderRequest);

        // 4. Update with Provider Order ID
        savedTx.setProviderOrderId(orderResult.getProviderOrderId());
        savedTx = paymentTransactionRepository.save(savedTx);

        log.info("Payment initiated successfully. Code: {}, ProviderOrderId: {}",
                savedTx.getTransactionCode(), savedTx.getProviderOrderId());

        return paymentMapper.toInitiateResponse(savedTx, orderResult);
    }

    @Override
    @Transactional(transactionManager = "masterTransactionManager")
    public PaymentTransactionResponse verifyPayment(VerifyPaymentRequest request) {
        log.info("Verifying payment for transaction code: {}", request.getTransactionCode());

        PaymentTransaction tx = paymentTransactionRepository.findByTransactionCode(request.getTransactionCode())
                .orElseThrow(() -> new ResourceNotFoundException("PaymentTransaction", request.getTransactionCode()));

        if (tx.getStatus() == PaymentStatus.SUCCESS) {
            log.info("Transaction {} is already marked as SUCCESS", tx.getTransactionCode());
            return paymentMapper.toResponse(tx);
        }

        if (tx.getStatus() == PaymentStatus.CANCELLED || tx.getStatus() == PaymentStatus.REFUNDED) {
            throw new InvalidStateTransitionException("PaymentTransaction", tx.getStatus().name(), PaymentStatus.SUCCESS.name());
        }

        PaymentGatewayProvider gateway = gatewayRegistry.getGateway(tx.getPaymentProvider());
        PaymentVerificationRequest verifyRequest = PaymentVerificationRequest.builder()
                .transactionCode(tx.getTransactionCode())
                .providerPaymentId(request.getProviderPaymentId())
                .providerOrderId(request.getProviderOrderId() != null ? request.getProviderOrderId() : tx.getProviderOrderId())
                .providerSignature(request.getProviderSignature())
                .rawPayload(request.getRawPayload())
                .build();

        PaymentVerificationResult result = gateway.verifyPayment(verifyRequest);

        if (result.isSuccessful()) {
            tx.markSuccess(result.getProviderTransactionId(), result.getPaymentMethod(), result.getRawResponse());
            log.info("Payment verified successfully for transaction: {}", tx.getTransactionCode());
        } else {
            tx.markFailed(result.getFailureReason(), result.getRawResponse());
            log.warn("Payment verification failed for transaction: {}, reason: {}", tx.getTransactionCode(), result.getFailureReason());
        }

        PaymentTransaction updated = paymentTransactionRepository.save(tx);
        if (updated.getStatus() == PaymentStatus.SUCCESS) {
            publishPaymentCompletedEvent(updated);
        }
        return paymentMapper.toResponse(updated);
    }

    @Override
    @Transactional(transactionManager = "masterTransactionManager")
    public PaymentTransactionResponse confirmManualPayment(ManualPaymentConfirmRequest request) {
        log.info("Confirming manual payment for transaction code: {}", request.getTransactionCode());

        PaymentTransaction tx = paymentTransactionRepository.findByTransactionCode(request.getTransactionCode())
                .orElseThrow(() -> new ResourceNotFoundException("PaymentTransaction", request.getTransactionCode()));

        if (tx.getPaymentProvider() != PaymentProvider.MANUAL) {
            throw new BusinessException(ApiResponseCode.BUSINESS_ERROR, "Only manual payment transactions can be confirmed via this endpoint");
        }

        if (tx.getStatus() == PaymentStatus.SUCCESS) {
            log.info("Manual transaction {} is already SUCCESS", tx.getTransactionCode());
            return paymentMapper.toResponse(tx);
        }

        Map<String, Object> callbackMap = new HashMap<>();
        callbackMap.put("referenceNumber", request.getReferenceNumber());
        callbackMap.put("confirmedBy", request.getConfirmedBy());
        callbackMap.put("notes", request.getNotes());
        callbackMap.put("confirmedAt", Instant.now().toString());

        tx.markSuccess(request.getReferenceNumber(), request.getPaymentMethod(), callbackMap);
        PaymentTransaction saved = paymentTransactionRepository.save(tx);
        publishPaymentCompletedEvent(saved);

        log.info("Manual payment transaction {} confirmed successfully", tx.getTransactionCode());
        return paymentMapper.toResponse(saved);
    }

    @Override
    @Transactional(transactionManager = "masterTransactionManager")
    public WebhookProcessingResult processWebhook(PaymentProvider provider, String payload, Map<String, String> headers) {
        log.info("Processing webhook for provider: {}", provider);
        PaymentGatewayProvider gateway = gatewayRegistry.getGateway(provider);
        WebhookProcessingResult result = gateway.processWebhook(payload, headers);

        if (!result.isHandled()) {
            log.warn("Webhook was not handled by provider: {}, reason: {}", provider, result.getFailureReason());
            return result;
        }

        // Update transaction if identified
        Optional<PaymentTransaction> txOpt = Optional.empty();
        if (result.getTransactionCode() != null) {
            txOpt = paymentTransactionRepository.findByTransactionCode(result.getTransactionCode());
        } else if (result.getProviderOrderId() != null) {
            txOpt = paymentTransactionRepository.findByProviderOrderId(result.getProviderOrderId());
        } else if (result.getProviderTransactionId() != null) {
            txOpt = paymentTransactionRepository.findByProviderTransactionId(result.getProviderTransactionId());
        }

        txOpt.ifPresent(tx -> {
            if (result.getStatus() == PaymentStatus.SUCCESS && tx.getStatus() != PaymentStatus.SUCCESS) {
                tx.markSuccess(result.getProviderTransactionId(), result.getPaymentMethod(), result.getRawPayload());
                PaymentTransaction saved = paymentTransactionRepository.save(tx);
                publishPaymentCompletedEvent(saved);
                log.info("Webhook marked transaction {} as SUCCESS", tx.getTransactionCode());
            } else if (result.getStatus() == PaymentStatus.FAILED && tx.getStatus() != PaymentStatus.SUCCESS) {
                tx.markFailed(result.getFailureReason(), result.getRawPayload());
                paymentTransactionRepository.save(tx);
                log.info("Webhook marked transaction {} as FAILED", tx.getTransactionCode());
            }
        });

        return result;
    }

    private void publishPaymentCompletedEvent(PaymentTransaction tx) {
        if (eventPublisher != null) {
            eventPublisher.publishEvent(PaymentCompletedEvent.builder()
                    .transactionCode(tx.getTransactionCode())
                    .schoolId(tx.getSchoolId())
                    .subscriptionId(tx.getSubscriptionId())
                    .onboardingId(tx.getOnboardingId())
                    .amount(tx.getAmount())
                    .currency(tx.getCurrency())
                    .paymentProvider(tx.getPaymentProvider())
                    .paymentMethod(tx.getPaymentMethod())
                    .status(tx.getStatus())
                    .providerTransactionId(tx.getProviderTransactionId())
                    .completedAt(tx.getCompletedAt())
                    .build());
        }
    }

    @Override
    @Transactional(transactionManager = "masterTransactionManager")
    public PaymentRefundResponse refundPayment(PaymentRefundRequest request) {
        log.info("Processing refund for transaction code: {}, amount: {}", request.getTransactionCode(), request.getAmount());

        PaymentTransaction tx = paymentTransactionRepository.findByTransactionCode(request.getTransactionCode())
                .orElseThrow(() -> new ResourceNotFoundException("PaymentTransaction", request.getTransactionCode()));

        if (tx.getStatus() != PaymentStatus.SUCCESS) {
            throw new InvalidStateTransitionException("PaymentTransaction", tx.getStatus().name(), PaymentStatus.REFUNDED.name());
        }

        if (request.getAmount().compareTo(tx.getAmount()) > 0) {
            throw new BusinessException(ApiResponseCode.BUSINESS_ERROR, "Refund amount cannot exceed original transaction amount");
        }

        PaymentGatewayProvider gateway = gatewayRegistry.getGateway(tx.getPaymentProvider());
        GatewayRefundRequest refundRequest = GatewayRefundRequest.builder()
                .transactionCode(tx.getTransactionCode())
                .providerTransactionId(tx.getProviderTransactionId())
                .amount(request.getAmount())
                .reason(request.getReason())
                .build();

        PaymentRefundResult result = gateway.processRefund(refundRequest);

        if (!result.isSuccessful()) {
            throw new BusinessException(ApiResponseCode.BUSINESS_ERROR, "Refund failed: " + result.getFailureReason());
        }

        tx.markRefunded(result.getRawResponse());
        paymentTransactionRepository.save(tx);

        log.info("Refund completed for transaction: {}, refundId: {}", tx.getTransactionCode(), result.getRefundId());

        return PaymentRefundResponse.builder()
                .transactionCode(tx.getTransactionCode())
                .refundId(result.getRefundId())
                .amount(request.getAmount())
                .status("REFUNDED")
                .refundedAt(Instant.now())
                .message("Refund processed successfully")
                .build();
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "masterTransactionManager")
    public PaymentTransactionResponse getTransactionByCode(String transactionCode) {
        PaymentTransaction tx = paymentTransactionRepository.findByTransactionCode(transactionCode)
                .orElseThrow(() -> new ResourceNotFoundException("PaymentTransaction", transactionCode));
        return paymentMapper.toResponse(tx);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "masterTransactionManager")
    public PagedApiResponse<PaymentTransactionResponse> getTransactionsBySchool(UUID schoolId, Pageable pageable) {
        if (!schoolRepository.existsById(schoolId)) {
            throw new ResourceNotFoundException("School", schoolId);
        }
        Page<PaymentTransaction> page = paymentTransactionRepository.findBySchoolId(schoolId, pageable);
        return PagedApiResponse.of(ApiResponseCode.SUCCESS, "Payment transactions retrieved successfully",
                page.getContent().stream().map(paymentMapper::toResponse).toList(), page);
    }
}
