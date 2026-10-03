package com.vidya.tenantdata.assignment.repository;

import com.vidya.tenantdata.assignment.entity.ClassTeacherAssignment;
import com.vidya.tenantdata.assignment.enums.ClassTeacherAssignmentStatus;
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
 * Repository for ClassTeacherAssignment tenant entities.
 *
 * <p>All queries are tenant-scoped by virtue of the {@code TenantRoutingDataSource}.
 * Soft-deleted records (where {@code deletedAt IS NOT NULL}) are excluded from active queries.
 */
@Repository
public interface ClassTeacherAssignmentRepository extends JpaRepository<ClassTeacherAssignment, UUID> {

    @Query("SELECT cta FROM ClassTeacherAssignment cta WHERE cta.id = :id AND cta.deletedAt IS NULL")
    Optional<ClassTeacherAssignment> findByIdAndNotDeleted(@Param("id") UUID id);

    @Query("SELECT cta FROM ClassTeacherAssignment cta WHERE cta.assignmentCode = :assignmentCode AND cta.deletedAt IS NULL")
    Optional<ClassTeacherAssignment> findByAssignmentCodeAndNotDeleted(@Param("assignmentCode") String assignmentCode);

    boolean existsByAssignmentCode(String assignmentCode);

    /**
     * Check if an ACTIVE class teacher assignment exists for this (academicYear, section),
     * optionally excluding a specific record (used for update idempotency checks).
     */
    @Query("SELECT COUNT(cta) > 0 FROM ClassTeacherAssignment cta " +
           "WHERE cta.academicYear.id = :academicYearId " +
           "AND cta.section.id = :sectionId " +
           "AND cta.status = 'ACTIVE' " +
           "AND cta.deletedAt IS NULL " +
           "AND (:excludeId IS NULL OR cta.id <> :excludeId)")
    boolean existsActiveBySectionAndAcademicYear(
            @Param("academicYearId") UUID academicYearId,
            @Param("sectionId") UUID sectionId,
            @Param("excludeId") UUID excludeId
    );

    /**
     * Find the current ACTIVE class teacher for the given section and academic year.
     */
    @Query("SELECT cta FROM ClassTeacherAssignment cta " +
           "WHERE cta.academicYear.id = :academicYearId " +
           "AND cta.section.id = :sectionId " +
           "AND cta.status = 'ACTIVE' " +
           "AND cta.deletedAt IS NULL")
    Optional<ClassTeacherAssignment> findActiveBySectionAndAcademicYear(
            @Param("academicYearId") UUID academicYearId,
            @Param("sectionId") UUID sectionId
    );

    /**
     * Find all (history + active) assignments for the given section and academic year.
     */
    @Query("SELECT cta FROM ClassTeacherAssignment cta " +
           "WHERE cta.academicYear.id = :academicYearId " +
           "AND cta.section.id = :sectionId " +
           "AND cta.deletedAt IS NULL " +
           "ORDER BY cta.createdAt DESC")
    List<ClassTeacherAssignment> findAllBySectionAndAcademicYear(
            @Param("academicYearId") UUID academicYearId,
            @Param("sectionId") UUID sectionId
    );

    /**
     * Find all active assignments for a given staff member, optionally filtered by academic year.
     */
    @Query("SELECT cta FROM ClassTeacherAssignment cta " +
           "WHERE cta.staff.id = :staffId " +
           "AND (:academicYearId IS NULL OR cta.academicYear.id = :academicYearId) " +
           "AND cta.status = 'ACTIVE' " +
           "AND cta.deletedAt IS NULL")
    List<ClassTeacherAssignment> findActiveByStaffId(
            @Param("staffId") UUID staffId,
            @Param("academicYearId") UUID academicYearId
    );

    /**
     * Find all (not deleted) assignments for a given academic year.
     */
    @Query("SELECT cta FROM ClassTeacherAssignment cta " +
           "WHERE cta.academicYear.id = :academicYearId " +
           "AND cta.deletedAt IS NULL")
    List<ClassTeacherAssignment> findAllByAcademicYear(@Param("academicYearId") UUID academicYearId);

    /**
     * Paginated search with optional filters.
     */
    @Query("SELECT cta FROM ClassTeacherAssignment cta " +
           "WHERE cta.deletedAt IS NULL " +
           "AND (:staffId IS NULL OR cta.staff.id = :staffId) " +
           "AND (:academicYearId IS NULL OR cta.academicYear.id = :academicYearId) " +
           "AND (:academicClassId IS NULL OR cta.academicClass.id = :academicClassId) " +
           "AND (:sectionId IS NULL OR cta.section.id = :sectionId) " +
           "AND (:status IS NULL OR cta.status = :status)")
    Page<ClassTeacherAssignment> searchAssignments(
            @Param("staffId") UUID staffId,
            @Param("academicYearId") UUID academicYearId,
            @Param("academicClassId") UUID academicClassId,
            @Param("sectionId") UUID sectionId,
            @Param("status") ClassTeacherAssignmentStatus status,
            Pageable pageable
    );
}
