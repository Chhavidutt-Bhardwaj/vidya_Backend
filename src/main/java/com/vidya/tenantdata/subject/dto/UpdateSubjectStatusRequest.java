package com.vidya.tenantdata.subject.dto;

import com.vidya.tenantdata.subject.enums.SubjectStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request payload for transitioning an academic Subject's lifecycle status.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Payload to update a subject's status")
public class UpdateSubjectStatusRequest {

    @NotNull(message = "status is required")
    @Schema(description = "Target subject lifecycle status", example = "INACTIVE")
    private SubjectStatus status;
}
