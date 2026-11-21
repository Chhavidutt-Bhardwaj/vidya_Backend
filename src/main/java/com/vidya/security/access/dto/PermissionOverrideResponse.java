package com.vidya.security.access.dto;

import com.vidya.security.access.entity.UserPermissionOverride;

import java.time.Instant;
import java.util.UUID;

public record PermissionOverrideResponse(
        UUID id,
        UUID userId,
        String permissionCode,
        UserPermissionOverride.Effect effect,
        String reason,
        Instant validUntil,
        UUID createdBy,
        Instant updatedAt
) {
    public static PermissionOverrideResponse from(UserPermissionOverride o) {
        return new PermissionOverrideResponse(o.getId(), o.getUserId(), o.getPermission().getCode(), o.getEffect(),
                o.getReason(), o.getValidUntil(), o.getCreatedBy(), o.getUpdatedAt());
    }
}
