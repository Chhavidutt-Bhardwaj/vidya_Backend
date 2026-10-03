package com.vidya.tenantdata.assignment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Request payload for updating mutable fields of a ClassTeacherAssignment.
 *
 * <p>Immutable fields (assignmentCode, staffId, academicYearId, academicClassId, sectionId) cannot be changed.
 * To change the assigned teacher, use the replace endpoint instead.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Payload to update mutable fields of a class teacher assignment")
public class UpdateClassTeacherAssignmentRequest {

    @Schema(description = "Updated start date", example = "2026-04-15")
    private LocalDate startDate;

    @Schema(description = "Updated end date", example = "2027-03-31")
    private LocalDate endDate;

    @Size(max = 1000, message = "notes must not exceed 1000 characters")
    @Schema(description = "Updated notes", example = "Updated: confirmed for full year")
    private String notes;
}
