package com.vidya.master.community.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request payload for updating a community member's role.
 * Uses custom role ID to reference CommunityRoleDefinition.
 */
public record UpdateCommunityMemberRoleRequest(
        @NotNull(message = "Custom role ID is required")
        UUID customRoleId
) {}
