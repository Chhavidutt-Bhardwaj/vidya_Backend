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
            "(:status IS NULL OR c.status = :status) AND " +
            "(:type IS NULL OR c.type = :type) AND " +
            "(:search IS NULL OR LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(c.communityCode) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(c.city) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Community> findWithFilters(
            @Param("status") CommunityStatus status,
            @Param("type") CommunityType type,
            @Param("search") String search,
            Pageable pageable
    );
}
