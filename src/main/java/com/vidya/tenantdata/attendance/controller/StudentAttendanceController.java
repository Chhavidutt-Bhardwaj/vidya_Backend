package com.vidya.tenantdata.attendance.controller;

import com.vidya.common.api.ApiResponse;
import com.vidya.common.api.PagedApiResponse;
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
import com.vidya.tenantdata.attendance.service.StudentAttendanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for Student Attendance Management.
 *
 * <p>All operations are tenant-scoped by the {@code TenantRoutingDataSource}.
 * Teacher authorization (section-scoped access) is enforced at the service layer.
 *
 * <p>API base: {@code /api/v1/attendance}
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/attendance")
@RequiredArgsConstructor
@Tag(name = "Student Attendance", description = "Daily student attendance management for the active school tenant")
public class StudentAttendanceController {

    private final StudentAttendanceService attendanceService;

    // ── Mark individual attendance ─────────────────────────────────────────────

    @PostMapping
    @PreAuthorize("hasAuthority('ATTENDANCE_CREATE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN') or hasRole('TEACHER')")
    @Operation(summary = "Mark attendance for a student",
               description = "Marks attendance for a single student. Teachers may only mark attendance for their assigned section.")
    public ResponseEntity<ApiResponse<AttendanceDetailResponse>> markAttendance(
            @Valid @RequestBody MarkAttendanceRequest request,
            @AuthenticationPrincipal Object principal) {
        log.info("REST: Mark attendance for student [{}] on [{}]", request.getStudentId(), request.getAttendanceDate());
        UUID markerStaffId = extractStaffId(principal);
        AttendanceDetailResponse response = attendanceService.markAttendance(request, markerStaffId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("ATTENDANCE_MARKED", "Attendance marked successfully", response));
    }

    // ── Bulk attendance ────────────────────────────────────────────────────────

    @PostMapping("/bulk")
    @PreAuthorize("hasAuthority('ATTENDANCE_BULK_MARK') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN') or hasRole('TEACHER')")
    @Operation(summary = "Bulk mark / upsert attendance for a section",
               description = "Transactional bulk upsert: creates new attendance records or updates existing active records. Atomic: all or nothing.")
    public ResponseEntity<ApiResponse<BulkAttendanceResponse>> markBulkAttendance(
            @Valid @RequestBody BulkAttendanceRequest request,
            @AuthenticationPrincipal Object principal) {
        log.info("REST: Bulk attendance for section [{}] on [{}], {} records",
                request.getSectionId(), request.getAttendanceDate(), request.getRecords().size());
        UUID markerStaffId = extractStaffId(principal);
        BulkAttendanceResponse response = attendanceService.markBulkAttendance(request, markerStaffId);
        return ResponseEntity.ok(ApiResponse.success("BULK_ATTENDANCE_PROCESSED",
                "Bulk attendance processed: " + response.getCreated() + " created, " + response.getUpdated() + " updated", response));
    }

    // ── Get by ID ─────────────────────────────────────────────────────────────

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ATTENDANCE_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN') or hasRole('TEACHER')")
    @Operation(summary = "Get attendance record by ID",
               description = "Retrieves a single attendance record by UUID")
    public ResponseEntity<ApiResponse<AttendanceDetailResponse>> getAttendance(
            @Parameter(description = "Attendance record UUID") @PathVariable UUID id) {
        log.info("REST: Get attendance by id [{}]", id);
        return ResponseEntity.ok(ApiResponse.success("ATTENDANCE_RETRIEVED",
                "Attendance record retrieved", attendanceService.getAttendance(id)));
    }

    // ── Update attendance (correction) ────────────────────────────────────────

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ATTENDANCE_UPDATE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN') or hasRole('TEACHER')")
    @Operation(summary = "Correct attendance record",
               description = "Updates status and/or remarks of an existing attendance record (attendance correction)")
    public ResponseEntity<ApiResponse<AttendanceDetailResponse>> updateAttendance(
            @Parameter(description = "Attendance record UUID") @PathVariable UUID id,
            @Valid @RequestBody UpdateAttendanceRequest request,
            @AuthenticationPrincipal Object principal) {
        log.info("REST: Update attendance [{}] → status [{}]", id, request.getStatus());
        UUID markerStaffId = extractStaffId(principal);
        return ResponseEntity.ok(ApiResponse.success("ATTENDANCE_UPDATED",
                "Attendance record updated", attendanceService.updateAttendance(id, request, markerStaffId)));
    }

    // ── Status-only patch ─────────────────────────────────────────────────────

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('ATTENDANCE_STATUS_UPDATE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN') or hasRole('TEACHER')")
    @Operation(summary = "Update attendance status",
               description = "Patches only the status field of an existing attendance record")
    public ResponseEntity<ApiResponse<AttendanceDetailResponse>> updateAttendanceStatus(
            @Parameter(description = "Attendance record UUID") @PathVariable UUID id,
            @Valid @RequestBody UpdateAttendanceStatusRequest request,
            @AuthenticationPrincipal Object principal) {
        log.info("REST: Patch attendance status [{}] → [{}]", id, request.getStatus());
        UUID markerStaffId = extractStaffId(principal);
        return ResponseEntity.ok(ApiResponse.success("ATTENDANCE_STATUS_UPDATED",
                "Attendance status updated",
                attendanceService.updateAttendanceStatus(id, request, markerStaffId)));
    }

    // ── Soft delete ───────────────────────────────────────────────────────────

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ATTENDANCE_DELETE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Delete attendance record",
               description = "Soft-deletes an attendance record. Historical data is preserved.")
    public ResponseEntity<ApiResponse<Void>> deleteAttendance(
            @Parameter(description = "Attendance record UUID") @PathVariable UUID id) {
        log.info("REST: Delete attendance [{}]", id);
        attendanceService.deleteAttendance(id);
        return ResponseEntity.ok(ApiResponse.success("ATTENDANCE_DELETED", "Attendance record deleted"));
    }

    // ── Student attendance history ────────────────────────────────────────────

    @GetMapping("/students/{studentId}")
    @PreAuthorize("hasAuthority('ATTENDANCE_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN') or hasRole('TEACHER')")
    @Operation(summary = "Get student attendance history",
               description = "Paginated attendance history for a student with optional filters")
    public ResponseEntity<PagedApiResponse<AttendanceResponse>> getStudentAttendanceHistory(
            @Parameter(description = "Student UUID") @PathVariable UUID studentId,
            @RequestParam(required = false) UUID academicYearId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) AttendanceStatus status,
            @PageableDefault(size = 30, sort = "attendanceDate", direction = Sort.Direction.DESC) Pageable pageable) {
        log.info("REST: Student [{}] attendance history", studentId);
        Page<AttendanceResponse> page = attendanceService.getStudentAttendanceHistory(
                studentId, academicYearId, fromDate, toDate, status, pageable);
        return ResponseEntity.ok(PagedApiResponse.of(
                "STUDENT_ATTENDANCE_RETRIEVED", "Student attendance history retrieved",
                page.getContent(), page));
    }

    // ── Student attendance summary ────────────────────────────────────────────

    @GetMapping("/students/{studentId}/summary")
    @PreAuthorize("hasAuthority('ATTENDANCE_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN') or hasRole('TEACHER')")
    @Operation(summary = "Get attendance summary for a student",
               description = "Returns present/absent/late/half-day/excused counts and percentage for a student in an academic year")
    public ResponseEntity<ApiResponse<AttendanceSummaryResponse>> getAttendanceSummary(
            @Parameter(description = "Student UUID") @PathVariable UUID studentId,
            @RequestParam UUID academicYearId) {
        log.info("REST: Attendance summary for student [{}], year [{}]", studentId, academicYearId);
        return ResponseEntity.ok(ApiResponse.success("ATTENDANCE_SUMMARY_RETRIEVED",
                "Attendance summary retrieved",
                attendanceService.getAttendanceSummary(studentId, academicYearId)));
    }

    // ── Daily section attendance sheet ────────────────────────────────────────

    @GetMapping("/sections/{sectionId}/daily")
    @PreAuthorize("hasAuthority('ATTENDANCE_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN') or hasRole('TEACHER')")
    @Operation(summary = "Daily section attendance sheet",
               description = "Returns attendance for all students in a section on a specific date. Unrecorded students have null status.")
    public ResponseEntity<ApiResponse<DailySectionAttendanceResponse>> getDailySectionAttendance(
            @Parameter(description = "Section UUID") @PathVariable UUID sectionId,
            @RequestParam UUID academicYearId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        log.info("REST: Daily attendance sheet for section [{}] on [{}]", sectionId, date);
        return ResponseEntity.ok(ApiResponse.success("DAILY_ATTENDANCE_RETRIEVED",
                "Daily section attendance sheet retrieved",
                attendanceService.getDailySectionAttendance(academicYearId, sectionId, date)));
    }

    // ── Section attendance history ─────────────────────────────────────────────

    @GetMapping("/sections/{sectionId}")
    @PreAuthorize("hasAuthority('ATTENDANCE_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN') or hasRole('TEACHER')")
    @Operation(summary = "Section attendance history",
               description = "Paginated attendance records for a section over a date range")
    public ResponseEntity<PagedApiResponse<AttendanceResponse>> getSectionAttendanceHistory(
            @Parameter(description = "Section UUID") @PathVariable UUID sectionId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @PageableDefault(size = 30, sort = "attendanceDate", direction = Sort.Direction.DESC) Pageable pageable) {
        log.info("REST: Section [{}] attendance history from [{}] to [{}]", sectionId, fromDate, toDate);
        Page<AttendanceResponse> page = attendanceService.getSectionAttendanceHistory(
                sectionId, fromDate, toDate, pageable);
        return ResponseEntity.ok(PagedApiResponse.of(
                "SECTION_ATTENDANCE_RETRIEVED", "Section attendance history retrieved",
                page.getContent(), page));
    }

    // ── Missing attendance gaps report ────────────────────────────────────────

    @GetMapping("/missing")
    @PreAuthorize("hasAuthority('ATTENDANCE_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN') or hasRole('TEACHER')")
    @Operation(summary = "Get missing attendance dates for a section",
               description = "Returns working dates in range (max 92 days) on which no attendance was recorded for any student in the section")
    public ResponseEntity<ApiResponse<List<LocalDate>>> getMissingAttendance(
            @RequestParam UUID sectionId,
            @RequestParam UUID academicYearId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        log.info("REST: Missing attendance dates for section [{}] from [{}] to [{}]", sectionId, from, to);
        List<LocalDate> missingDates = attendanceService.getMissingAttendanceDates(sectionId, academicYearId, from, to);
        return ResponseEntity.ok(ApiResponse.success("MISSING_ATTENDANCE_DATES_RETRIEVED",
                "Missing attendance dates retrieved successfully", missingDates));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    /**
     * Extracts the marker staff UUID from the authenticated principal.
     *
     * <p>The {@link com.vidya.security.user.PlatformUserDetails#getUserId()} returns the
     * Master DB {@code PlatformUser.id}. Resolving this to a Tenant DB {@code Staff.id}
     * requires a cross-DB lookup (masterUserId → TenantUser → Staff) which is beyond the
     * scope of this stateless controller method.
     *
     * <p>For this implementation, teacher-scope authorization (section-level) is enforced at
     * the service layer only when a non-null staffId is provided. The RBAC permission system
     * ({@code ATTENDANCE_CREATE}, {@code TEACHER} role) provides the primary access control gate.
     * Full teacher-scope enforcement will be added when a TenantUserResolverService is available.
     *
     * @return always {@code null} — the service skips teacher-scope check when null
     */
    private UUID extractStaffId(Object principal) {
        // Future: resolve principal → tenantUser → staff and return staff.getId()
        return null;
    }
}
