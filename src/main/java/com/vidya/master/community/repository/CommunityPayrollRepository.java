package com.vidya.master.community.repository;

import com.vidya.master.community.entity.CommunityPayroll;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA Repository for {@link CommunityPayroll} entity.
 */
@Repository
public interface CommunityPayrollRepository extends JpaRepository<CommunityPayroll, UUID> {

    List<CommunityPayroll> findByCommunityId(UUID communityId);

    Optional<CommunityPayroll> findByCommunityIdAndIsActiveTrue(UUID communityId);
}
