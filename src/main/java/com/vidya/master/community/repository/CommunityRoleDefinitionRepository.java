package com.vidya.master.community.repository;

import com.vidya.master.community.entity.CommunityRoleDefinition;
import com.vidya.master.community.entity.CommunityType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA Repository for {@link CommunityRoleDefinition} entity.
 */
@Repository
public interface CommunityRoleDefinitionRepository extends JpaRepository<CommunityRoleDefinition, UUID> {

    Optional<CommunityRoleDefinition> findByRoleCode(String roleCode);

    List<CommunityRoleDefinition> findByCommunityType(CommunityType communityType);

    List<CommunityRoleDefinition> findByCommunityTypeAndIsActiveTrue(CommunityType communityType);

    boolean existsByRoleCode(String roleCode);

    boolean existsByRoleNameAndCommunityType(String roleName, CommunityType communityType);
}
