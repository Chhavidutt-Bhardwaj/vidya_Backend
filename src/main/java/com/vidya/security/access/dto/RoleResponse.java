package com.vidya.security.access.dto;

import com.vidya.security.access.AccessScope;
import com.vidya.security.access.entity.AccessPermission;
import com.vidya.security.access.entity.AccessRole;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RoleResponse(
        UUID id,
        String code,
        String name,
        String description,
        AccessScope scope,
        UUID schoolId,
        boolean system,
        boolean grantsAllPermissions,
        boolean active,
        List<String> permissionCodes,
        Instant updatedAt
) {
    public static RoleResponse from(AccessRole r, boolean includePermissions) {
        List<String> codes = includePermissions
                ? r.getPermissions().stream().map(AccessPermission::getCode).sorted().toList()
                : null;
        return new RoleResponse(r.getId(), r.getCode(), r.getName(), r.getDescription(), r.getScope(),
                r.getSchoolId(), r.isSystem(), r.isGrantsAllPermissions(), r.isActive(), codes, r.getUpdatedAt());
    }
}
