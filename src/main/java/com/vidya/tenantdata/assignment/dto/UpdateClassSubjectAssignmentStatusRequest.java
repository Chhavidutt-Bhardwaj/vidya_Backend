package com.vidya.tenantdata.assignment.dto;

import com.vidya.tenantdata.assignment.enums.ClassSubjectAssignmentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request payload for transitioning a ClassSubjectAssignment status.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Payload to update a class subject assignment's status")
public class UpdateClassSubjectAssignmentStatusRequest {

    @NotNull(message = "status is required")
    @Schema(description = "Target class subject assignment status", example = "INACTIVE")
    private ClassSubjectAssignmentStatus status;
}
