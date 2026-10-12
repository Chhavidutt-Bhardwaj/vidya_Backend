package com.vidya.tenantdata.student.repository;

import com.vidya.tenantdata.student.entity.StudentAcademicEnrollment;
import com.vidya.tenantdata.student.entity.StudentEnrollmentStatus;
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
public interface StudentAcademicEnrollmentRepository extends JpaRepository<StudentAcademicEnrollment, UUID> {

    @Query("SELECT e FROM StudentAcademicEnrollment e WHERE e.id = :id AND e.deletedAt IS NULL")
    Optional<StudentAcademicEnrollment> findByIdAndNotDeleted(@Param("id") UUID id);

    @Query("SELECT e FROM StudentAcademicEnrollment e WHERE e.enrollmentCode = :code AND e.deletedAt IS NULL")
    Optional<StudentAcademicEnrollment> findByEnrollmentCodeAndNotDeleted(@Param("code") String code);

    boolean existsByEnrollmentCode(String enrollmentCode);

    @Query("SELECT e FROM StudentAcademicEnrollment e WHERE e.student.id = :studentId AND e.deletedAt IS NULL ORDER BY e.enrollmentDate DESC")
    List<StudentAcademicEnrollment> findAllByStudentId(@Param("studentId") UUID studentId);

    @Query("SELECT e FROM StudentAcademicEnrollment e WHERE e.student.id = :studentId AND e.academicYear.id = :academicYearId AND e.enrollmentStatus IN ('ADMITTED', 'ACTIVE') AND e.deletedAt IS NULL")
    Optional<StudentAcademicEnrollment> findActiveEnrollment(@Param("studentId") UUID studentId, @Param("academicYearId") UUID academicYearId);

    @Query("SELECT COUNT(e) > 0 FROM StudentAcademicEnrollment e WHERE e.student.id = :studentId AND e.academicYear.id = :academicYearId AND e.enrollmentStatus IN ('ADMITTED', 'ACTIVE') AND e.deletedAt IS NULL")
    boolean existsActiveEnrollmentForStudentAndYear(@Param("studentId") UUID studentId, @Param("academicYearId") UUID academicYearId);

    @Query("SELECT COUNT(e) > 0 FROM StudentAcademicEnrollment e " +
           "WHERE e.academicYear.id = :academicYearId " +
           "AND e.academicClass.id = :academicClassId " +
           "AND ((:sectionId IS NULL AND e.section IS NULL) OR (e.section.id = :sectionId)) " +
           "AND LOWER(e.rollNumber) = LOWER(:rollNumber) " +
           "AND e.enrollmentStatus IN ('ADMITTED', 'ACTIVE') " +
           "AND e.deletedAt IS NULL " +
           "AND (:excludeId IS NULL OR e.id != :excludeId)")
    boolean existsActiveRollNumber(
            @Param("academicYearId") UUID academicYearId,
            @Param("academicClassId") UUID academicClassId,
            @Param("sectionId") UUID sectionId,
            @Param("rollNumber") String rollNumber,
            @Param("excludeId") UUID excludeId
    );

    @Query("SELECT COUNT(e) FROM StudentAcademicEnrollment e WHERE e.section.id = :sectionId AND e.enrollmentStatus IN ('ADMITTED', 'ACTIVE') AND e.deletedAt IS NULL")
    long countActiveEnrollmentsBySectionId(@Param("sectionId") UUID sectionId);

    @Query(value = "SELECT e FROM StudentAcademicEnrollment e " +
           "JOIN FETCH e.student s " +
           "JOIN FETCH e.academicYear y " +
           "JOIN FETCH e.academicClass c " +
           "LEFT JOIN FETCH e.section sec " +
           "WHERE (:studentId IS NULL OR e.student.id = :studentId) " +
           "AND (:academicYearId IS NULL OR e.academicYear.id = :academicYearId) " +
           "AND (:academicClassId IS NULL OR e.academicClass.id = :academicClassId) " +
           "AND (:sectionId IS NULL OR e.section.id = :sectionId) " +
           "AND (:status IS NULL OR e.enrollmentStatus = :status) " +
           "AND (CAST(:enrollmentCode AS string) IS NULL OR LOWER(e.enrollmentCode) LIKE LOWER(CONCAT('%', CAST(:enrollmentCode AS string), '%'))) " +
           "AND e.deletedAt IS NULL",
           countQuery = "SELECT COUNT(e) FROM StudentAcademicEnrollment e " +
           "WHERE (:studentId IS NULL OR e.student.id = :studentId) " +
           "AND (:academicYearId IS NULL OR e.academicYear.id = :academicYearId) " +
           "AND (:academicClassId IS NULL OR e.academicClass.id = :academicClassId) " +
           "AND (:sectionId IS NULL OR e.section.id = :sectionId) " +
           "AND (:status IS NULL OR e.enrollmentStatus = :status) " +
           "AND (CAST(:enrollmentCode AS string) IS NULL OR LOWER(e.enrollmentCode) LIKE LOWER(CONCAT('%', CAST(:enrollmentCode AS string), '%'))) " +
           "AND e.deletedAt IS NULL")
    Page<StudentAcademicEnrollment> searchEnrollments(
            @Param("studentId") UUID studentId,
            @Param("academicYearId") UUID academicYearId,
            @Param("academicClassId") UUID academicClassId,
            @Param("sectionId") UUID sectionId,
            @Param("status") StudentEnrollmentStatus status,
            @Param("enrollmentCode") String enrollmentCode,
            Pageable pageable
    );

    @Query(value = "SELECT e FROM StudentAcademicEnrollment e " +
           "JOIN FETCH e.student s " +
           "JOIN FETCH s.tenantUser u " +
           "LEFT JOIN FETCH u.profile p " +
           "JOIN FETCH e.academicClass c " +
           "LEFT JOIN FETCH e.section sec " +
           "WHERE e.academicClass.id = :classId " +
           "AND (:sectionId IS NULL OR e.section.id = :sectionId) " +
           "AND (:academicYearId IS NULL OR e.academicYear.id = :academicYearId) " +
           "AND (:status IS NULL OR e.enrollmentStatus = :status) " +
           "AND (CAST(:searchQuery AS string) IS NULL OR LOWER(s.admissionNumber) LIKE LOWER(CONCAT('%', CAST(:searchQuery AS string), '%')) " +
           "     OR LOWER(s.studentCode) LIKE LOWER(CONCAT('%', CAST(:searchQuery AS string), '%')) " +
           "     OR LOWER(e.rollNumber) LIKE LOWER(CONCAT('%', CAST(:searchQuery AS string), '%')) " +
           "     OR (p.firstName IS NOT NULL AND LOWER(p.firstName) LIKE LOWER(CONCAT('%', CAST(:searchQuery AS string), '%'))) " +
           "     OR (p.lastName IS NOT NULL AND LOWER(p.lastName) LIKE LOWER(CONCAT('%', CAST(:searchQuery AS string), '%')))) " +
           "AND e.deletedAt IS NULL",
           countQuery = "SELECT COUNT(e) FROM StudentAcademicEnrollment e " +
           "LEFT JOIN e.student s " +
           "LEFT JOIN s.tenantUser u " +
           "LEFT JOIN u.profile p " +
           "WHERE e.academicClass.id = :classId " +
           "AND (:sectionId IS NULL OR e.section.id = :sectionId) " +
           "AND (:academicYearId IS NULL OR e.academicYear.id = :academicYearId) " +
           "AND (:status IS NULL OR e.enrollmentStatus = :status) " +
           "AND (CAST(:searchQuery AS string) IS NULL OR LOWER(s.admissionNumber) LIKE LOWER(CONCAT('%', CAST(:searchQuery AS string), '%')) " +
           "     OR LOWER(s.studentCode) LIKE LOWER(CONCAT('%', CAST(:searchQuery AS string), '%')) " +
           "     OR LOWER(e.rollNumber) LIKE LOWER(CONCAT('%', CAST(:searchQuery AS string), '%')) " +
           "     OR (p.firstName IS NOT NULL AND LOWER(p.firstName) LIKE LOWER(CONCAT('%', CAST(:searchQuery AS string), '%'))) " +
           "     OR (p.lastName IS NOT NULL AND LOWER(p.lastName) LIKE LOWER(CONCAT('%', CAST(:searchQuery AS string), '%')))) " +
           "AND e.deletedAt IS NULL")
    Page<StudentAcademicEnrollment> searchClassSectionStudents(
            @Param("classId") UUID classId,
            @Param("sectionId") UUID sectionId,
            @Param("academicYearId") UUID academicYearId,
            @Param("status") StudentEnrollmentStatus status,
            @Param("searchQuery") String searchQuery,
            Pageable pageable
    );
}
