package com.vidya.tenantdata.student.repository;

import com.vidya.tenantdata.student.entity.Student;
import com.vidya.tenantdata.student.entity.StudentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface StudentRepository extends JpaRepository<Student, UUID> {

    Optional<Student> findByStudentCode(String studentCode);

    Optional<Student> findByAdmissionNumber(String admissionNumber);

    Optional<Student> findByTenantUserId(UUID tenantUserId);

    boolean existsByStudentCode(String studentCode);

    boolean existsByAdmissionNumber(String admissionNumber);

    boolean existsByTenantUserId(UUID tenantUserId);

    @Query("SELECT s FROM Student s WHERE s.id = :id AND s.deletedAt IS NULL")
    Optional<Student> findByIdAndNotDeleted(@Param("id") UUID id);

    @Query("SELECT s FROM Student s WHERE s.studentCode = :studentCode AND s.deletedAt IS NULL")
    Optional<Student> findByStudentCodeAndNotDeleted(@Param("studentCode") String studentCode);

    @Query("SELECT s FROM Student s WHERE s.admissionNumber = :admissionNumber AND s.deletedAt IS NULL")
    Optional<Student> findByAdmissionNumberAndNotDeleted(@Param("admissionNumber") String admissionNumber);

    @Query("SELECT s FROM Student s LEFT JOIN s.tenantUser u LEFT JOIN u.profile p " +
           "WHERE s.deletedAt IS NULL " +
           "AND (:status IS NULL OR s.status = :status) " +
           "AND (:admissionNumber IS NULL OR LOWER(s.admissionNumber) LIKE LOWER(CONCAT('%', :admissionNumber, '%'))) " +
           "AND (:studentCode IS NULL OR LOWER(s.studentCode) LIKE LOWER(CONCAT('%', :studentCode, '%'))) " +
           "AND (:searchQuery IS NULL OR LOWER(s.admissionNumber) LIKE LOWER(CONCAT('%', :searchQuery, '%')) " +
           "     OR LOWER(s.studentCode) LIKE LOWER(CONCAT('%', :searchQuery, '%')) " +
           "     OR (p.firstName IS NOT NULL AND LOWER(p.firstName) LIKE LOWER(CONCAT('%', :searchQuery, '%'))) " +
           "     OR (p.lastName IS NOT NULL AND LOWER(p.lastName) LIKE LOWER(CONCAT('%', :searchQuery, '%'))))")
    Page<Student> searchStudents(
            @Param("status") StudentStatus status,
            @Param("admissionNumber") String admissionNumber,
            @Param("studentCode") String studentCode,
            @Param("searchQuery") String searchQuery,
            Pageable pageable
    );
}
