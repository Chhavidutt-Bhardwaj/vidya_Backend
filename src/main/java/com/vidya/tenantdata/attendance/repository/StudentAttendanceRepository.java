package com.vidya.tenantdata.attendance.repository;

import com.vidya.tenantdata.attendance.entity.StudentAttendance;
import com.vidya.tenantdata.attendance.enums.AttendanceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Repository for StudentAttendance tenant entities.
 *
 * <p>All queries are tenant-scoped by virtue of the {@code TenantRoutingDataSource}.
 * Soft-deleted records (where {@code deletedAt IS NOT NULL}) are excluded from active queries.
 */
@Repository
public interface StudentAttendanceRepository extends JpaRepository<StudentAttendance, UUID> {

    @Query("SELECT sa FROM StudentAttendance sa WHERE sa.id = :id AND sa.deletedAt IS NULL")
    Optional<StudentAttendance> findByIdAndNotDeleted(@Param("id") UUID id);

    @Query("SELECT sa FROM StudentAttendance sa WHERE sa.attendanceCode = :code AND sa.deletedAt IS NULL")
    Optional<StudentAttendance> findByAttendanceCodeAndNotDeleted(@Param("code") String code);

    boolean existsByAttendanceCode(String attendanceCode);

    /**
     * Find an existing active attendance record for a student on a specific date in an academic year.
     * Used for duplicate detection and update-or-reject semantics.
     */
    @Query("SELECT sa FROM StudentAttendance sa " +
           "WHERE sa.student.id = :studentId " +
           "AND sa.academicYear.id = :academicYearId " +
           "AND sa.attendanceDate = :attendanceDate " +
           "AND sa.deletedAt IS NULL")
    Optional<StudentAttendance> findActiveByStudentAndYearAndDate(
            @Param("studentId") UUID studentId,
            @Param("academicYearId") UUID academicYearId,
            @Param("attendanceDate") LocalDate attendanceDate
    );

    /**
     * Check existence (for duplicate guard at app layer — DB partial unique index provides final guarantee).
     */
    @Query("SELECT COUNT(sa) > 0 FROM StudentAttendance sa " +
           "WHERE sa.student.id = :studentId " +
           "AND sa.academicYear.id = :academicYearId " +
           "AND sa.attendanceDate = :attendanceDate " +
           "AND sa.deletedAt IS NULL " +
           "AND (:excludeId IS NULL OR sa.id <> :excludeId)")
    boolean existsActiveByStudentAndYearAndDate(
            @Param("studentId") UUID studentId,
            @Param("academicYearId") UUID academicYearId,
            @Param("attendanceDate") LocalDate attendanceDate,
            @Param("excludeId") UUID excludeId
    );

    /**
     * Student attendance history filtered by academic year and optional date range / status.
     */
    @Query("SELECT sa FROM StudentAttendance sa " +
           "WHERE sa.student.id = :studentId " +
           "AND sa.deletedAt IS NULL " +
           "AND (:academicYearId IS NULL OR sa.academicYear.id = :academicYearId) " +
           "AND (:fromDate IS NULL OR sa.attendanceDate >= :fromDate) " +
           "AND (:toDate IS NULL OR sa.attendanceDate <= :toDate) " +
           "AND (:status IS NULL OR sa.status = :status)")
    Page<StudentAttendance> findStudentAttendanceHistory(
            @Param("studentId") UUID studentId,
            @Param("academicYearId") UUID academicYearId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("status") AttendanceStatus status,
            Pageable pageable
    );

    /**
     * All attendance records for a section on a specific date (daily attendance sheet).
     */
    @Query("SELECT sa FROM StudentAttendance sa " +
           "WHERE sa.section.id = :sectionId " +
           "AND sa.attendanceDate = :attendanceDate " +
           "AND sa.deletedAt IS NULL " +
           "ORDER BY sa.student.id")
    List<StudentAttendance> findBySectionAndDate(
            @Param("sectionId") UUID sectionId,
            @Param("attendanceDate") LocalDate attendanceDate
    );

    /**
     * Attendance records for a section over a date range (section attendance history).
     */
    @Query("SELECT sa FROM StudentAttendance sa " +
           "WHERE sa.section.id = :sectionId " +
           "AND sa.attendanceDate >= :fromDate " +
           "AND sa.attendanceDate <= :toDate " +
           "AND sa.deletedAt IS NULL " +
           "ORDER BY sa.attendanceDate DESC, sa.student.id")
    Page<StudentAttendance> findBySectionAndDateRange(
            @Param("sectionId") UUID sectionId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            Pageable pageable
    );

    /**
     * Attendance records for an academic year / section / specific date (supports daily view via yearId+sectionId+date).
     */
    @Query("SELECT sa FROM StudentAttendance sa " +
           "WHERE sa.academicYear.id = :academicYearId " +
           "AND sa.section.id = :sectionId " +
           "AND sa.attendanceDate = :attendanceDate " +
           "AND sa.deletedAt IS NULL " +
           "ORDER BY sa.student.id")
    List<StudentAttendance> findByYearAndSectionAndDate(
            @Param("academicYearId") UUID academicYearId,
            @Param("sectionId") UUID sectionId,
            @Param("attendanceDate") LocalDate attendanceDate
    );

    /**
     * Count attendance records for a student in an academic year, grouped by status —
     * used for attendance summary calculation.
     */
    @Query("SELECT COUNT(sa) FROM StudentAttendance sa " +
           "WHERE sa.student.id = :studentId " +
           "AND sa.academicYear.id = :academicYearId " +
           "AND sa.status = :status " +
           "AND sa.deletedAt IS NULL")
    long countByStudentAndYearAndStatus(
            @Param("studentId") UUID studentId,
            @Param("academicYearId") UUID academicYearId,
            @Param("status") AttendanceStatus status
    );

    /**
     * Total non-deleted attendance records for a student in an academic year.
     */
    @Query("SELECT COUNT(sa) FROM StudentAttendance sa " +
           "WHERE sa.student.id = :studentId " +
           "AND sa.academicYear.id = :academicYearId " +
           "AND sa.deletedAt IS NULL")
    long countTotalByStudentAndYear(
            @Param("studentId") UUID studentId,
            @Param("academicYearId") UUID academicYearId
    );

    /**
     * IDs of students who have attendance records on a given date in a section (used for bulk idempotency).
     */
    @Query("SELECT sa.student.id FROM StudentAttendance sa " +
           "WHERE sa.section.id = :sectionId " +
           "AND sa.academicYear.id = :academicYearId " +
           "AND sa.attendanceDate = :attendanceDate " +
           "AND sa.deletedAt IS NULL")
    List<UUID> findStudentIdsWithAttendanceOnDate(
            @Param("sectionId") UUID sectionId,
            @Param("academicYearId") UUID academicYearId,
            @Param("attendanceDate") LocalDate attendanceDate
    );

    /**
     * All active attendance records for a student in an academic year.
     */
    @Query("SELECT sa FROM StudentAttendance sa " +
           "WHERE sa.student.id = :studentId " +
           "AND sa.academicYear.id = :academicYearId " +
           "AND sa.deletedAt IS NULL " +
           "ORDER BY sa.attendanceDate ASC")
    List<StudentAttendance> findAllActiveByStudentAndYear(
            @Param("studentId") UUID studentId,
            @Param("academicYearId") UUID academicYearId
    );

    /**
     * Set of dates with at least one active attendance record for a section in a date range.
     */
    @Query("SELECT DISTINCT sa.attendanceDate FROM StudentAttendance sa " +
           "WHERE sa.section.id = :sectionId " +
           "AND sa.academicYear.id = :academicYearId " +
           "AND sa.attendanceDate >= :fromDate " +
           "AND sa.attendanceDate <= :toDate " +
           "AND sa.deletedAt IS NULL")
    Set<LocalDate> findDistinctAttendanceDatesBySectionAndYearAndRange(
            @Param("sectionId") UUID sectionId,
            @Param("academicYearId") UUID academicYearId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate
    );

    @Query("SELECT COUNT(sa) FROM StudentAttendance sa " +
           "WHERE sa.student.id = :studentId " +
           "AND sa.attendanceDate >= :startDate " +
           "AND sa.attendanceDate <= :endDate " +
           "AND sa.deletedAt IS NULL")
    long countTotalByStudentAndDateRange(
            @Param("studentId") UUID studentId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("SELECT COUNT(sa) FROM StudentAttendance sa " +
           "WHERE sa.student.id = :studentId " +
           "AND sa.attendanceDate >= :startDate " +
           "AND sa.attendanceDate <= :endDate " +
           "AND (sa.status = 'PRESENT' OR sa.status = 'HALF_DAY') " +
           "AND sa.deletedAt IS NULL")
    long countPresentByStudentAndDateRange(
            @Param("studentId") UUID studentId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );
}
