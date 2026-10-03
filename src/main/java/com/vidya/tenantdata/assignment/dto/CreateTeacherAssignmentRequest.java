package com.vidya.tenantdata.assignment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Request payload for assigning a teacher to a subject for a class/section.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Payload to create a teacher subject assignment")
public class CreateTeacherAssignmentRequest {

    @NotNull(message = "teacherId is required")
    @Schema(description = "UUID of the Staff member (teacher) in this tenant", example = "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11")
    private UUID teacherId;

    @NotNull(message = "subjectId is required")
    @Schema(description = "UUID of the academic Subject in this tenant", example = "b1ffcd88-8b1a-3de7-aa5c-5aa8ac270b22")
    private UUID subjectId;

    @NotNull(message = "academicYearId is required")
    @Schema(description = "UUID of the AcademicYear in this tenant", example = "c2aacd77-7c2b-2ef6-bb4d-4bb7bd160c33")
    private UUID academicYearId;

    @NotNull(message = "academicClassId is required")
    @Schema(description = "UUID of the AcademicClass in this tenant", example = "d3bbde66-6d3c-1fa5-cc3e-3cc6ce050d44")
    private UUID academicClassId;

    @Schema(description = "Optional UUID of the specific Section", example = "e4ccef55-5e4d-0eb4-dd2f-2dd5df940e55")
    private UUID sectionId;

    @Schema(description = "Optional UUID of the associated Curriculum", example = "f5ddfa44-4f5e-9fc3-ee1a-1ee4ea830f66")
    private UUID curriculumId;

    @Schema(description = "Whether this is the primary teacher for the subject", example = "true", defaultValue = "true")
    private Boolean isPrimary;

    @Schema(description = "Assignment start date", example = "2026-04-01")
    private LocalDate startDate;

    @Schema(description = "Assignment end date (optional)", example = "2027-03-31")
    private LocalDate endDate;
}
