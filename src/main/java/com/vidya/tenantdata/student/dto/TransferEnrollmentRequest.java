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
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request payload to transfer a student to a new class or section within the academic year")
public class TransferEnrollmentRequest {

    @NotNull(message = "New academic class ID is required")
    @Schema(description = "Target academic class UUID")
    private UUID newAcademicClassId;

    @Schema(description = "Target section UUID (optional if class has no sections)")
    private UUID newSectionId;

    @Size(max = 50, message = "Roll number cannot exceed 50 characters")
    @Schema(description = "New roll number in the destination section", example = "22")
    private String newRollNumber;

    @Schema(description = "Date of transfer (defaults to today)", example = "2026-10-15")
    private LocalDate transferDate;

    @Size(max = 1000, message = "Remarks cannot exceed 1000 characters")
    @Schema(description = "Reason and remarks for transfer", example = "Section rebalancing based on second language choice")
    private String remarks;
}
