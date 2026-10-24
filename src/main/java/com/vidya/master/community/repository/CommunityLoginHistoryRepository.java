package com.vidya.master.community.repository;

import com.vidya.master.community.entity.CommunityLoginHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA Repository for {@link CommunityLoginHistory} entity.
 */
@Repository
public interface CommunityLoginHistoryRepository extends JpaRepository<CommunityLoginHistory, UUID> {

    List<CommunityLoginHistory> findByCommunityIdOrderByLoginTimeDesc(UUID communityId);

    List<CommunityLoginHistory> findByPlatformUserIdOrderByLoginTimeDesc(UUID platformUserId);

    Page<CommunityLoginHistory> findByCommunityIdOrderByLoginTimeDesc(UUID communityId, Pageable pageable);

    Page<CommunityLoginHistory> findByPlatformUserIdOrderByLoginTimeDesc(UUID platformUserId, Pageable pageable);

    List<CommunityLoginHistory> findByCommunityIdAndLoginTimeAfter(UUID communityId, Instant since);
}
