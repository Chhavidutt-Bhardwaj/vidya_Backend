package com.vidya.tenantdata.subject.repository;

import com.vidya.tenantdata.subject.entity.Curriculum;
import com.vidya.tenantdata.subject.enums.CurriculumStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CurriculumRepository extends JpaRepository<Curriculum, UUID> {

    @Query("SELECT c FROM Curriculum c WHERE c.id = :id AND c.deletedAt IS NULL")
    Optional<Curriculum> findByIdAndNotDeleted(@Param("id") UUID id);

    @Query("SELECT c FROM Curriculum c WHERE c.curriculumCode = :curriculumCode AND c.deletedAt IS NULL")
    Optional<Curriculum> findByCurriculumCodeAndNotDeleted(@Param("curriculumCode") String curriculumCode);

    boolean existsByCurriculumCode(String curriculumCode);

    @Query("SELECT c FROM Curriculum c WHERE c.deletedAt IS NULL " +
           "AND (:academicYearId IS NULL OR c.academicYear.id = :academicYearId) " +
           "AND (:status IS NULL OR c.status = :status) " +
           "AND (:query IS NULL OR :query = '' OR " +
           "     LOWER(c.name) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "     LOWER(c.curriculumCode) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<Curriculum> searchCurricula(
            @Param("academicYearId") UUID academicYearId,
            @Param("status") CurriculumStatus status,
            @Param("query") String query,
            Pageable pageable
    );
}
