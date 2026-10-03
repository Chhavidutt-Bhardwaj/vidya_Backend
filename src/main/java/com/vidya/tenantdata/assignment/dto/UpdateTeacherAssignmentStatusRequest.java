package com.vidya.tenantdata.assignment.dto;

import com.vidya.tenantdata.assignment.enums.TeacherAssignmentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request payload for transitioning a TeacherSubjectAssignment status.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Payload to update a teacher assignment's status")
public class UpdateTeacherAssignmentStatusRequest {

    @NotNull(message = "status is required")
    @Schema(description = "Target teacher assignment status", example = "ACTIVE")
    private TeacherAssignmentStatus status;
}
