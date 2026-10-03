package com.vidya.tenantdata.student.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Request payload to update an academic enrollment")
public class UpdateEnrollmentRequest {

    @Schema(description = "Section UUID")
    private UUID sectionId;

    @Size(max = 50, message = "Roll number cannot exceed 50 characters")
    @Schema(description = "Updated roll number", example = "16")
    private String rollNumber;

    @Schema(description = "Updated enrollment date", example = "2026-06-01")
    private LocalDate enrollmentDate;

    @Size(max = 1000, message = "Remarks cannot exceed 1000 characters")
    @Schema(description = "Updated remarks", example = "Updated section allocation")
    private String remarks;
}
