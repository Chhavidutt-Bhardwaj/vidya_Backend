package com.vidya.tenantdata.assignment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Request payload for creating a ClassSubjectAssignment.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Payload to deploy a subject to a class/section for an academic year")
public class CreateClassSubjectAssignmentRequest {

    @NotNull(message = "academicYearId is required")
    @Schema(description = "UUID of the AcademicYear in this tenant", example = "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11")
    private UUID academicYearId;

    @NotNull(message = "academicClassId is required")
    @Schema(description = "UUID of the AcademicClass in this tenant", example = "b1ffcd88-8b1a-3de7-aa5c-5aa8ac270b22")
    private UUID academicClassId;

    @Schema(description = "Optional UUID of the specific Section", example = "c2aacd77-7c2b-2ef6-bb4d-4bb7bd160c33")
    private UUID sectionId;

    @NotNull(message = "subjectId is required")
    @Schema(description = "UUID of the academic Subject in this tenant", example = "d3bbde66-6d3c-1fa5-cc3e-3cc6ce050d44")
    private UUID subjectId;

    @Schema(description = "Optional UUID of the associated Curriculum", example = "e4ccef55-5e4d-0eb4-dd2f-2dd5df940e55")
    private UUID curriculumId;

    @Schema(description = "Whether this subject is compulsory for this class/section", example = "true", defaultValue = "true")
    private Boolean isCompulsory;

    @Min(value = 0, message = "displayOrder cannot be negative")
    @Schema(description = "Display order for UI/reports", example = "1")
    private Integer displayOrder;

    @Min(value = 1, message = "periodsPerWeek must be at least 1")
    @Schema(description = "Number of scheduled periods per week (optional)", example = "6")
    private Integer periodsPerWeek;
}
