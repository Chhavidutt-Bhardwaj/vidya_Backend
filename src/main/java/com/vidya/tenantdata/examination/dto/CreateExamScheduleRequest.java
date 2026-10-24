package com.vidya.tenantdata.examination.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to create an exam schedule")
public class CreateExamScheduleRequest {

    @NotNull(message = "examTermId is required")
    @Schema(description = "Exam term UUID")
    private UUID examTermId;

    @NotNull(message = "academicClassId is required")
    @Schema(description = "Academic class / grade level UUID")
    private UUID academicClassId;

    @Schema(description = "Optional grading scale UUID (falls back to default scale)")
    private UUID gradingScaleId;

    @NotBlank(message = "Exam schedule name is required")
    @Size(max = 100, message = "Name cannot exceed 100 characters")
    @Schema(description = "Schedule name", example = "Class 10 Mid-Term Schedule")
    private String name;
}
