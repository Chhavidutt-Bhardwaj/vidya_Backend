package com.vidya.tenantdata.assignment.repository;

import com.vidya.tenantdata.assignment.entity.TeacherSubjectAssignment;
import com.vidya.tenantdata.assignment.enums.TeacherAssignmentStatus;
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
public interface TeacherSubjectAssignmentRepository extends JpaRepository<TeacherSubjectAssignment, UUID> {

    @Query("SELECT tsa FROM TeacherSubjectAssignment tsa WHERE tsa.id = :id AND tsa.deletedAt IS NULL")
    Optional<TeacherSubjectAssignment> findByIdAndNotDeleted(@Param("id") UUID id);

    @Query("SELECT tsa FROM TeacherSubjectAssignment tsa WHERE tsa.assignmentCode = :assignmentCode AND tsa.deletedAt IS NULL")
    Optional<TeacherSubjectAssignment> findByAssignmentCodeAndNotDeleted(@Param("assignmentCode") String assignmentCode);

    boolean existsByAssignmentCode(String assignmentCode);

    @Query("SELECT COUNT(tsa) > 0 FROM TeacherSubjectAssignment tsa " +
           "WHERE tsa.teacher.id = :teacherId " +
           "AND tsa.subject.id = :subjectId " +
           "AND tsa.academicYear.id = :academicYearId " +
           "AND tsa.academicClass.id = :academicClassId " +
           "AND ((:sectionId IS NULL AND tsa.section IS NULL) OR (tsa.section.id = :sectionId)) " +
           "AND tsa.deletedAt IS NULL " +
           "AND tsa.status IN ('DRAFT', 'ACTIVE') " +
           "AND (:excludeId IS NULL OR tsa.id <> :excludeId)")
    boolean existsActiveDuplicate(
            @Param("teacherId") UUID teacherId,
            @Param("subjectId") UUID subjectId,
            @Param("academicYearId") UUID academicYearId,
            @Param("academicClassId") UUID academicClassId,
            @Param("sectionId") UUID sectionId,
            @Param("excludeId") UUID excludeId
    );

    @Query("SELECT COUNT(tsa) > 0 FROM TeacherSubjectAssignment tsa " +
           "WHERE tsa.subject.id = :subjectId " +
           "AND tsa.academicYear.id = :academicYearId " +
           "AND tsa.academicClass.id = :academicClassId " +
           "AND ((:sectionId IS NULL AND tsa.section IS NULL) OR (tsa.section.id = :sectionId)) " +
           "AND tsa.isPrimary = true " +
           "AND tsa.status = 'ACTIVE' " +
           "AND tsa.deletedAt IS NULL " +
           "AND (:excludeId IS NULL OR tsa.id <> :excludeId)")
    boolean existsActivePrimary(
            @Param("subjectId") UUID subjectId,
            @Param("academicYearId") UUID academicYearId,
            @Param("academicClassId") UUID academicClassId,
            @Param("sectionId") UUID sectionId,
            @Param("excludeId") UUID excludeId
    );

    @Query("SELECT tsa FROM TeacherSubjectAssignment tsa " +
           "WHERE tsa.deletedAt IS NULL " +
           "AND (:teacherId IS NULL OR tsa.teacher.id = :teacherId) " +
           "AND (:subjectId IS NULL OR tsa.subject.id = :subjectId) " +
           "AND (:academicYearId IS NULL OR tsa.academicYear.id = :academicYearId) " +
           "AND (:academicClassId IS NULL OR tsa.academicClass.id = :academicClassId) " +
           "AND (:sectionId IS NULL OR tsa.section.id = :sectionId) " +
           "AND (:status IS NULL OR tsa.status = :status) " +
           "AND (:isPrimary IS NULL OR tsa.isPrimary = :isPrimary)")
    Page<TeacherSubjectAssignment> searchAssignments(
            @Param("teacherId") UUID teacherId,
            @Param("subjectId") UUID subjectId,
            @Param("academicYearId") UUID academicYearId,
            @Param("academicClassId") UUID academicClassId,
            @Param("sectionId") UUID sectionId,
            @Param("status") TeacherAssignmentStatus status,
            @Param("isPrimary") Boolean isPrimary,
            Pageable pageable
    );

    @Query("SELECT tsa FROM TeacherSubjectAssignment tsa " +
           "WHERE tsa.teacher.id = :teacherId " +
           "AND (:academicYearId IS NULL OR tsa.academicYear.id = :academicYearId) " +
           "AND tsa.deletedAt IS NULL AND tsa.status = 'ACTIVE'")
    List<TeacherSubjectAssignment> findActiveByTeacherId(
            @Param("teacherId") UUID teacherId,
            @Param("academicYearId") UUID academicYearId
    );

    @Query("SELECT tsa FROM TeacherSubjectAssignment tsa " +
           "WHERE tsa.academicYear.id = :academicYearId " +
           "AND tsa.academicClass.id = :academicClassId " +
           "AND ((:sectionId IS NULL AND tsa.section IS NULL) OR (tsa.section.id = :sectionId)) " +
           "AND tsa.deletedAt IS NULL AND tsa.status = 'ACTIVE'")
    List<TeacherSubjectAssignment> findActiveByClassAndSection(
            @Param("academicYearId") UUID academicYearId,
            @Param("academicClassId") UUID academicClassId,
            @Param("sectionId") UUID sectionId
    );

    @Query("SELECT COUNT(tsa) > 0 FROM TeacherSubjectAssignment tsa " +
           "WHERE tsa.teacher.id = :teacherId " +
           "AND tsa.subject.id = :subjectId " +
           "AND tsa.academicYear.id = :academicYearId " +
           "AND tsa.deletedAt IS NULL AND tsa.status = 'ACTIVE'")
    boolean isTeacherQualifiedForSubject(
            @Param("teacherId") UUID teacherId,
            @Param("subjectId") UUID subjectId,
            @Param("academicYearId") UUID academicYearId
    );
}
