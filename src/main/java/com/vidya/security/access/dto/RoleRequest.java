package com.vidya.security.access.dto;

import com.vidya.security.access.AccessScope;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * Create or update a role. On update, {@code code} and {@code scope} are ignored (immutable) and
 * {@code permissionCodes}, when non-null, replaces the role's grants.
 */
public record RoleRequest(
        @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "code must be UPPER_SNAKE_CASE")
        @Size(max = 50) String code,
        @NotBlank @Size(max = 100) String name,
        String description,
        AccessScope scope,
        Boolean active,
        Set<String> permissionCodes
) {}
