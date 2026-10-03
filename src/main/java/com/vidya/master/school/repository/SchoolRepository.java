package com.vidya.master.school.repository;

import com.vidya.master.school.entity.School;
import com.vidya.master.school.entity.SchoolBoard;
import com.vidya.master.school.entity.SchoolStatus;
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
 * Spring Data JPA Repository for {@link School} entity.
 */
@Repository
public interface SchoolRepository extends JpaRepository<School, UUID>, JpaSpecificationExecutor<School> {

    Optional<School> findBySchoolCode(String schoolCode);

    Optional<School> findByTenantId(UUID tenantId);

    boolean existsBySchoolCode(String schoolCode);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, UUID id);

    @Query("SELECT s FROM School s WHERE " +
            "(:status IS NULL OR s.status = :status) AND " +
            "(:board IS NULL OR s.board = :board) AND " +
            "(:search IS NULL OR LOWER(s.schoolName) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(s.schoolCode) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(s.email) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<School> findWithFilters(
            @Param("status") SchoolStatus status,
            @Param("board") SchoolBoard board,
            @Param("search") String search,
            Pageable pageable
    );
}
