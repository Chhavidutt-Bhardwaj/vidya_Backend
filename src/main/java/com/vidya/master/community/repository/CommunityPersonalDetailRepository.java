package com.vidya.master.community.repository;

import com.vidya.master.community.entity.CommunityPersonalDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA Repository for {@link CommunityPersonalDetail} entity.
 */
@Repository
public interface CommunityPersonalDetailRepository extends JpaRepository<CommunityPersonalDetail, UUID> {

    Optional<CommunityPersonalDetail> findByCommunityId(UUID communityId);

    boolean existsByCommunityId(UUID communityId);
}
