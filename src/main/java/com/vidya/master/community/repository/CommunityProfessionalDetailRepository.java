package com.vidya.master.community.repository;

import com.vidya.master.community.entity.CommunityProfessionalDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA Repository for {@link CommunityProfessionalDetail} entity.
 */
@Repository
public interface CommunityProfessionalDetailRepository extends JpaRepository<CommunityProfessionalDetail, UUID> {

    Optional<CommunityProfessionalDetail> findByCommunityId(UUID communityId);

    boolean existsByCommunityId(UUID communityId);
}
