package com.vidya.master.community.dto;

import com.vidya.master.community.entity.CommunityStatus;
import com.vidya.master.community.entity.CommunityType;

import java.time.Instant;
import java.util.UUID;

/**
 * Response payload representing the core identity of a community.
 * Section details (personal, address, bank, payroll, etc.) are retrieved
 * via dedicated section endpoints governed by RBAC.
 */
public record CommunityResponse(
        UUID id,
        String communityCode,
        String name,
        String description,
        CommunityType type,
        CommunityStatus status,
        Instant createdAt,
        Instant updatedAt,
        long memberCount
) {}
