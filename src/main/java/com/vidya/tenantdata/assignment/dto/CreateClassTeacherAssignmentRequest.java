package com.vidya.tenantdata.assignment.dto;

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

/**
 * Request payload for assigning a teacher as class/section teacher for a given section.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Payload to create a class teacher assignment (section teacher)")
public class CreateClassTeacherAssignmentRequest {

    @NotNull(message = "staffId is required")
    @Schema(description = "UUID of the Staff member (teacher) in this tenant", example = "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11")
    private UUID staffId;

    @NotNull(message = "academicYearId is required")
    @Schema(description = "UUID of the AcademicYear in this tenant", example = "c2aacd77-7c2b-2ef6-bb4d-4bb7bd160c33")
    private UUID academicYearId;

    @NotNull(message = "academicClassId is required")
    @Schema(description = "UUID of the AcademicClass in this tenant", example = "d3bbde66-6d3c-1fa5-cc3e-3cc6ce050d44")
    private UUID academicClassId;

    @NotNull(message = "sectionId is required")
    @Schema(description = "UUID of the Section in this tenant", example = "e4ccef55-5e4d-0eb4-dd2f-2dd5df940e55")
    private UUID sectionId;

    @Schema(description = "Assignment start date", example = "2026-04-01")
    private LocalDate startDate;

    @Size(max = 1000, message = "notes must not exceed 1000 characters")
    @Schema(description = "Optional notes about this assignment", example = "Assigned as homeroom teacher for 2026-27 academic year")
    private String notes;
}
