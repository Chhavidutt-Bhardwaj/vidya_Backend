package com.vidya.tenantdata.assignment.dto;

import com.vidya.tenantdata.assignment.enums.ClassTeacherAssignmentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request payload for transitioning the status of a ClassTeacherAssignment.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Payload to update the lifecycle status of a class teacher assignment")
public class UpdateClassTeacherAssignmentStatusRequest {

    @NotNull(message = "status is required")
    @Schema(description = "Target status for the transition", example = "COMPLETED")
    private ClassTeacherAssignmentStatus status;
}
