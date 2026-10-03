package com.vidya.master.onboarding.repository;

import com.vidya.master.onboarding.entity.OnboardingSource;
import com.vidya.master.onboarding.entity.OnboardingStatus;
import com.vidya.master.onboarding.entity.SchoolOnboarding;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA Repository for {@link SchoolOnboarding} entity.
 */
@Repository
public interface SchoolOnboardingRepository extends JpaRepository<SchoolOnboarding, UUID> {

    Optional<SchoolOnboarding> findByOnboardingCode(String onboardingCode);

    Optional<SchoolOnboarding> findBySchoolId(UUID schoolId);

    Page<SchoolOnboarding> findByStatus(OnboardingStatus status, Pageable pageable);

    List<SchoolOnboarding> findByCommunityId(UUID communityId);

    Page<SchoolOnboarding> findByCommunityId(UUID communityId, Pageable pageable);

    Page<SchoolOnboarding> findBySource(OnboardingSource source, Pageable pageable);

    @Query("SELECT o FROM SchoolOnboarding o WHERE " +
            "(:status IS NULL OR o.status = :status) AND " +
            "(:source IS NULL OR o.source = :source) AND " +
            "(:communityId IS NULL OR o.communityId = :communityId)")
    Page<SchoolOnboarding> findWithFilters(
            @Param("status") OnboardingStatus status,
            @Param("source") OnboardingSource source,
            @Param("communityId") UUID communityId,
            Pageable pageable
    );

    @Query("SELECT CASE WHEN COUNT(o) > 0 THEN true ELSE false END " +
            "FROM SchoolOnboarding o, CommunityMember cm " +
            "WHERE o.communityId = cm.community.id " +
            "AND cm.platformUserId = :platformUserId " +
            "AND o.school.id = :schoolId")
    boolean existsByPlatformUserIdAndSchoolId(@Param("platformUserId") UUID platformUserId, @Param("schoolId") UUID schoolId);
}
