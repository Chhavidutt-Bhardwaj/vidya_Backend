package com.vidya.tenantdata.engagement.controller;

import com.vidya.common.api.ApiResponse;
import com.vidya.tenantdata.engagement.dto.EngagementDtos.DailyActivityReport;
import com.vidya.tenantdata.engagement.dto.EngagementDtos.DailyReportSettingsRequest;
import com.vidya.tenantdata.engagement.dto.EngagementDtos.DailyReportSettingsResponse;
import com.vidya.tenantdata.engagement.dto.EngagementDtos.OutboxItem;
import com.vidya.tenantdata.engagement.dto.EngagementDtos.ReportRunResponse;
import com.vidya.tenantdata.engagement.dto.EngagementDtos.RunRequest;
import com.vidya.tenantdata.engagement.enums.ReportRunTrigger;
import com.vidya.tenantdata.engagement.notification.NotificationOutboxService;
import com.vidya.tenantdata.engagement.service.DailyActivityReportService;
import com.vidya.tenantdata.engagement.service.DailyReportGenerator;
import com.vidya.tenantdata.user.account.SchoolContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/daily-reports")
@RequiredArgsConstructor
@Tag(name = "Daily Activity Report", description = "Per-student daily summary (attendance, homework, lessons, activities, announcements, remarks), "
        + "its settings and send history. Families: /api/v1/parent/daily-report")
public class DailyReportController {

    private final DailyActivityReportService reportService;
    private final DailyReportGenerator generator;
    private final NotificationOutboxService outbox;
    private final SchoolContext context;
    private final Clock clock;

    @GetMapping("/students/{studentId}")
    @PreAuthorize("hasAuthority('DAILY_REPORT_READ') and @dataAccess.canAccessStudent(authentication, #studentId, 'STUDENT')")
    @Operation(summary = "One student's daily report", description = "Default date: today")
    public ResponseEntity<ApiResponse<DailyActivityReport>> student(@PathVariable UUID studentId,
                                                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(ApiResponse.success("DAILY_REPORT", "Daily report", reportService.student(studentId, date)));
    }

    @GetMapping("/sections/{sectionId}")
    @PreAuthorize("hasAuthority('DAILY_REPORT_READ') and @dataAccess.canAccessSection(authentication, #sectionId, 'STUDENT')")
    @Operation(summary = "Daily reports of a section", description = "What every family of the section gets (teacher preview)")
    public ResponseEntity<ApiResponse<List<DailyActivityReport>>> section(@PathVariable UUID sectionId,
                                                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(ApiResponse.success("DAILY_REPORTS", "Daily reports", reportService.section(sectionId, date)));
    }

    @GetMapping("/settings")
    @PreAuthorize("hasAuthority('DAILY_REPORT_MANAGE')")
    public ResponseEntity<ApiResponse<DailyReportSettingsResponse>> settings() {
        return ResponseEntity.ok(ApiResponse.success("DAILY_REPORT_SETTINGS", "Daily report settings", reportService.settings()));
    }

    @PutMapping("/settings")
    @PreAuthorize("hasAuthority('DAILY_REPORT_MANAGE') and @dataAccess.hasFullScope(authentication, 'STUDENT')")
    @Operation(summary = "Turn the daily report on / off, set the send time and what it contains",
            description = "Fields left out keep their value. Wording and channels: operations-config templates, event DAILY_ACTIVITY_REPORT")
    public ResponseEntity<ApiResponse<DailyReportSettingsResponse>> saveSettings(@RequestBody DailyReportSettingsRequest request) {
        return ResponseEntity.ok(ApiResponse.success("DAILY_REPORT_SETTINGS_SAVED", "Daily report settings saved", reportService.saveSettings(request)));
    }

    @GetMapping("/runs")
    @PreAuthorize("hasAuthority('DAILY_REPORT_MANAGE')")
    @Operation(summary = "Send history", description = "One row per day: status, students with news, recipients, messages queued")
    public ResponseEntity<ApiResponse<List<ReportRunResponse>>> runs(@RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(ApiResponse.success("DAILY_REPORT_RUNS", "Daily report runs", generator.runs(limit)));
    }

    @PostMapping("/runs")
    @PreAuthorize("hasAuthority('DAILY_REPORT_MANAGE') and @dataAccess.hasFullScope(authentication, 'STUDENT')")
    @Operation(summary = "Send a day's report now", description = "Default: today. Works even when automatic sending is off or the day is a "
            + "holiday. Families already sent that day's report are not sent it again")
    public ResponseEntity<ApiResponse<ReportRunResponse>> run(@RequestBody(required = false) RunRequest request) {
        LocalDate date = request != null && request.date() != null ? request.date() : LocalDate.now(clock);
        return ResponseEntity.ok(ApiResponse.success("DAILY_REPORT_SENT", "Daily report queued",
                generator.generate(date, ReportRunTrigger.MANUAL, context.actorId())));
    }

    @GetMapping("/outbox")
    @PreAuthorize("hasAuthority('COMMUNICATION_READ') and @dataAccess.hasFullScope(authentication, 'STUDENT')")
    @Operation(summary = "Recent outgoing messages", description = "Every channel, newest first, with delivery status and errors")
    public ResponseEntity<ApiResponse<List<OutboxItem>>> outbox(@RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(ApiResponse.success("NOTIFICATION_OUTBOX", "Outgoing messages", outbox.recent(limit)));
    }
}
