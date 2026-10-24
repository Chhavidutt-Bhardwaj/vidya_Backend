package com.vidya.master.community.repository;

import com.vidya.master.community.entity.CommunityRoleAuditLog;
import com.vidya.master.community.entity.CommunityRoleAuditLog.RoleAuditAction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository for {@link CommunityRoleAuditLog} — immutable audit trail of
 * all role lifecycle events (creation, permission changes, member assignments).
 */
@Repository
public interface CommunityRoleAuditLogRepository extends JpaRepository<CommunityRoleAuditLog, UUID> {

    Page<CommunityRoleAuditLog> findByCommunityId(UUID communityId, Pageable pageable);

    Page<CommunityRoleAuditLog> findByCommunityIdAndAction(UUID communityId, RoleAuditAction action, Pageable pageable);

    Page<CommunityRoleAuditLog> findByTargetMemberId(UUID memberId, Pageable pageable);

    Page<CommunityRoleAuditLog> findByRoleDefinitionId(UUID roleDefinitionId, Pageable pageable);

    Page<CommunityRoleAuditLog> findByActorUserId(UUID actorUserId, Pageable pageable);
}
