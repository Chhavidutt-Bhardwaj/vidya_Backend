package com.vidya.master.payment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.vidya.common.api.PagedApiResponse;
import com.vidya.common.exception.GlobalExceptionHandler;
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
import com.vidya.master.payment.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private PaymentController paymentController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private UUID schoolId;
    private PaymentTransactionResponse sampleTxResponse;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(paymentController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        schoolId = UUID.randomUUID();
        sampleTxResponse = PaymentTransactionResponse.builder()
                .id(UUID.randomUUID())
                .transactionCode("TXN-1234567890AB")
                .schoolId(schoolId)
                .amount(new BigDecimal("5000.00"))
                .currency("INR")
                .status(PaymentStatus.SUCCESS)
                .paymentProvider(PaymentProvider.RAZORPAY)
                .providerOrderId("order_rzp_123")
                .providerTransactionId("pay_123")
                .idempotencyKey("IDEM-123")
                .paymentMethod(PaymentMethod.UPI)
                .initiatedAt(Instant.now())
                .completedAt(Instant.now())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/payments/initiate should return 201 Created")
    void initiatePayment_Success() throws Exception {
        InitiatePaymentRequest request = InitiatePaymentRequest.builder()
                .schoolId(schoolId)
                .amount(new BigDecimal("5000.00"))
                .currency("INR")
                .paymentProvider(PaymentProvider.RAZORPAY)
                .idempotencyKey("IDEM-123")
                .build();

        InitiatePaymentResponse response = InitiatePaymentResponse.builder()
                .transactionCode("TXN-1234567890AB")
                .schoolId(schoolId)
                .amount(new BigDecimal("5000.00"))
                .currency("INR")
                .status(PaymentStatus.INITIATED)
                .paymentProvider(PaymentProvider.RAZORPAY)
                .providerOrderId("order_rzp_123")
                .idempotencyKey("IDEM-123")
                .build();

        when(paymentService.initiatePayment(any(InitiatePaymentRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/payments/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", equalTo(true)))
                .andExpect(jsonPath("$.data.transactionCode", equalTo("TXN-1234567890AB")))
                .andExpect(jsonPath("$.data.providerOrderId", equalTo("order_rzp_123")));
    }

    @Test
    @DisplayName("POST /api/v1/payments/verify should return 200 Ok")
    void verifyPayment_Success() throws Exception {
        VerifyPaymentRequest request = VerifyPaymentRequest.builder()
                .transactionCode("TXN-1234567890AB")
                .providerPaymentId("pay_123")
                .providerOrderId("order_rzp_123")
                .providerSignature("signature_hex")
                .build();

        when(paymentService.verifyPayment(any(VerifyPaymentRequest.class))).thenReturn(sampleTxResponse);

        mockMvc.perform(post("/api/v1/payments/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", equalTo(true)))
                .andExpect(jsonPath("$.data.status", equalTo("SUCCESS")))
                .andExpect(jsonPath("$.data.providerTransactionId", equalTo("pay_123")));
    }

    @Test
    @DisplayName("POST /api/v1/payments/manual/confirm should return 200 Ok")
    void confirmManualPayment_Success() throws Exception {
        ManualPaymentConfirmRequest request = ManualPaymentConfirmRequest.builder()
                .transactionCode("TXN-1234567890AB")
                .paymentMethod(PaymentMethod.NEFT)
                .referenceNumber("UTR999999")
                .confirmedBy("admin@vidya.io")
                .notes("Bank confirmation received")
                .build();

        when(paymentService.confirmManualPayment(any(ManualPaymentConfirmRequest.class))).thenReturn(sampleTxResponse);

        mockMvc.perform(post("/api/v1/payments/manual/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", equalTo(true)))
                .andExpect(jsonPath("$.data.status", equalTo("SUCCESS")));
    }

    @Test
    @DisplayName("GET /api/v1/payments/{transactionCode} should return transaction")
    void getByTransactionCode_Success() throws Exception {
        when(paymentService.getTransactionByCode("TXN-1234567890AB")).thenReturn(sampleTxResponse);

        mockMvc.perform(get("/api/v1/payments/TXN-1234567890AB"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", equalTo(true)))
                .andExpect(jsonPath("$.data.transactionCode", equalTo("TXN-1234567890AB")));
    }

    @Test
    @DisplayName("GET /api/v1/payments/school/{schoolId} should return paginated transactions")
    void getBySchool_Success() throws Exception {
        var page = new PageImpl<>(List.of(sampleTxResponse), PageRequest.of(0, 20), 1);
        PagedApiResponse<PaymentTransactionResponse> pagedResponse = PagedApiResponse.of(
                "SUCCESS", "OK", List.of(sampleTxResponse), page);

        when(paymentService.getTransactionsBySchool(eq(schoolId), any())).thenReturn(pagedResponse);

        mockMvc.perform(get("/api/v1/payments/school/{schoolId}", schoolId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", equalTo(true)))
                .andExpect(jsonPath("$.data", hasSize(1)));
    }

    @Test
    @DisplayName("POST /api/v1/payments/refund should return 200 Ok")
    void refundPayment_Success() throws Exception {
        PaymentRefundRequest request = PaymentRefundRequest.builder()
                .transactionCode("TXN-1234567890AB")
                .amount(new BigDecimal("5000.00"))
                .reason("Customer requested refund")
                .build();

        PaymentRefundResponse response = PaymentRefundResponse.builder()
                .transactionCode("TXN-1234567890AB")
                .refundId("rfnd_123")
                .amount(new BigDecimal("5000.00"))
                .status("REFUNDED")
                .refundedAt(Instant.now())
                .message("Refund processed successfully")
                .build();

        when(paymentService.refundPayment(any(PaymentRefundRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/payments/refund")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", equalTo(true)))
                .andExpect(jsonPath("$.data.status", equalTo("REFUNDED")));
    }
}
