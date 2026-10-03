package com.vidya.tenantdata.academic.repository;

import com.vidya.tenantdata.academic.entity.Section;
import com.vidya.tenantdata.academic.enums.SectionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SectionRepository extends JpaRepository<Section, UUID> {

    boolean existsByAcademicYearIdAndAcademicClassIdAndCode(UUID academicYearId, UUID academicClassId, String code);

    @Query("SELECT s FROM Section s WHERE s.id = :id AND s.deletedAt IS NULL")
    Optional<Section> findByIdAndNotDeleted(@Param("id") UUID id);

    @Query("SELECT s FROM Section s WHERE s.deletedAt IS NULL " +
           "AND (:academicYearId IS NULL OR s.academicYear.id = :academicYearId) " +
           "AND (:academicClassId IS NULL OR s.academicClass.id = :academicClassId) " +
           "AND (:status IS NULL OR s.status = :status)")
    Page<Section> searchSections(
            @Param("academicYearId") UUID academicYearId,
            @Param("academicClassId") UUID academicClassId,
            @Param("status") SectionStatus status,
            Pageable pageable
    );

    boolean existsByAcademicClassIdAndDeletedAtIsNull(UUID academicClassId);

    boolean existsByAcademicYearIdAndDeletedAtIsNull(UUID academicYearId);
}
