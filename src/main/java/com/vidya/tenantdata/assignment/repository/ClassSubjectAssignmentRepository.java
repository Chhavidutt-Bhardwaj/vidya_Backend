package com.vidya.tenantdata.assignment.repository;

import com.vidya.tenantdata.assignment.entity.ClassSubjectAssignment;
import com.vidya.tenantdata.assignment.enums.ClassSubjectAssignmentStatus;
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
public interface ClassSubjectAssignmentRepository extends JpaRepository<ClassSubjectAssignment, UUID> {

    @Query("SELECT csa FROM ClassSubjectAssignment csa WHERE csa.id = :id AND csa.deletedAt IS NULL")
    Optional<ClassSubjectAssignment> findByIdAndNotDeleted(@Param("id") UUID id);

    @Query("SELECT csa FROM ClassSubjectAssignment csa WHERE csa.assignmentCode = :assignmentCode AND csa.deletedAt IS NULL")
    Optional<ClassSubjectAssignment> findByAssignmentCodeAndNotDeleted(@Param("assignmentCode") String assignmentCode);

    boolean existsByAssignmentCode(String assignmentCode);

    @Query("SELECT COUNT(csa) > 0 FROM ClassSubjectAssignment csa " +
           "WHERE csa.academicYear.id = :academicYearId " +
           "AND csa.academicClass.id = :academicClassId " +
           "AND ((:sectionId IS NULL AND csa.section IS NULL) OR (csa.section.id = :sectionId)) " +
           "AND csa.subject.id = :subjectId " +
           "AND csa.deletedAt IS NULL " +
           "AND (:excludeId IS NULL OR csa.id <> :excludeId)")
    boolean existsActiveDuplicate(
            @Param("academicYearId") UUID academicYearId,
            @Param("academicClassId") UUID academicClassId,
            @Param("sectionId") UUID sectionId,
            @Param("subjectId") UUID subjectId,
            @Param("excludeId") UUID excludeId
    );

    @Query("SELECT csa FROM ClassSubjectAssignment csa " +
           "WHERE csa.deletedAt IS NULL " +
           "AND (:academicYearId IS NULL OR csa.academicYear.id = :academicYearId) " +
           "AND (:academicClassId IS NULL OR csa.academicClass.id = :academicClassId) " +
           "AND (:sectionId IS NULL OR csa.section.id = :sectionId) " +
           "AND (:subjectId IS NULL OR csa.subject.id = :subjectId) " +
           "AND (:status IS NULL OR csa.status = :status) " +
           "AND (:isCompulsory IS NULL OR csa.isCompulsory = :isCompulsory) " +
           "ORDER BY csa.displayOrder ASC, csa.createdAt ASC")
    Page<ClassSubjectAssignment> searchAssignments(
            @Param("academicYearId") UUID academicYearId,
            @Param("academicClassId") UUID academicClassId,
            @Param("sectionId") UUID sectionId,
            @Param("subjectId") UUID subjectId,
            @Param("status") ClassSubjectAssignmentStatus status,
            @Param("isCompulsory") Boolean isCompulsory,
            Pageable pageable
    );

    @Query("SELECT csa FROM ClassSubjectAssignment csa " +
           "WHERE csa.academicYear.id = :academicYearId " +
           "AND csa.academicClass.id = :academicClassId " +
           "AND ((:sectionId IS NULL AND csa.section IS NULL) OR (csa.section.id = :sectionId)) " +
           "AND csa.deletedAt IS NULL AND csa.status = 'ACTIVE' " +
           "ORDER BY csa.displayOrder ASC")
    List<ClassSubjectAssignment> findActiveByClassAndSection(
            @Param("academicYearId") UUID academicYearId,
            @Param("academicClassId") UUID academicClassId,
            @Param("sectionId") UUID sectionId
    );
}
