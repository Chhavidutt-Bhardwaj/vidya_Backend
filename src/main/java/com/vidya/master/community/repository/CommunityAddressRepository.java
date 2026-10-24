package com.vidya.master.community.repository;

import com.vidya.master.community.entity.CommunityAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA Repository for {@link CommunityAddress} entity.
 */
@Repository
public interface CommunityAddressRepository extends JpaRepository<CommunityAddress, UUID> {

    List<CommunityAddress> findByCommunityId(UUID communityId);

    Optional<CommunityAddress> findByCommunityIdAndIsPrimaryTrue(UUID communityId);

    List<CommunityAddress> findByStateIgnoreCase(String state);
}
