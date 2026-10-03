package com.vidya.master.community.dto;

import com.vidya.master.community.entity.CommunityMemberRole;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request payload for adding a platform user to a community.
 */
public record AddCommunityMemberRequest(
        @NotNull(message = "Platform user ID is required")
        UUID platformUserId,

        @NotNull(message = "Member role is required")
        CommunityMemberRole role
) {}
