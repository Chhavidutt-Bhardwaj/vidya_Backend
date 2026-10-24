package com.vidya.tenantdata.examination.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to create an exam term")
public class CreateExamTermRequest {

    @NotNull(message = "academicYearId is required")
    @Schema(description = "Academic year UUID")
    private UUID academicYearId;

    @NotBlank(message = "Exam term name is required")
    @Size(max = 100, message = "Name cannot exceed 100 characters")
    @Schema(description = "Exam term name", example = "Term 1 / Mid-Term Examinations")
    private String name;

    @NotNull(message = "startDate is required")
    @Schema(description = "Term start date", example = "2026-09-15")
    private LocalDate startDate;

    @NotNull(message = "endDate is required")
    @Schema(description = "Term end date", example = "2026-09-30")
    private LocalDate endDate;

    @DecimalMin("0.00")
    @DecimalMax("100.00")
    @Schema(description = "Term weightage percentage towards final score", example = "30.00")
    private BigDecimal weightagePercentage;
}
