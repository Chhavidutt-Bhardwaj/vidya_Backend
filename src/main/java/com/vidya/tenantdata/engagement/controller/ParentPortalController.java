package com.vidya.tenantdata.engagement.controller;

import com.vidya.common.api.ApiResponse;
import com.vidya.tenantdata.engagement.dto.EngagementDtos.AnnouncementResponse;
import com.vidya.tenantdata.engagement.dto.EngagementDtos.ChildResponse;
import com.vidya.tenantdata.engagement.dto.EngagementDtos.DailyActivityReport;
import com.vidya.tenantdata.engagement.dto.EngagementDtos.FamilyDailyReport;
import com.vidya.tenantdata.engagement.dto.EngagementDtos.HomeworkResponse;
import com.vidya.tenantdata.engagement.dto.EngagementDtos.RemarkResponse;
import com.vidya.tenantdata.engagement.service.AnnouncementService;
import com.vidya.tenantdata.engagement.service.DailyActivityReportService;
import com.vidya.tenantdata.engagement.service.HomeworkService;
import com.vidya.tenantdata.engagement.service.StudentRemarkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** The family's view: every child linked to the signed-in parent (or the signed-in student). */
@RestController
@RequestMapping("/api/v1/parent")
@RequiredArgsConstructor
@Tag(name = "Parent Portal", description = "Children, daily activity report, homework, remarks and announcements for the signed-in parent")
public class ParentPortalController {

    private static final String OWN_CHILD = "@dataAccess.canAccessStudent(authentication, #studentId, 'STUDENT')";

    private final DailyActivityReportService reportService;
    private final HomeworkService homeworkService;
    private final StudentRemarkService remarkService;
    private final AnnouncementService announcementService;

    @GetMapping("/children")
    @PreAuthorize("hasAuthority('DAILY_REPORT_READ')")
    @Operation(summary = "My children", description = "Students linked to this login, with their current class and section")
    public ResponseEntity<ApiResponse<List<ChildResponse>>> children() {
        return ResponseEntity.ok(ApiResponse.success("CHILDREN", "Children", reportService.children()));
    }

    @GetMapping("/daily-report")
    @PreAuthorize("hasAuthority('DAILY_REPORT_READ')")
    @Operation(summary = "Today's report for all my children", description = "Default date: today")
    public ResponseEntity<ApiResponse<FamilyDailyReport>> familyReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(ApiResponse.success("FAMILY_DAILY_REPORT", "Daily report", reportService.family(date)));
    }

    @GetMapping("/children/{studentId}/daily-report")
    @PreAuthorize("hasAuthority('DAILY_REPORT_READ') and " + OWN_CHILD)
    public ResponseEntity<ApiResponse<DailyActivityReport>> childReport(@PathVariable UUID studentId,
                                                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(ApiResponse.success("DAILY_REPORT", "Daily report", reportService.student(studentId, date)));
    }

    @GetMapping("/children/{studentId}/homework")
    @PreAuthorize("hasAuthority('HOMEWORK_READ') and " + OWN_CHILD)
    @Operation(summary = "A child's homework", description = "Due between from (default today) and to (default in two weeks)")
    public ResponseEntity<ApiResponse<List<HomeworkResponse>>> homework(@PathVariable UUID studentId,
                                                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success("HOMEWORK_LIST", "Homework", homeworkService.forStudent(studentId, from, to)));
    }

    @GetMapping("/children/{studentId}/remarks")
    @PreAuthorize("hasAuthority('STUDENT_REMARK_READ') and " + OWN_CHILD)
    @Operation(summary = "Teacher remarks about a child", description = "Only remarks the teacher made visible to guardians")
    public ResponseEntity<ApiResponse<List<RemarkResponse>>> remarks(@PathVariable UUID studentId,
                                                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                                     @RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(ApiResponse.success("STUDENT_REMARKS", "Remarks", remarkService.list(studentId, null, from, to, limit)));
    }

    @GetMapping("/announcements")
    @PreAuthorize("hasAuthority('ANNOUNCEMENT_READ')")
    @Operation(summary = "Announcements for my children", description = "School-wide and for my children's classes and sections; "
            + "published between from (default 30 days ago) and to (default today), not expired. Emergencies first")
    public ResponseEntity<ApiResponse<List<AnnouncementResponse>>> announcements(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(ApiResponse.success("ANNOUNCEMENTS", "Announcements", announcementService.forFamily(from, to, limit)));
    }
}
