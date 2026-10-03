package com.vidya.master.community.dto;

import com.vidya.master.community.entity.CommunityStatus;
import com.vidya.master.community.entity.CommunityType;

import java.time.Instant;
import java.util.UUID;

/**
 * Response payload representing a community.
 */
public record CommunityResponse(
        UUID id,
        String communityCode,
        String name,
        String description,
        CommunityType type,
        CommunityStatus status,
        String contactEmail,
        String contactPhone,
        String addressLine1,
        String addressLine2,
        String city,
        String state,
        String pincode,
        String country,
        Instant createdAt,
        Instant updatedAt,
        long memberCount
) {}
