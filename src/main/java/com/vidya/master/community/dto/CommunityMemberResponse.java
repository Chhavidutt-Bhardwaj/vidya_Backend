package com.vidya.master.community.dto;

import com.vidya.master.community.entity.CommunityMemberStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Response payload representing a community membership record.
 * The effective role name is derived from the assigned {@link com.vidya.master.community.entity.CommunityRoleDefinition}.
 */
public record CommunityMemberResponse(
        UUID id,
        UUID communityId,
        String communityCode,
        UUID platformUserId,
        String memberCode,
        UUID customRoleId,
        String roleName,
        CommunityMemberStatus status,
        Instant joinedAt,
        Instant lastActiveAt,
        Instant createdAt
) {}
