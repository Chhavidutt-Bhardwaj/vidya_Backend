package com.vidya.tenantdata.user.dto;

import com.vidya.tenantdata.user.entity.TenantUserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request payload to change tenant user status")
public class UpdateTenantUserStatusRequest {

    @NotNull(message = "Target status is required")
    @Schema(description = "Target status (ACTIVE, INACTIVE, SUSPENDED, LOCKED, DELETED)", example = "SUSPENDED")
    private TenantUserStatus status;

    @Schema(description = "Optional reason for status change", example = "Temporary leave of absence")
    private String reason;
}
