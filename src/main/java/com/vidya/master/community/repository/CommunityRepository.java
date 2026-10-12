package com.vidya.master.community.repository;

import com.vidya.master.community.entity.Community;
import com.vidya.master.community.entity.CommunityStatus;
import com.vidya.master.community.entity.CommunityType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA Repository for {@link Community} entity.
 */
@Repository
public interface CommunityRepository extends JpaRepository<Community, UUID>, JpaSpecificationExecutor<Community> {

    Optional<Community> findByCommunityCode(String communityCode);

    boolean existsByCommunityCode(String communityCode);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);

    Page<Community> findByStatus(CommunityStatus status, Pageable pageable);

    Page<Community> findByType(CommunityType type, Pageable pageable);

    @Query("SELECT c FROM Community c WHERE " +
            "(CAST(:status AS string) IS NULL OR c.status = :status) AND " +
            "(CAST(:type AS string) IS NULL OR c.type = :type) AND " +
            "(CAST(:search AS string) IS NULL OR LOWER(c.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) OR LOWER(c.communityCode) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) OR LOWER(c.city) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))")
    Page<Community> findWithFilters(
            @Param("status") CommunityStatus status,
            @Param("type") CommunityType type,
            @Param("search") String search,
            Pageable pageable
    );
}
