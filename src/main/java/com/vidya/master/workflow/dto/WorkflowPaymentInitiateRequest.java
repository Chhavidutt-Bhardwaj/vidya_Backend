package com.vidya.master.workflow.dto;

import com.vidya.master.payment.entity.PaymentMethod;
import com.vidya.master.payment.entity.PaymentProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request payload for initiating payment for school onboarding")
public class WorkflowPaymentInitiateRequest {

    @NotNull(message = "Payment provider is required")
    @Schema(description = "Payment provider gateway", example = "RAZORPAY")
    private PaymentProvider paymentProvider;

    @Schema(description = "Payment method (e.g. CARD, UPI, NETBANKING, NEFT, CHEQUE)", example = "UPI")
    private PaymentMethod paymentMethod;

    @Schema(description = "Deterministic idempotency key for payment order creation", example = "ONB-PAY-SCH123-171112233")
    private String idempotencyKey;

    @Schema(description = "Additional notes or description", example = "Subscription fee for academic year 2026-27")
    private String notes;
}
