package com.vidya.security.access.dto;

import com.vidya.security.access.entity.UserPermissionOverride;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record PermissionOverrideRequest(
        @NotBlank String permissionCode,
        @NotNull UserPermissionOverride.Effect effect,
        String reason,
        Instant validUntil
) {}
