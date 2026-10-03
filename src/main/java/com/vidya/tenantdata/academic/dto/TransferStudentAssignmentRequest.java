package com.vidya.tenantdata.academic.dto;

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
@Schema(description = "Payload to transfer an existing student assignment to a new section/class")
public class TransferStudentAssignmentRequest {

    @NotNull(message = "newAcademicClassId is required")
    @Schema(description = "UUID of target class")
    private UUID newAcademicClassId;

    @NotNull(message = "newSectionId is required")
    @Schema(description = "UUID of target section")
    private UUID newSectionId;

    @Size(max = 50, message = "newRollNumber cannot exceed 50 characters")
    @Schema(description = "New roll number in destination section", example = "15")
    private String newRollNumber;

    @Schema(description = "Effective transfer date (defaults to today)", example = "2026-07-01")
    private LocalDate transferDate;
}
