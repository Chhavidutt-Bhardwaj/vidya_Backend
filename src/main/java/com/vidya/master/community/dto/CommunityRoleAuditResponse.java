package com.vidya.master.community.dto;

import com.vidya.master.community.entity.CommunityRoleAuditLog.RoleAuditAction;

import java.time.Instant;
import java.util.UUID;

/**
 * Response payload representing a single role audit log entry.
 * Returned by {@code GET /api/v1/communities/{id}/roles/audit-log}.
 */
public record CommunityRoleAuditResponse(
        UUID id,
        UUID communityId,
        UUID actorUserId,
        String actorName,
        UUID targetMemberId,
        UUID roleDefinitionId,
        String roleNameSnapshot,
        RoleAuditAction action,
        String reason,
        String previousRoleName,
        String actorIpAddress,
        Instant occurredAt
) {}
