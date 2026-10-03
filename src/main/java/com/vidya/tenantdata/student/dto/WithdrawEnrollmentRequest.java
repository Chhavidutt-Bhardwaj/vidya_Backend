package com.vidya.tenantdata.student.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request payload to withdraw a student from an academic enrollment")
public class WithdrawEnrollmentRequest {

    @NotNull(message = "Withdrawal date is required")
    @Schema(description = "Date of withdrawal", example = "2026-10-15")
    private LocalDate withdrawalDate;

    @Size(max = 255, message = "Withdrawal reason cannot exceed 255 characters")
    @Schema(description = "Reason for student withdrawal", example = "Relocating to another city")
    private String withdrawalReason;

    @Size(max = 1000, message = "Remarks cannot exceed 1000 characters")
    @Schema(description = "Administrative remarks", example = "Transfer certificate issued")
    private String remarks;
}
