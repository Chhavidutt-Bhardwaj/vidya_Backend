package com.vidya.master.payment.controller;

import com.vidya.common.api.ApiResponse;
import com.vidya.common.api.ApiResponseCode;
import com.vidya.common.api.PagedApiResponse;
import com.vidya.master.payment.dto.InitiatePaymentRequest;
import com.vidya.master.payment.dto.InitiatePaymentResponse;
import com.vidya.master.payment.dto.ManualPaymentConfirmRequest;
import com.vidya.master.payment.dto.PaymentRefundRequest;
import com.vidya.master.payment.dto.PaymentRefundResponse;
import com.vidya.master.payment.dto.PaymentTransactionResponse;
import com.vidya.master.payment.dto.VerifyPaymentRequest;
import com.vidya.master.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Payment Management", description = "APIs for initiating, verifying, and managing online & manual payments")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/initiate")
    @Operation(summary = "Initiate a payment transaction with Razorpay, Cashfree, or Manual gateway")
    public ResponseEntity<ApiResponse<InitiatePaymentResponse>> initiatePayment(
            @Valid @RequestBody InitiatePaymentRequest request) {
        InitiatePaymentResponse response = paymentService.initiatePayment(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(ApiResponseCode.PAYMENT_INITIATED, "Payment initiated successfully", response));
    }

    @PostMapping("/verify")
    @Operation(summary = "Verify gateway payment completion token / signature")
    public ResponseEntity<ApiResponse<PaymentTransactionResponse>> verifyPayment(
            @Valid @RequestBody VerifyPaymentRequest request) {
        PaymentTransactionResponse response = paymentService.verifyPayment(request);
        return ResponseEntity.ok(ApiResponse.success(ApiResponseCode.PAYMENT_VERIFIED, "Payment verified successfully", response));
    }

    @PostMapping("/manual/confirm")
    @Operation(summary = "Confirm an offline or manual payment by reference / cheque / bank transfer")
    public ResponseEntity<ApiResponse<PaymentTransactionResponse>> confirmManualPayment(
            @Valid @RequestBody ManualPaymentConfirmRequest request) {
        PaymentTransactionResponse response = paymentService.confirmManualPayment(request);
        return ResponseEntity.ok(ApiResponse.success(ApiResponseCode.PAYMENT_VERIFIED, "Manual payment confirmed successfully", response));
    }

    @GetMapping("/{transactionCode}")
    @Operation(summary = "Get payment transaction details by transaction code")
    public ResponseEntity<ApiResponse<PaymentTransactionResponse>> getByTransactionCode(
            @PathVariable String transactionCode) {
        PaymentTransactionResponse response = paymentService.getTransactionByCode(transactionCode);
        return ResponseEntity.ok(ApiResponse.success(ApiResponseCode.SUCCESS, "Payment transaction retrieved successfully", response));
    }

    @GetMapping("/school/{schoolId}")
    @Operation(summary = "Get paginated payment transaction history for a school")
    public ResponseEntity<PagedApiResponse<PaymentTransactionResponse>> getBySchool(
            @PathVariable UUID schoolId,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        PagedApiResponse<PaymentTransactionResponse> response = paymentService.getTransactionsBySchool(schoolId, pageable);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refund")
    @Operation(summary = "Process a refund for a successful payment transaction")
    public ResponseEntity<ApiResponse<PaymentRefundResponse>> refundPayment(
            @Valid @RequestBody PaymentRefundRequest request) {
        PaymentRefundResponse response = paymentService.refundPayment(request);
        return ResponseEntity.ok(ApiResponse.success(ApiResponseCode.SUCCESS, "Refund processed successfully", response));
    }
}
