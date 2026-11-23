package com.vidya.master.workflow.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request to explicitly waive the onboarding payment (trial, offline contract, partner deal).
 * Requires {@code ONBOARDING_PAYMENT_WAIVE}; the approver is the authenticated user.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Authorized waiver of the onboarding payment")
public class PaymentWaiverRequest {

    @NotBlank(message = "A waiver reason is required")
    @Size(max = 1000, message = "Waiver reason must not exceed 1000 characters")
    @Schema(description = "Business justification for the waiver", example = "Pilot school — 6-month free trial approved by sales head")
    private String reason;

    @Size(max = 100, message = "Reference must not exceed 100 characters")
    @Schema(description = "Optional external reference (contract number, approval ticket)", example = "CONTRACT-2026-0142")
    private String reference;
}
