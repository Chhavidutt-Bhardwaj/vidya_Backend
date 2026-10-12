package com.vidya.tenantdata.attendance.service;

import com.vidya.tenantdata.attendance.dto.AttendanceDetailResponse;
import com.vidya.tenantdata.attendance.dto.AttendanceResponse;
import com.vidya.tenantdata.attendance.dto.AttendanceSummaryResponse;
import com.vidya.tenantdata.attendance.dto.BulkAttendanceRequest;
import com.vidya.tenantdata.attendance.dto.BulkAttendanceResponse;
import com.vidya.tenantdata.attendance.dto.DailySectionAttendanceResponse;
import com.vidya.tenantdata.attendance.dto.MarkAttendanceRequest;
import com.vidya.tenantdata.attendance.dto.UpdateAttendanceRequest;
import com.vidya.tenantdata.attendance.dto.UpdateAttendanceStatusRequest;
import com.vidya.tenantdata.attendance.enums.AttendanceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Business operations for Student Attendance Management.
 *
 * <p>All write operations run inside the Tenant transaction manager.
 * All validation of academic hierarchy, student eligibility, date rules,
 * and teacher authorization is performed here, not in the controller.
 */
public interface StudentAttendanceService {

    /**
     * Mark attendance for a single student.
     *
     * <p>Validates: academic year (active), academic class, section hierarchy,
     * student (active, not deleted), student academic assignment (active, matching year/class/section),
     * attendance date (not null, within academic year, not future), duplicate detection.
     *
     * @param request         the mark-attendance request
     * @param markerStaffId   UUID of the authenticated staff member marking attendance
     * @return detailed attendance record
     */
    AttendanceDetailResponse markAttendance(MarkAttendanceRequest request, UUID markerStaffId);

    /**
     * Update an existing attendance record (attendance correction).
     *
     * @param id              attendance record UUID
     * @param request         update request (new status + optional remarks)
     * @param markerStaffId   UUID of the authenticated staff member correcting attendance
     * @return updated attendance record
     */
    AttendanceDetailResponse updateAttendance(UUID id, UpdateAttendanceRequest request, UUID markerStaffId);

    /**
     * Patch-update only the status of an existing attendance record.
     *
     * @param id              attendance record UUID
     * @param request         status-only request
     * @param markerStaffId   UUID of the authenticated staff member
     * @return updated attendance record
     */
    AttendanceDetailResponse updateAttendanceStatus(UUID id, UpdateAttendanceStatusRequest request, UUID markerStaffId);

    /**
     * Retrieve a single attendance record by UUID.
     *
     * @param id attendance record UUID
     * @return detailed attendance record
     */
    AttendanceDetailResponse getAttendance(UUID id);

    /**
     * Soft-delete an attendance record.
     *
     * @param id attendance record UUID
     */
    void deleteAttendance(UUID id);

    /**
     * Paginated attendance history for a specific student, with optional filters.
     *
     * @param studentId      the student UUID
     * @param academicYearId optional academic year filter
     * @param fromDate       optional start date filter (inclusive)
     * @param toDate         optional end date filter (inclusive)
     * @param status         optional status filter
     * @param pageable       pagination / sorting
     * @return page of attendance responses
     */
    Page<AttendanceResponse> getStudentAttendanceHistory(
            UUID studentId,
            UUID academicYearId,
            LocalDate fromDate,
            LocalDate toDate,
            AttendanceStatus status,
            Pageable pageable
    );

    /**
     * Retrieve the daily attendance sheet for a section on a specific date.
     * Includes all students active in that section; unrecorded students appear with null status.
     *
     * @param academicYearId  the academic year UUID
     * @param sectionId       the section UUID
     * @param attendanceDate  the calendar date
     * @return daily attendance sheet
     */
    DailySectionAttendanceResponse getDailySectionAttendance(
            UUID academicYearId,
            UUID sectionId,
            LocalDate attendanceDate
    );

    /**
     * Paginated attendance records for a section over a date range.
     *
     * @param sectionId  the section UUID
     * @param fromDate   inclusive start date
     * @param toDate     inclusive end date
     * @param pageable   pagination / sorting
     * @return page of attendance responses
     */
    Page<AttendanceResponse> getSectionAttendanceHistory(
            UUID sectionId,
            LocalDate fromDate,
            LocalDate toDate,
            Pageable pageable
    );

    /**
     * Attendance summary for a student in a given academic year.
     *
     * @param studentId      student UUID
     * @param academicYearId academic year UUID
     * @return attendance summary with counts and percentage
     */
    AttendanceSummaryResponse getAttendanceSummary(UUID studentId, UUID academicYearId);


    /**
     * Mark attendance in bulk for all students in a section on one date using transactional upsert semantics.
     *
     * <p>For each student:
     * <ul>
     *   <li>If no active record exists: creates a new record</li>
     *   <li>If an active record exists: updates the existing record</li>
     * </ul>
     *
     * <p>The entire operation is atomic. If any single student fails validation,
     * the entire batch is rejected without committing any records.
     *
     * @param request       the bulk attendance request
     * @param markerStaffId UUID of the authenticated staff member
     * @return summary counts (created, updated, total) and per-student results
     */
    BulkAttendanceResponse markBulkAttendance(BulkAttendanceRequest request, UUID markerStaffId);

    /**
     * Gaps report: returns working dates in range (max 92 days) on which no attendance was recorded
     * for any student in the section.
     *
     * @param sectionId      section UUID
     * @param academicYearId academic year UUID
     * @param fromDate       inclusive start date
     * @param toDate         inclusive end date
     * @return list of working dates with no attendance recorded
     */
    List<LocalDate> getMissingAttendanceDates(UUID sectionId, UUID academicYearId, LocalDate fromDate, LocalDate toDate);
}
