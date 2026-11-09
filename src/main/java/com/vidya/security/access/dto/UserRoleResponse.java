package com.vidya.security.access.dto;

import com.vidya.security.access.AccessScope;
import com.vidya.security.access.entity.UserRoleAssignment;

import java.time.Instant;
import java.util.UUID;

/**
 * A role held by a user. {@code assignmentId == null} marks the account-type role that comes from
 * {@code platform_user.platform_role}; it cannot be removed through the assignment APIs.
 */
public record UserRoleResponse(
        UUID assignmentId,
        UUID roleId,
        String roleCode,
        String roleName,
        AccessScope scope,
        UUID roleSchoolId,
        boolean primary,
        boolean active,
        boolean effective,
        Instant validFrom,
        Instant validUntil,
        String notes,
        UUID assignedBy,
        Instant assignedAt
) {
    public static UserRoleResponse from(UserRoleAssignment a, Instant now) {
        return new UserRoleResponse(a.getId(), a.getRole().getId(), a.getRole().getCode(), a.getRole().getName(),
                a.getRole().getScope(), a.getRole().getSchoolId(), false, a.isActive(), a.isEffective(now),
                a.getValidFrom(), a.getValidUntil(), a.getNotes(), a.getAssignedBy(), a.getCreatedAt());
    }
}
