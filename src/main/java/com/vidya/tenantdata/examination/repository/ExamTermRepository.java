package com.vidya.tenantdata.examination.repository;

import com.vidya.tenantdata.examination.entity.ExamTerm;
import com.vidya.tenantdata.examination.enums.ExamTermStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExamTermRepository extends JpaRepository<ExamTerm, UUID> {

    @Query("SELECT e FROM ExamTerm e WHERE e.id = :id AND e.deletedAt IS NULL")
    Optional<ExamTerm> findActiveById(@Param("id") UUID id);

    @Query("SELECT e FROM ExamTerm e WHERE e.termCode = :code AND e.deletedAt IS NULL")
    Optional<ExamTerm> findActiveByCode(@Param("code") String code);

    @Query("SELECT e FROM ExamTerm e WHERE e.academicYear.id = :academicYearId AND e.deletedAt IS NULL ORDER BY e.startDate ASC")
    List<ExamTerm> findByAcademicYearId(@Param("academicYearId") UUID academicYearId);

    @Query("SELECT e FROM ExamTerm e WHERE e.deletedAt IS NULL " +
           "AND (:academicYearId IS NULL OR e.academicYear.id = :academicYearId) " +
           "AND (:status IS NULL OR e.status = :status)")
    Page<ExamTerm> searchTerms(@Param("academicYearId") UUID academicYearId,
                               @Param("status") ExamTermStatus status,
                               Pageable pageable);

    @Query("SELECT COUNT(e) > 0 FROM ExamTerm e WHERE e.academicYear.id = :academicYearId " +
           "AND LOWER(e.name) = LOWER(:name) AND e.deletedAt IS NULL")
    boolean existsByAcademicYearIdAndNameIgnoreCase(@Param("academicYearId") UUID academicYearId,
                                                    @Param("name") String name);
}
