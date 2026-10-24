package com.vidya.master.community.repository;

import com.vidya.master.community.entity.CommunityBankDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA Repository for {@link CommunityBankDetail} entity.
 */
@Repository
public interface CommunityBankDetailRepository extends JpaRepository<CommunityBankDetail, UUID> {

    List<CommunityBankDetail> findByCommunityId(UUID communityId);

    Optional<CommunityBankDetail> findByCommunityIdAndIsPrimaryTrue(UUID communityId);
}
