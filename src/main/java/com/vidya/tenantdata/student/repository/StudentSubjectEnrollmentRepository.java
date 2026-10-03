package com.vidya.tenantdata.student.repository;

import com.vidya.tenantdata.student.entity.StudentSubjectEnrollment;
import com.vidya.tenantdata.student.entity.StudentSubjectEnrollmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StudentSubjectEnrollmentRepository extends JpaRepository<StudentSubjectEnrollment, UUID> {

    @Query("SELECT s FROM StudentSubjectEnrollment s WHERE s.id = :id AND s.deletedAt IS NULL")
    Optional<StudentSubjectEnrollment> findByIdAndNotDeleted(@Param("id") UUID id);

    @Query("SELECT s FROM StudentSubjectEnrollment s WHERE s.subjectEnrollmentCode = :code AND s.deletedAt IS NULL")
    Optional<StudentSubjectEnrollment> findBySubjectEnrollmentCodeAndNotDeleted(@Param("code") String code);

    boolean existsBySubjectEnrollmentCode(String subjectEnrollmentCode);

    @Query("SELECT s FROM StudentSubjectEnrollment s " +
           "JOIN FETCH s.subject sub " +
           "LEFT JOIN FETCH s.classSubjectAssignment csa " +
           "WHERE s.enrollment.id = :enrollmentId AND s.deletedAt IS NULL " +
           "ORDER BY sub.name ASC")
    List<StudentSubjectEnrollment> findAllByEnrollmentId(@Param("enrollmentId") UUID enrollmentId);

    @Query("SELECT s FROM StudentSubjectEnrollment s " +
           "WHERE s.enrollment.id = :enrollmentId AND s.subject.id = :subjectId AND s.status = 'ACTIVE' AND s.deletedAt IS NULL")
    Optional<StudentSubjectEnrollment> findActiveByEnrollmentIdAndSubjectId(
            @Param("enrollmentId") UUID enrollmentId,
            @Param("subjectId") UUID subjectId
    );

    @Query("SELECT COUNT(s) > 0 FROM StudentSubjectEnrollment s " +
           "WHERE s.enrollment.id = :enrollmentId AND s.subject.id = :subjectId AND s.status = 'ACTIVE' AND s.deletedAt IS NULL")
    boolean existsActiveByEnrollmentIdAndSubjectId(
            @Param("enrollmentId") UUID enrollmentId,
            @Param("subjectId") UUID subjectId
    );
}
