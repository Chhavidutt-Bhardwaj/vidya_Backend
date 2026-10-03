package com.vidya.tenantdata.assignment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Request payload for updating an existing TeacherSubjectAssignment.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Payload to update a teacher subject assignment")
public class UpdateTeacherAssignmentRequest {

    @Schema(description = "Whether this is the primary teacher for the subject", example = "true")
    private Boolean isPrimary;

    @Schema(description = "Assignment start date", example = "2026-04-01")
    private LocalDate startDate;

    @Schema(description = "Assignment end date", example = "2027-03-31")
    private LocalDate endDate;
}
