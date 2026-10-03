package com.vidya.master.payment.dto;

import com.vidya.master.payment.entity.PaymentMethod;
import com.vidya.master.payment.entity.PaymentProvider;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InitiatePaymentRequest {

    @NotNull(message = "School ID is required")
    private UUID schoolId;

    private UUID subscriptionId;

    private UUID onboardingId;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    private BigDecimal amount;

    private String currency;

    @NotNull(message = "Payment provider is required")
    private PaymentProvider paymentProvider;

    private PaymentMethod paymentMethod;

    @NotBlank(message = "Idempotency key is required")
    private String idempotencyKey;

    private String customerName;

    private String customerEmail;

    private String customerPhone;

    private Map<String, String> notes;
}
