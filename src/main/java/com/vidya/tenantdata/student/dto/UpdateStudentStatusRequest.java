package com.vidya.tenantdata.student.dto;

import com.vidya.tenantdata.student.entity.StudentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request payload for transitioning Student status.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Payload to change student lifecycle status")
public class UpdateStudentStatusRequest {

    @NotNull(message = "status is required")
    @Schema(description = "Target lifecycle status", example = "SUSPENDED")
    private StudentStatus status;

    @Schema(description = "Optional reason or remarks for status change", example = "Transferred to another branch")
    private String reason;
}
