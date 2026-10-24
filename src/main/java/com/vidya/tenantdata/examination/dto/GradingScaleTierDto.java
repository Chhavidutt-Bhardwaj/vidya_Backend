package com.vidya.tenantdata.examination.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Grading scale tier representation")
public class GradingScaleTierDto {

    private UUID id;

    @NotBlank(message = "Grade label is required")
    @Schema(description = "Grade symbol", example = "A1")
    private String grade;

    @NotNull(message = "minPercentage is required")
    @DecimalMin("0.00")
    @DecimalMax("100.00")
    @Schema(description = "Minimum percentage boundary", example = "91.00")
    private BigDecimal minPercentage;

    @NotNull(message = "maxPercentage is required")
    @DecimalMin("0.00")
    @DecimalMax("100.00")
    @Schema(description = "Maximum percentage boundary", example = "100.00")
    private BigDecimal maxPercentage;

    @Schema(description = "Grade point equivalent", example = "10.0")
    private BigDecimal gradePoint;

    @Schema(description = "Descriptive remark", example = "Outstanding")
    private String description;

    @Schema(description = "Whether this grade is passing", example = "true")
    private boolean isPassing;

    @Schema(description = "Sorting order", example = "1")
    private int sortOrder;
}
