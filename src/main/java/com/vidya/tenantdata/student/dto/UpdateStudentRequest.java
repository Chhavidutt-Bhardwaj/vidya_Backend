package com.vidya.tenantdata.student.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Request payload for updating Student academic/admission details.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Payload to update student details")
public class UpdateStudentRequest {

    @Size(max = 100, message = "admissionNumber cannot exceed 100 characters")
    @Schema(description = "Updated school admission number", example = "ADM-2026-0042")
    private String admissionNumber;

    @PastOrPresent(message = "admissionDate cannot be in the future")
    @Schema(description = "Updated date of admission", example = "2026-06-01")
    private LocalDate admissionDate;
}
