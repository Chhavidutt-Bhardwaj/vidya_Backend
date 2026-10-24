package com.vidya.tenantdata.examination.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
@Schema(description = "Request to submit or update mark for a single student")
public class SubmitStudentMarkRequest {

    @NotNull(message = "studentId is required")
    @Schema(description = "Student UUID")
    private UUID studentId;

    @DecimalMin("0.00")
    @Schema(description = "Total marks scored", example = "88.50")
    private BigDecimal marksObtained;

    @DecimalMin("0.00")
    @Schema(description = "Theory marks scored", example = "70.50")
    private BigDecimal theoryMarksObtained;

    @DecimalMin("0.00")
    @Schema(description = "Practical marks scored", example = "18.00")
    private BigDecimal practicalMarksObtained;

    @Schema(description = "Flag if student was absent for exam", example = "false")
    private boolean isAbsent;

    @Schema(description = "Flag if student is officially exempt", example = "false")
    private boolean isExempt;

    @Size(max = 255, message = "Remarks cannot exceed 255 characters")
    @Schema(description = "Evaluator remarks", example = "Excellent grasp of core concepts")
    private String remarks;
}
