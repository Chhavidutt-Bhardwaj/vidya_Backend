package com.vidya.tenantdata.staff.dto;

import com.vidya.tenantdata.staff.enums.EmploymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request payload for explicitly transitioning a Staff member's employment status.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Payload to transition staff employment status")
public class UpdateStaffStatusRequest {

    @NotNull(message = "status is required")
    @Schema(description = "Target employment status", example = "SUSPENDED")
    private EmploymentStatus status;

    @Schema(description = "Optional reason or remarks for the status change", example = "Under investigation")
    private String reason;
}
