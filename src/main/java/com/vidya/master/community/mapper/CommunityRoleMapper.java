package com.vidya.master.community.mapper;

import com.vidya.master.community.dto.CommunityRoleAuditResponse;
import com.vidya.master.community.dto.CommunityRoleResponse;
import com.vidya.master.community.entity.CommunityRoleAuditLog;
import com.vidya.master.community.entity.CommunityRoleDefinition;
import com.vidya.master.community.entity.CommunityRolePermission;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Maps between CommunityRole domain objects and DTOs.
 */
@Component
public class CommunityRoleMapper {

    /**
     * Converts a {@link CommunityRoleDefinition} to a {@link CommunityRoleResponse},
     * building the sectionCode → Set<actionCode> matrix from granted permission rows.
     */
    public CommunityRoleResponse toResponse(CommunityRoleDefinition role) {
        if (role == null) return null;

        Map<Integer, Set<Integer>> matrix = new HashMap<>();
        if (role.getPermissions() != null) {
            role.getPermissions().stream()
                    .filter(CommunityRolePermission::isGranted)
                    .forEach(p -> matrix
                            .computeIfAbsent(p.getSectionCode(), k -> new HashSet<>())
                            .add(p.getActionCode()));
        }

        return new CommunityRoleResponse(
                role.getId(),
                role.getRoleCode(),
                role.getRoleName(),
                role.getDescription(),
                role.getCommunityType(),
                role.isSystem(),
                role.isActive(),
                role.getLevel() != null ? role.getLevel() : 1,
                matrix,
                role.getCreatedAt(),
                role.getUpdatedAt()
        );
    }

    /** Converts a {@link CommunityRoleAuditLog} to a {@link CommunityRoleAuditResponse}. */
    public CommunityRoleAuditResponse toAuditResponse(CommunityRoleAuditLog log) {
        if (log == null) return null;
        return new CommunityRoleAuditResponse(
                log.getId(),
                log.getCommunity().getId(),
                log.getActorUserId(),
                log.getActorName(),
                log.getTargetMember() != null ? log.getTargetMember().getId() : null,
                log.getRoleDefinition() != null ? log.getRoleDefinition().getId() : null,
                log.getRoleNameSnapshot(),
                log.getAction(),
                log.getReason(),
                log.getPreviousRoleName(),
                log.getActorIpAddress(),
                log.getOccurredAt()
        );
    }
}
