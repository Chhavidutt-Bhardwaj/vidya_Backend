package com.vidya.tenantdata.academic.repository;

import com.vidya.tenantdata.academic.entity.StudentAcademicAssignment;
import com.vidya.tenantdata.academic.enums.StudentAssignmentStatus;
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
public interface StudentAcademicAssignmentRepository extends JpaRepository<StudentAcademicAssignment, UUID> {

    @Query("SELECT a FROM StudentAcademicAssignment a WHERE a.id = :id AND a.deletedAt IS NULL")
    Optional<StudentAcademicAssignment> findByIdAndNotDeleted(@Param("id") UUID id);

    @Query("SELECT a FROM StudentAcademicAssignment a " +
           "WHERE a.student.id = :studentId " +
           "AND a.academicYear.id = :academicYearId " +
           "AND a.status = 'ACTIVE' " +
           "AND a.deletedAt IS NULL")
    Optional<StudentAcademicAssignment> findActiveAssignment(
            @Param("studentId") UUID studentId,
            @Param("academicYearId") UUID academicYearId
    );

    @Query("SELECT a FROM StudentAcademicAssignment a " +
           "WHERE a.student.id = :studentId " +
           "AND a.deletedAt IS NULL " +
           "ORDER BY a.startDate DESC")
    List<StudentAcademicAssignment> findAllByStudentId(@Param("studentId") UUID studentId);

    @Query("SELECT a FROM StudentAcademicAssignment a " +
           "WHERE a.deletedAt IS NULL " +
           "AND (:studentId IS NULL OR a.student.id = :studentId) " +
           "AND (:academicYearId IS NULL OR a.academicYear.id = :academicYearId) " +
           "AND (:academicClassId IS NULL OR a.academicClass.id = :academicClassId) " +
           "AND (:sectionId IS NULL OR a.section.id = :sectionId) " +
           "AND (:status IS NULL OR a.status = :status)")
    Page<StudentAcademicAssignment> searchAssignments(
            @Param("studentId") UUID studentId,
            @Param("academicYearId") UUID academicYearId,
            @Param("academicClassId") UUID academicClassId,
            @Param("sectionId") UUID sectionId,
            @Param("status") StudentAssignmentStatus status,
            Pageable pageable
    );

    @Query("SELECT COUNT(a) FROM StudentAcademicAssignment a " +
           "WHERE a.section.id = :sectionId " +
           "AND a.status = 'ACTIVE' " +
           "AND a.deletedAt IS NULL")
    long countActiveAssignmentsBySectionId(@Param("sectionId") UUID sectionId);

    boolean existsBySectionIdAndDeletedAtIsNull(UUID sectionId);

    boolean existsByAcademicClassIdAndDeletedAtIsNull(UUID academicClassId);

    boolean existsByAcademicYearIdAndDeletedAtIsNull(UUID academicYearId);

    @Query("SELECT a FROM StudentAcademicAssignment a " +
           "WHERE a.academicClass.id = :classId " +
           "AND (:sectionId IS NULL OR a.section.id = :sectionId) " +
           "AND a.status = 'ACTIVE' " +
           "AND a.deletedAt IS NULL")
    List<StudentAcademicAssignment> findActiveByClassAndSection(
            @Param("classId") UUID classId,
            @Param("sectionId") UUID sectionId
    );
}
