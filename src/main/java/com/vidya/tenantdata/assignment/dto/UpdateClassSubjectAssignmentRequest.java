package com.vidya.tenantdata.assignment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request payload for updating a ClassSubjectAssignment.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Payload to update a class subject assignment")
public class UpdateClassSubjectAssignmentRequest {

    @Schema(description = "Whether this subject is compulsory", example = "true")
    private Boolean isCompulsory;

    @Min(value = 0, message = "displayOrder cannot be negative")
    @Schema(description = "Display order for UI/reports", example = "2")
    private Integer displayOrder;

    @Min(value = 1, message = "periodsPerWeek must be at least 1")
    @Schema(description = "Number of scheduled periods per week", example = "5")
    private Integer periodsPerWeek;
}
