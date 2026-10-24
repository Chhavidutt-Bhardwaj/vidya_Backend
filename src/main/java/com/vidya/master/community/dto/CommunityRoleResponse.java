package com.vidya.master.community.dto;

import com.vidya.master.community.entity.CommunityType;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Response payload representing a community role definition with its full permission matrix.
 *
 * @param permissionsMatrix Map of sectionCode -> Set of granted actionCodes.
 *                          Frontend uses this map to conditionally render modules/tabs and action buttons.
 */
public record CommunityRoleResponse(
        UUID id,
        String roleCode,
        String roleName,
        String description,
        CommunityType communityType,
        boolean isSystem,
        boolean isActive,
        int level,
        Map<Integer, Set<Integer>> permissionsMatrix,
        Instant createdAt,
        Instant updatedAt
) {}
