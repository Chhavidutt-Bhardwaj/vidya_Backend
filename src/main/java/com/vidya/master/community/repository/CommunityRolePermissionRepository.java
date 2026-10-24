package com.vidya.master.community.repository;

import com.vidya.master.community.entity.CommunityRolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CommunityRolePermissionRepository extends JpaRepository<CommunityRolePermission, UUID> {
    List<CommunityRolePermission> findByRoleDefinitionId(UUID roleDefinitionId);
    void deleteByRoleDefinitionId(UUID roleDefinitionId);
}
