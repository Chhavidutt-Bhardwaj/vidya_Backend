package com.vidya.master.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidya.common.api.PagedApiResponse;
import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.DuplicateResourceException;
import com.vidya.common.exception.InvalidStateTransitionException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.master.payment.dto.InitiatePaymentRequest;
import com.vidya.master.payment.dto.InitiatePaymentResponse;
import com.vidya.master.payment.dto.ManualPaymentConfirmRequest;
import com.vidya.master.payment.dto.PaymentRefundRequest;
import com.vidya.master.payment.dto.PaymentRefundResponse;
import com.vidya.master.payment.dto.PaymentTransactionResponse;
import com.vidya.master.payment.dto.VerifyPaymentRequest;
import com.vidya.master.payment.entity.PaymentMethod;
import com.vidya.master.payment.entity.PaymentProvider;
import com.vidya.master.payment.entity.PaymentStatus;
import com.vidya.master.payment.entity.PaymentTransaction;
import com.vidya.master.payment.gateway.PaymentGatewayRegistry;
import com.vidya.master.payment.gateway.cashfree.CashfreePaymentGateway;
import com.vidya.master.payment.gateway.cashfree.CashfreeProperties;
import com.vidya.master.payment.gateway.manual.ManualPaymentGateway;
import com.vidya.master.payment.gateway.model.WebhookProcessingResult;
import com.vidya.master.payment.gateway.razorpay.RazorpayPaymentGateway;
import com.vidya.master.payment.gateway.razorpay.RazorpayProperties;
import com.vidya.master.payment.mapper.PaymentMapper;
import com.vidya.master.payment.repository.PaymentTransactionRepository;
import com.vidya.master.school.repository.SchoolRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;

    @Mock
    private SchoolRepository schoolRepository;

    private PaymentGatewayRegistry gatewayRegistry;
    private PaymentMapper paymentMapper;
    private PaymentServiceImpl paymentService;

    private UUID schoolId;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        RazorpayProperties rzpProps = new RazorpayProperties();
        rzpProps.setKeyId("rzp_key");
        rzpProps.setKeySecret("rzp_secret");
        rzpProps.setWebhookSecret("rzp_webhook");
        RazorpayPaymentGateway razorpayGateway = new RazorpayPaymentGateway(rzpProps, objectMapper);

        CashfreeProperties cfProps = new CashfreeProperties();
        cfProps.setAppId("cf_app");
        cfProps.setSecretKey("cf_secret");
        CashfreePaymentGateway cashfreeGateway = new CashfreePaymentGateway(cfProps, objectMapper);
        ManualPaymentGateway manualGateway = new ManualPaymentGateway();

        gatewayRegistry = new PaymentGatewayRegistry(List.of(razorpayGateway, cashfreeGateway, manualGateway));
        paymentMapper = new PaymentMapper();
        paymentService = new PaymentServiceImpl(paymentTransactionRepository, schoolRepository, gatewayRegistry, paymentMapper);

        schoolId = UUID.randomUUID();
    }

    @Test
    @DisplayName("initiatePayment with Razorpay should create transaction and provider order")
    void initiatePayment_Razorpay_Success() {
        InitiatePaymentRequest request = InitiatePaymentRequest.builder()
                .schoolId(schoolId)
                .amount(new BigDecimal("1000.00"))
                .currency("INR")
                .paymentProvider(PaymentProvider.RAZORPAY)
                .idempotencyKey("IDEM-001")
                .customerName("John Doe")
                .customerEmail("john@school.com")
                .build();

        when(schoolRepository.existsById(schoolId)).thenReturn(true);
        when(paymentTransactionRepository.findByIdempotencyKey("IDEM-001")).thenReturn(Optional.empty());
        when(paymentTransactionRepository.save(any(PaymentTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        InitiatePaymentResponse response = paymentService.initiatePayment(request);

        assertThat(response).isNotNull();
        assertThat(response.getPaymentProvider()).isEqualTo(PaymentProvider.RAZORPAY);
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.INITIATED);
        assertThat(response.getProviderOrderId()).startsWith("order_rzp_");
        assertThat(response.getCheckoutPayload()).containsKey("key");
    }

    @Test
    @DisplayName("initiatePayment with Cashfree should create Cashfree session and order")
    void initiatePayment_Cashfree_Success() {
        InitiatePaymentRequest request = InitiatePaymentRequest.builder()
                .schoolId(schoolId)
                .amount(new BigDecimal("2500.00"))
                .currency("INR")
                .paymentProvider(PaymentProvider.CASHFREE)
                .idempotencyKey("IDEM-002")
                .customerName("Jane Doe")
                .customerPhone("9876543210")
                .build();

        when(schoolRepository.existsById(schoolId)).thenReturn(true);
        when(paymentTransactionRepository.findByIdempotencyKey("IDEM-002")).thenReturn(Optional.empty());
        when(paymentTransactionRepository.save(any(PaymentTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        InitiatePaymentResponse response = paymentService.initiatePayment(request);

        assertThat(response).isNotNull();
        assertThat(response.getPaymentProvider()).isEqualTo(PaymentProvider.CASHFREE);
        assertThat(response.getProviderOrderId()).startsWith("cf_order_");
        assertThat(response.getCheckoutPayload()).containsKey("payment_session_id");
    }

    @Test
    @DisplayName("initiatePayment with Manual should create manual payment order instructions")
    void initiatePayment_Manual_Success() {
        InitiatePaymentRequest request = InitiatePaymentRequest.builder()
                .schoolId(schoolId)
                .amount(new BigDecimal("50000.00"))
                .currency("INR")
                .paymentProvider(PaymentProvider.MANUAL)
                .paymentMethod(PaymentMethod.NEFT)
                .idempotencyKey("IDEM-003")
                .build();

        when(schoolRepository.existsById(schoolId)).thenReturn(true);
        when(paymentTransactionRepository.findByIdempotencyKey("IDEM-003")).thenReturn(Optional.empty());
        when(paymentTransactionRepository.save(any(PaymentTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        InitiatePaymentResponse response = paymentService.initiatePayment(request);

        assertThat(response).isNotNull();
        assertThat(response.getPaymentProvider()).isEqualTo(PaymentProvider.MANUAL);
        assertThat(response.getProviderOrderId()).startsWith("MANUAL-");
    }

    @Test
    @DisplayName("initiatePayment with existing matching idempotency key should return existing transaction")
    void initiatePayment_IdempotentHit_Success() {
        PaymentTransaction existingTx = new PaymentTransaction(
                "TXN-EXISTING", schoolId, null, null, new BigDecimal("1000.00"), "INR",
                PaymentProvider.RAZORPAY, "IDEM-001", null);

        InitiatePaymentRequest request = InitiatePaymentRequest.builder()
                .schoolId(schoolId)
                .amount(new BigDecimal("1000.00"))
                .currency("INR")
                .paymentProvider(PaymentProvider.RAZORPAY)
                .idempotencyKey("IDEM-001")
                .build();

        when(schoolRepository.existsById(schoolId)).thenReturn(true);
        when(paymentTransactionRepository.findByIdempotencyKey("IDEM-001")).thenReturn(Optional.of(existingTx));

        InitiatePaymentResponse response = paymentService.initiatePayment(request);

        assertThat(response.getTransactionCode()).isEqualTo("TXN-EXISTING");
    }

    @Test
    @DisplayName("initiatePayment with mismatching idempotency key details should throw DuplicateResourceException")
    void initiatePayment_IdempotentMismatch_ThrowsException() {
        PaymentTransaction existingTx = new PaymentTransaction(
                "TXN-EXISTING", schoolId, null, null, new BigDecimal("500.00"), "INR",
                PaymentProvider.RAZORPAY, "IDEM-001", null);

        InitiatePaymentRequest request = InitiatePaymentRequest.builder()
                .schoolId(schoolId)
                .amount(new BigDecimal("1000.00"))
                .currency("INR")
                .paymentProvider(PaymentProvider.RAZORPAY)
                .idempotencyKey("IDEM-001")
                .build();

        when(schoolRepository.existsById(schoolId)).thenReturn(true);
        when(paymentTransactionRepository.findByIdempotencyKey("IDEM-001")).thenReturn(Optional.of(existingTx));

        assertThatThrownBy(() -> paymentService.initiatePayment(request))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    @DisplayName("verifyPayment with valid signature should transition transaction to SUCCESS")
    void verifyPayment_Success() {
        PaymentTransaction tx = new PaymentTransaction(
                "TXN-12345", schoolId, null, null, new BigDecimal("1000.00"), "INR",
                PaymentProvider.RAZORPAY, "IDEM-001", null);
        tx.setProviderOrderId("order_rzp_123");

        VerifyPaymentRequest request = VerifyPaymentRequest.builder()
                .transactionCode("TXN-12345")
                .providerPaymentId("pay_12345")
                .providerOrderId("order_rzp_123")
                .build();

        when(paymentTransactionRepository.findByTransactionCode("TXN-12345")).thenReturn(Optional.of(tx));
        when(paymentTransactionRepository.save(any(PaymentTransaction.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentTransactionResponse response = paymentService.verifyPayment(request);

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(response.getProviderTransactionId()).isEqualTo("pay_12345");
    }

    @Test
    @DisplayName("confirmManualPayment should transition MANUAL transaction to SUCCESS with reference number")
    void confirmManualPayment_Success() {
        PaymentTransaction tx = new PaymentTransaction(
                "TXN-MAN-01", schoolId, null, null, new BigDecimal("10000.00"), "INR",
                PaymentProvider.MANUAL, "IDEM-MAN-01", null);

        ManualPaymentConfirmRequest request = ManualPaymentConfirmRequest.builder()
                .transactionCode("TXN-MAN-01")
                .paymentMethod(PaymentMethod.NEFT)
                .referenceNumber("UTR123456789")
                .confirmedBy("superadmin@vidya.io")
                .notes("Verified with bank statement")
                .build();

        when(paymentTransactionRepository.findByTransactionCode("TXN-MAN-01")).thenReturn(Optional.of(tx));
        when(paymentTransactionRepository.save(any(PaymentTransaction.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentTransactionResponse response = paymentService.confirmManualPayment(request);

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(response.getPaymentMethod()).isEqualTo(PaymentMethod.NEFT);
        assertThat(response.getProviderTransactionId()).isEqualTo("UTR123456789");
    }

    @Test
    @DisplayName("confirmManualPayment for non-manual provider should throw BusinessException")
    void confirmManualPayment_NonManualProvider_ThrowsException() {
        PaymentTransaction tx = new PaymentTransaction(
                "TXN-RZP-01", schoolId, null, null, new BigDecimal("1000.00"), "INR",
                PaymentProvider.RAZORPAY, "IDEM-RZP-01", null);

        ManualPaymentConfirmRequest request = ManualPaymentConfirmRequest.builder()
                .transactionCode("TXN-RZP-01")
                .paymentMethod(PaymentMethod.NEFT)
                .referenceNumber("UTR123")
                .build();

        when(paymentTransactionRepository.findByTransactionCode("TXN-RZP-01")).thenReturn(Optional.of(tx));

        assertThatThrownBy(() -> paymentService.confirmManualPayment(request))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("refundPayment for successful transaction should succeed")
    void refundPayment_Success() {
        PaymentTransaction tx = new PaymentTransaction(
                "TXN-REF-01", schoolId, null, null, new BigDecimal("1000.00"), "INR",
                PaymentProvider.RAZORPAY, "IDEM-REF-01", null);
        tx.markSuccess("pay_999", PaymentMethod.UPI, Map.of());

        PaymentRefundRequest request = PaymentRefundRequest.builder()
                .transactionCode("TXN-REF-01")
                .amount(new BigDecimal("1000.00"))
                .reason("Duplicate charge")
                .build();

        when(paymentTransactionRepository.findByTransactionCode("TXN-REF-01")).thenReturn(Optional.of(tx));
        when(paymentTransactionRepository.save(any(PaymentTransaction.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentRefundResponse response = paymentService.refundPayment(request);

        assertThat(response.getStatus()).isEqualTo("REFUNDED");
        assertThat(response.getRefundId()).isNotNull();
    }

    @Test
    @DisplayName("refundPayment on non-success transaction should throw InvalidStateTransitionException")
    void refundPayment_NonSuccess_ThrowsException() {
        PaymentTransaction tx = new PaymentTransaction(
                "TXN-INIT-01", schoolId, null, null, new BigDecimal("1000.00"), "INR",
                PaymentProvider.RAZORPAY, "IDEM-01", null);

        PaymentRefundRequest request = PaymentRefundRequest.builder()
                .transactionCode("TXN-INIT-01")
                .amount(new BigDecimal("1000.00"))
                .reason("Refund test")
                .build();

        when(paymentTransactionRepository.findByTransactionCode("TXN-INIT-01")).thenReturn(Optional.of(tx));

        assertThatThrownBy(() -> paymentService.refundPayment(request))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    @DisplayName("getTransactionsBySchool should return paged responses")
    void getTransactionsBySchool_Success() {
        PaymentTransaction tx = new PaymentTransaction(
                "TXN-PAGE-01", schoolId, null, null, new BigDecimal("1000.00"), "INR",
                PaymentProvider.RAZORPAY, "IDEM-PAGE-01", null);

        Pageable pageable = PageRequest.of(0, 10);
        when(schoolRepository.existsById(schoolId)).thenReturn(true);
        when(paymentTransactionRepository.findBySchoolId(schoolId, pageable))
                .thenReturn(new PageImpl<>(List.of(tx), pageable, 1));

        PagedApiResponse<PaymentTransactionResponse> result = paymentService.getTransactionsBySchool(schoolId, pageable);

        assertThat(result.getData()).hasSize(1);
        assertThat(result.getData().get(0).getTransactionCode()).isEqualTo("TXN-PAGE-01");
    }
}
