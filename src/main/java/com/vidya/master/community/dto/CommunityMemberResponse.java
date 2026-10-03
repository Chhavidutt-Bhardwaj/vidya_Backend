package com.vidya.master.community.dto;

import com.vidya.master.community.entity.CommunityMemberRole;
import com.vidya.master.community.entity.CommunityMemberStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Response payload representing a community membership record.
 */
public record CommunityMemberResponse(
        UUID id,
        UUID communityId,
        String communityCode,
        UUID platformUserId,
        String memberCode,
        CommunityMemberRole role,
        CommunityMemberStatus status,
        Instant joinedAt,
        Instant createdAt
) {}
