package com.vidya.tenantdata.dashboard.controller;

import com.vidya.common.api.ApiResponse;
import com.vidya.tenantdata.dashboard.dto.DashboardDtos.ActionItem;
import com.vidya.tenantdata.dashboard.dto.DashboardDtos.ActionItemType;
import com.vidya.tenantdata.dashboard.dto.DashboardDtos.AttendanceTrend;
import com.vidya.tenantdata.dashboard.dto.DashboardDtos.AutoDebit;
import com.vidya.tenantdata.dashboard.dto.DashboardDtos.ClassEnrollment;
import com.vidya.tenantdata.dashboard.dto.DashboardDtos.DashboardHome;
import com.vidya.tenantdata.dashboard.dto.DashboardDtos.DismissResult;
import com.vidya.tenantdata.dashboard.dto.DashboardDtos.Kpis;
import com.vidya.tenantdata.dashboard.dto.DashboardDtos.PulseTile;
import com.vidya.tenantdata.dashboard.dto.DashboardDtos.QuickCounts;
import com.vidya.tenantdata.dashboard.dto.DashboardDtos.Snapshot;
import com.vidya.tenantdata.dashboard.service.DashboardService;
import com.vidya.tenantdata.fee.dto.FeeDtos.FeeBreakdown;
import com.vidya.tenantdata.fee.dto.FeeDtos.FeeTrend;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * School overview dashboard (sidebar "Overview"). Every endpoint needs DASHBOARD_READ; each block inside also needs its module's
 * read permission and school-wide scope, otherwise it is null and listed in {@code unavailable}. See tenantdata/dashboard/DASHBOARD.md.
 *
 * <p>Common parameters: {@code academicYearId} (default: the current year), {@code from} / {@code to} (default: month to date).
 */
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "School overview: KPIs, school snapshot, live operations, quick counts, trends and action items")
public class DashboardController {

    private final DashboardService service;

    @GetMapping
    @PreAuthorize("hasAuthority('DASHBOARD_READ')")
    @Operation(summary = "Everything on the home screen",
            description = "KPIs, snapshot, operations pulse, quick counts and the first 5 action items in one call. "
                    + "Blocks the user may not read, or whose module is not built, are null and listed in unavailable.")
    public ResponseEntity<ApiResponse<DashboardHome>> home(@RequestParam(required = false) UUID academicYearId,
                                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success("DASHBOARD", "Dashboard", service.home(academicYearId, from, to)));
    }

    @GetMapping("/kpis")
    @PreAuthorize("hasAuthority('DASHBOARD_READ')")
    @Operation(summary = "Hero KPIs", description = "Active enrolment vs capacity and last year; attendance of the 'to' date vs the previous 30-day "
            + "average; students admitted in the window vs the window before; fees collected vs falling due, collection rate and overdue.")
    public ResponseEntity<ApiResponse<Kpis>> kpis(@RequestParam(required = false) UUID academicYearId,
                                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success("DASHBOARD_KPIS", "Dashboard KPIs", service.kpis(academicYearId, from, to)));
    }

    @GetMapping("/snapshot")
    @PreAuthorize("hasAuthority('DASHBOARD_READ')")
    @Operation(summary = "School at a glance", description = "Students on the roll (admitted / active / without section / boys / girls / with a guardian), "
            + "staff (teaching / non-teaching / inactive), classes, sections, subjects, sections without a class teacher and timetable state.")
    public ResponseEntity<ApiResponse<Snapshot>> snapshot(@RequestParam(required = false) UUID academicYearId) {
        return ResponseEntity.ok(ApiResponse.success("DASHBOARD_SNAPSHOT", "School snapshot", service.snapshot(academicYearId)));
    }

    @GetMapping("/operations-pulse")
    @PreAuthorize("hasAuthority('DASHBOARD_READ')")
    @Operation(summary = "Live operations tiles for today", description = "Attendance marking progress, substitutions, next exam paper and fleet status.")
    public ResponseEntity<ApiResponse<List<PulseTile>>> operationsPulse() {
        return ResponseEntity.ok(ApiResponse.success("DASHBOARD_PULSE", "Operations pulse", service.operationsPulse()));
    }

    @GetMapping("/quick-counts")
    @PreAuthorize("hasAuthority('DASHBOARD_READ')")
    @Operation(summary = "Badge counts", description = "Sections still to mark today, substitutions today, pending approvals, admissions in the last 7 days, "
            + "holidays in the next 30 days. A null count is not available to the user.")
    public ResponseEntity<ApiResponse<QuickCounts>> quickCounts() {
        return ResponseEntity.ok(ApiResponse.success("DASHBOARD_QUICK_COUNTS", "Quick counts", service.quickCounts()));
    }

    @GetMapping("/attendance-trend")
    @PreAuthorize("hasAuthority('DASHBOARD_READ') and hasAuthority('ATTENDANCE_READ') and @dataAccess.hasFullScope(authentication, 'ATTENDANCE')")
    @Operation(summary = "Daily attendance rate", description = "One point per day that has marks, in the window (default month to date, at most 366 days).")
    public ResponseEntity<ApiResponse<AttendanceTrend>> attendanceTrend(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success("DASHBOARD_ATTENDANCE_TREND", "Attendance trend", service.attendanceTrend(from, to)));
    }

    @GetMapping("/enrollment-by-class")
    @PreAuthorize("hasAuthority('DASHBOARD_READ') and hasAuthority('STUDENT_READ') and @dataAccess.hasFullScope(authentication, 'STUDENT')")
    @Operation(summary = "Students per class", description = "Live roll, section capacity and occupancy for every class of the year.")
    public ResponseEntity<ApiResponse<List<ClassEnrollment>>> enrollmentByClass(@RequestParam(required = false) UUID academicYearId) {
        return ResponseEntity.ok(ApiResponse.success("DASHBOARD_ENROLLMENT_BY_CLASS", "Enrolment by class", service.enrollmentByClass(academicYearId)));
    }

    @GetMapping("/fee-trend")
    @PreAuthorize("hasAuthority('DASHBOARD_READ') and hasAuthority('FEE_READ') and @dataAccess.hasFullScope(authentication, 'FEE')")
    @Operation(summary = "Fee collections per day", description = "Money realized each day of the window (default month to date) and the running total.")
    public ResponseEntity<ApiResponse<FeeTrend>> feeTrend(@RequestParam(required = false) UUID academicYearId,
                                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success("DASHBOARD_FEE_TREND", "Fee collection trend", service.feeTrend(academicYearId, from, to)));
    }

    @GetMapping("/fee-breakdown")
    @PreAuthorize("hasAuthority('DASHBOARD_READ') and hasAuthority('FEE_READ') and @dataAccess.hasFullScope(authentication, 'FEE')")
    @Operation(summary = "Fees per head and per class", description = "Billed, concession, paid, due and overdue of the year, with the collection rate.")
    public ResponseEntity<ApiResponse<FeeBreakdown>> feeBreakdown(@RequestParam(required = false) UUID academicYearId) {
        return ResponseEntity.ok(ApiResponse.success("DASHBOARD_FEE_BREAKDOWN", "Fee breakdown", service.feeBreakdown(academicYearId)));
    }

    @GetMapping("/auto-debit")
    @PreAuthorize("hasAuthority('DASHBOARD_READ') and hasAuthority('FEE_READ') and @dataAccess.hasFullScope(authentication, 'FEE')")
    @Operation(summary = "This month's auto-debit", description = "Mandates active / pending and the month's debits: scheduled, at the bank, "
            + "collected, bounced (with NACH bounce charges + GST), skipped and refused. month = YYYY-MM, default this month.")
    public ResponseEntity<ApiResponse<AutoDebit>> autoDebit(@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return ResponseEntity.ok(ApiResponse.success("DASHBOARD_AUTO_DEBIT", "Auto-debit", service.autoDebit(month)));
    }

    @GetMapping("/export")
    @PreAuthorize("hasAuthority('DASHBOARD_READ')")
    @Operation(summary = "Export the summary as CSV", description = "The home screen as section,metric,value rows (money in rupees), "
            + "with only the blocks the user may see and the ones not available listed at the end.")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) UUID academicYearId,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        byte[] body = ("\uFEFF" + service.exportCsv(academicYearId, from, to)).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"dashboard-summary.csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(body);
    }

    @GetMapping("/action-items")
    @PreAuthorize("hasAuthority('DASHBOARD_READ')")
    @Operation(summary = "Waiting for my approval", description = "Lesson plans to review, marks to lock, report cards to publish, re-evaluation "
            + "requests, fee concessions, refused auto-debits, online payments to refund and stalled mandates, oldest first, limited to the kinds the user can act on and not dismissed by them. Approve through each item's api.")
    public ResponseEntity<ApiResponse<Page<ActionItem>>> actionItems(@RequestParam(required = false) UUID academicYearId,
                                                                     @RequestParam(required = false) ActionItemType type,
                                                                     @RequestParam(defaultValue = "0") int page,
                                                                     @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success("DASHBOARD_ACTION_ITEMS", "Action items",
                service.actionItems(academicYearId, type, Math.max(0, page), Math.min(Math.max(1, size), 200))));
    }

    @PostMapping("/action-items/{id}/dismiss")
    @PreAuthorize("hasAuthority('DASHBOARD_READ')")
    @Operation(summary = "Hide an action item", description = "Hides the item (id = TYPE:key) from the current user's dashboard only. "
            + "It is not approved or rejected and stays pending in its module.")
    public ResponseEntity<ApiResponse<DismissResult>> dismiss(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success("DASHBOARD_ACTION_ITEM_DISMISSED", "Action item dismissed", service.dismiss(id)));
    }
}
