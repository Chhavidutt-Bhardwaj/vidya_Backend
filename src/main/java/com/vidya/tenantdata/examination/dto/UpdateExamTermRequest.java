package com.vidya.tenantdata.examination.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to update an exam term")
public class UpdateExamTermRequest {

    @Size(max = 100, message = "Name cannot exceed 100 characters")
    @Schema(description = "Updated name", example = "Term 1 (Half Yearly)")
    private String name;

    @Schema(description = "Updated start date", example = "2026-09-18")
    private LocalDate startDate;

    @Schema(description = "Updated end date", example = "2026-10-02")
    private LocalDate endDate;

    @DecimalMin("0.00")
    @DecimalMax("100.00")
    @Schema(description = "Updated weightage percentage", example = "35.00")
    private BigDecimal weightagePercentage;
}
