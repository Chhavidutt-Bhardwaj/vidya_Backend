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
@Schema(description = "Request to enroll a student in an individual subject")
public class CreateStudentSubjectEnrollmentRequest {

    @NotNull(message = "Subject ID is required")
    @Schema(description = "Subject UUID")
    private UUID subjectId;

    @Schema(description = "Class subject assignment UUID (optional)")
    private UUID classSubjectAssignmentId;

    @Schema(description = "Effective start date (defaults to today)", example = "2026-06-01")
    private LocalDate effectiveFrom;

    @Size(max = 1000, message = "Remarks cannot exceed 1000 characters")
    @Schema(description = "Remarks", example = "Elected French as second language")
    private String remarks;
}
