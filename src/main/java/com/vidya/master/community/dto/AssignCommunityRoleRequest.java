package com.vidya.master.community.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request to assign a community role to a community member.
 * Submitted by an authorised platform user (PLATFORM_SUPER_ADMIN or COMMUNITY_ROLE_MANAGER).
 * The actor's identity and IP are captured from the security context — not from this payload.
 */
public record AssignCommunityRoleRequest(
        @NotNull(message = "Member ID is required")
        UUID memberId,

        @NotNull(message = "Role definition ID is required")
        UUID roleDefinitionId,

        /** Optional reason for this role assignment/change (saved to audit log). */
        String reason
) {}
