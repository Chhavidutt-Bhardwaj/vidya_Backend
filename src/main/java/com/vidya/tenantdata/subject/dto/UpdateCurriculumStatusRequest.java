package com.vidya.tenantdata.subject.dto;

import com.vidya.tenantdata.subject.enums.CurriculumStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request payload for transitioning a Curriculum's lifecycle status.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Payload to update a curriculum's status")
public class UpdateCurriculumStatusRequest {

    @NotNull(message = "status is required")
    @Schema(description = "Target curriculum lifecycle status", example = "ACTIVE")
    private CurriculumStatus status;
}
