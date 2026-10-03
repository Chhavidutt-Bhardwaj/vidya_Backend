package com.vidya.master.community.repository;

import com.vidya.master.community.entity.CommunityMember;
import com.vidya.master.community.entity.CommunityMemberStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA Repository for {@link CommunityMember} entity.
 */
@Repository
public interface CommunityMemberRepository extends JpaRepository<CommunityMember, UUID> {

    Optional<CommunityMember> findByMemberCode(String memberCode);

    Optional<CommunityMember> findByCommunityIdAndPlatformUserId(UUID communityId, UUID platformUserId);

    boolean existsByCommunityIdAndPlatformUserId(UUID communityId, UUID platformUserId);

    List<CommunityMember> findByCommunityId(UUID communityId);

    List<CommunityMember> findByPlatformUserId(UUID platformUserId);

    Page<CommunityMember> findByCommunityId(UUID communityId, Pageable pageable);

    long countByCommunityIdAndStatus(UUID communityId, CommunityMemberStatus status);

    long countByCommunityId(UUID communityId);
}
