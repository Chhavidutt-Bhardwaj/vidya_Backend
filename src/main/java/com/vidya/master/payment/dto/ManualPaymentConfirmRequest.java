package com.vidya.master.payment.dto;

import com.vidya.master.payment.entity.PaymentMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManualPaymentConfirmRequest {

    @NotBlank(message = "Transaction code is required")
    private String transactionCode;

    @NotNull(message = "Payment method is required")
    private PaymentMethod paymentMethod;

    @NotBlank(message = "Payment reference/UTR/Cheque number is required")
    private String referenceNumber;

    private String confirmedBy;

    private String notes;
}
