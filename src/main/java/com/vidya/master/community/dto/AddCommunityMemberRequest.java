package com.vidya.master.community.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request payload for adding a platform user to a community.
 *
 * <p>The {@code customRoleId} references a {@link com.vidya.master.community.entity.CommunityRoleDefinition}
 * that must belong to the same community. This replaces the previous hardcoded
 * {@link com.vidya.master.community.entity.CommunityMemberRole} enum field.
 */
public record AddCommunityMemberRequest(
        @NotNull(message = "Platform user ID is required")
        UUID platformUserId,

        /**
         * The custom role to assign to this member.
         * Must be an active role definition belonging to the target community.
         */
        @NotNull(message = "Custom role ID is required")
        UUID customRoleId
) {}
