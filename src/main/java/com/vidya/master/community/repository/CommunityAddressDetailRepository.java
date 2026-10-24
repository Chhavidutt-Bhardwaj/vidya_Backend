package com.vidya.master.community.repository;

import com.vidya.master.community.entity.CommunityAddressDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/** Repository for {@link CommunityAddressDetail}. */
@Repository
public interface CommunityAddressDetailRepository extends JpaRepository<CommunityAddressDetail, UUID> {
    Optional<CommunityAddressDetail> findByCommunityId(UUID communityId);
    boolean existsByCommunityId(UUID communityId);
}
