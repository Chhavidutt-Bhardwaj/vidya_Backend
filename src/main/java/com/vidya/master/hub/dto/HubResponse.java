package com.vidya.master.hub.dto;

import com.vidya.master.hub.entity.HubStatus;

import java.time.Instant;
import java.util.UUID;

public record HubResponse(
        UUID id,
        String hubCode,
        String name,
        String state,
        String region,
        String description,
        HubStatus status,
        UUID leadCommunityId,
        String leadCommunityName,
        UUID hubManagerUserId,
        String hubManagerName,
        UUID hubSubManagerUserId,
        String hubSubManagerName,
        String contactEmail,
        String contactPhone,
        long communityCount,
        long schoolCount,
        Instant createdAt,
        Instant updatedAt
) {}
