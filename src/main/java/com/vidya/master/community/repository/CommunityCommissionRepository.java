package com.vidya.master.community.repository;

import com.vidya.master.community.entity.CommunityCommission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA Repository for {@link CommunityCommission} entity.
 */
@Repository
public interface CommunityCommissionRepository extends JpaRepository<CommunityCommission, UUID> {

    List<CommunityCommission> findByCommunityId(UUID communityId);

    List<CommunityCommission> findByCommunityIdAndIsActiveTrue(UUID communityId);
}
