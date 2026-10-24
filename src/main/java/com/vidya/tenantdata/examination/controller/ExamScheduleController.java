package com.vidya.tenantdata.examination.controller;

import com.vidya.common.api.ApiResponse;
import com.vidya.common.api.PagedApiResponse;
import com.vidya.tenantdata.examination.dto.CreateExamScheduleRequest;
import com.vidya.tenantdata.examination.dto.CreateExamSubjectConfigRequest;
import com.vidya.tenantdata.examination.dto.ExamScheduleResponse;
import com.vidya.tenantdata.examination.dto.ExamSubjectConfigResponse;
import com.vidya.tenantdata.examination.enums.ExamScheduleStatus;
import com.vidya.tenantdata.examination.service.ExamScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/exams/schedules")
@RequiredArgsConstructor
@Tag(name = "Exam Schedules", description = "Class-level exam timetable schedules and subject date/marks configuration")
public class ExamScheduleController {

    private final ExamScheduleService examScheduleService;

    @PostMapping
    @PreAuthorize("hasAuthority('EXAM_CREATE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Create exam schedule for a class")
    public ResponseEntity<ApiResponse<ExamScheduleResponse>> createExamSchedule(
            @Valid @RequestBody CreateExamScheduleRequest request) {
        log.info("REST: Create exam schedule: {}", request.getName());
        ExamScheduleResponse response = examScheduleService.createExamSchedule(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("EXAM_SCHEDULE_CREATED", "Exam schedule created successfully", response));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('EXAM_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN') or hasRole('TEACHER')")
    @Operation(summary = "Get exam schedule by ID")
    public ResponseEntity<ApiResponse<ExamScheduleResponse>> getExamScheduleById(@PathVariable UUID id) {
        log.info("REST: Get exam schedule ID: {}", id);
        ExamScheduleResponse response = examScheduleService.getExamScheduleById(id);
        return ResponseEntity.ok(ApiResponse.success("EXAM_SCHEDULE_FETCHED", "Exam schedule retrieved successfully", response));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('EXAM_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN') or hasRole('TEACHER')")
    @Operation(summary = "Search exam schedules with filters")
    public ResponseEntity<PagedApiResponse<ExamScheduleResponse>> searchSchedules(
            @RequestParam(required = false) UUID termId,
            @RequestParam(required = false) UUID classId,
            @RequestParam(required = false) ExamScheduleStatus status,
            @Parameter(hidden = true)
            @PageableDefault(size = 20) Pageable pageable) {
        Page<ExamScheduleResponse> page = examScheduleService.searchSchedules(termId, classId, status, pageable);
        return ResponseEntity.ok(PagedApiResponse.of("EXAM_SCHEDULES_FETCHED", "Exam schedules retrieved successfully", page.getContent(), page));
    }

    @PostMapping("/{id}/subjects")
    @PreAuthorize("hasAuthority('EXAM_UPDATE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Add subject to exam schedule", description = "Schedules an exam date, duration, max marks, and pass marks for a subject")
    public ResponseEntity<ApiResponse<ExamSubjectConfigResponse>> addSubjectToSchedule(
            @PathVariable UUID id,
            @Valid @RequestBody CreateExamSubjectConfigRequest request) {
        log.info("REST: Add subject to exam schedule: {}", id);
        ExamSubjectConfigResponse response = examScheduleService.addSubjectToSchedule(id, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("EXAM_SUBJECT_CONFIGURED", "Subject exam configured successfully", response));
    }

    @GetMapping("/{id}/subjects")
    @PreAuthorize("hasAuthority('EXAM_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN') or hasRole('TEACHER')")
    @Operation(summary = "Get configured subjects for an exam schedule")
    public ResponseEntity<ApiResponse<List<ExamSubjectConfigResponse>>> getScheduleSubjects(@PathVariable UUID id) {
        List<ExamSubjectConfigResponse> responses = examScheduleService.getScheduleSubjects(id);
        return ResponseEntity.ok(ApiResponse.success("EXAM_SUBJECTS_FETCHED", "Exam subjects retrieved successfully", responses));
    }

    @DeleteMapping("/{id}/subjects/{subjectConfigId}")
    @PreAuthorize("hasAuthority('EXAM_UPDATE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Remove subject from exam schedule")
    public ResponseEntity<ApiResponse<Void>> removeSubjectFromSchedule(
            @PathVariable UUID id,
            @PathVariable UUID subjectConfigId) {
        log.info("REST: Remove subject config: {} from schedule: {}", subjectConfigId, id);
        examScheduleService.removeSubjectFromSchedule(id, subjectConfigId);
        return ResponseEntity.ok(ApiResponse.success("EXAM_SUBJECT_REMOVED", "Subject removed from exam schedule successfully"));
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAuthority('EXAM_PUBLISH') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Publish exam schedule")
    public ResponseEntity<ApiResponse<ExamScheduleResponse>> publishSchedule(@PathVariable UUID id) {
        log.info("REST: Publish exam schedule: {}", id);
        ExamScheduleResponse response = examScheduleService.publishSchedule(id);
        return ResponseEntity.ok(ApiResponse.success("EXAM_SCHEDULE_PUBLISHED", "Exam schedule published successfully", response));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('EXAM_DELETE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Soft delete exam schedule")
    public ResponseEntity<ApiResponse<Void>> deleteExamSchedule(@PathVariable UUID id) {
        log.info("REST: Delete exam schedule: {}", id);
        examScheduleService.deleteExamSchedule(id);
        return ResponseEntity.ok(ApiResponse.success("EXAM_SCHEDULE_DELETED", "Exam schedule deleted successfully"));
    }
}
