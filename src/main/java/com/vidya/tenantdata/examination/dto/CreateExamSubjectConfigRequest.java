package com.vidya.tenantdata.examination.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to configure a subject in an exam schedule")
public class CreateExamSubjectConfigRequest {

    @NotNull(message = "subjectId is required")
    @Schema(description = "Subject UUID")
    private UUID subjectId;

    @NotNull(message = "examDate is required")
    @Schema(description = "Exam date", example = "2026-09-20")
    private LocalDate examDate;

    @NotNull(message = "startTime is required")
    @Schema(description = "Exam start time", example = "09:00:00")
    private LocalTime startTime;

    @NotNull(message = "endTime is required")
    @Schema(description = "Exam end time", example = "12:00:00")
    private LocalTime endTime;

    @NotNull(message = "maxMarks is required")
    @DecimalMin("0.01")
    @Schema(description = "Maximum marks", example = "100.00")
    private BigDecimal maxMarks;

    @NotNull(message = "passMarks is required")
    @DecimalMin("0.00")
    @Schema(description = "Passing marks threshold", example = "35.00")
    private BigDecimal passMarks;

    @Schema(description = "Theory maximum marks", example = "80.00")
    private BigDecimal theoryMaxMarks;

    @Schema(description = "Practical maximum marks", example = "20.00")
    private BigDecimal practicalMaxMarks;

    @Schema(description = "Optional room UUID")
    private UUID roomId;
}
