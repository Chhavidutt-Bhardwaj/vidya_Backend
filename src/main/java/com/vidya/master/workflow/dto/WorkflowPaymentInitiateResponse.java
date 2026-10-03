package com.vidya.master.workflow.dto;

import com.vidya.master.onboarding.entity.OnboardingStatus;
import com.vidya.master.onboarding.entity.OnboardingStep;
import com.vidya.master.payment.entity.PaymentProvider;
import com.vidya.master.payment.entity.PaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response payload after initiating workflow payment")
public class WorkflowPaymentInitiateResponse {

    @Schema(description = "Onboarding UUID")
    private UUID onboardingId;

    @Schema(description = "Onboarding Code", example = "ONB-A1B2C3D4")
    private String onboardingCode;

    @Schema(description = "Transaction Code", example = "TXN-A1B2C3D4")
    private String transactionCode;

    @Schema(description = "Payment Gateway Provider Order ID", example = "order_rzp_123456")
    private String providerOrderId;

    @Schema(description = "Amount payable", example = "53098.82")
    private BigDecimal amount;

    @Schema(description = "Currency", example = "INR")
    private String currency;

    @Schema(description = "Selected Payment Provider", example = "RAZORPAY")
    private PaymentProvider paymentProvider;

    @Schema(description = "Payment Transaction Status", example = "INITIATED")
    private PaymentStatus paymentStatus;

    @Schema(description = "Current Onboarding Status", example = "PAYMENT_PENDING")
    private OnboardingStatus onboardingStatus;

    @Schema(description = "Next Expected Step in Pipeline", example = "PAYMENT")
    private OnboardingStep nextStep;

    @Schema(description = "Provider-specific client parameters (e.g. checkout tokens, order details)")
    private Map<String, Object> gatewayParameters;
}
