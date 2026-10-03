package com.vidya.master.community.dto;

import com.vidya.master.community.entity.CommunityMemberRole;
import jakarta.validation.constraints.NotNull;

/**
 * Request payload for updating a community member's role.
 */
public record UpdateCommunityMemberRoleRequest(
        @NotNull(message = "Member role is required")
        CommunityMemberRole role
) {}
