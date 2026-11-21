package com.vidya.tenantdata.academic.config.controller;

import com.vidya.common.api.ApiResponse;
import com.vidya.tenantdata.academic.config.dto.AcademicConfigDtos.AllocationResult;
import com.vidya.tenantdata.academic.config.dto.AcademicConfigDtos.ClassTeachersRequest;
import com.vidya.tenantdata.academic.config.dto.AcademicConfigDtos.ConfigOverview;
import com.vidya.tenantdata.academic.config.dto.AcademicConfigDtos.CopyResult;
import com.vidya.tenantdata.academic.config.dto.AcademicConfigDtos.CopySyllabiRequest;
import com.vidya.tenantdata.academic.config.dto.AcademicConfigDtos.DeliverRequest;
import com.vidya.tenantdata.academic.config.dto.AcademicConfigDtos.LessonPlanRequest;
import com.vidya.tenantdata.academic.config.dto.AcademicConfigDtos.LessonPlanResponse;
import com.vidya.tenantdata.academic.config.dto.AcademicConfigDtos.ProgressRequest;
import com.vidya.tenantdata.academic.config.dto.AcademicConfigDtos.ReviewRequest;
import com.vidya.tenantdata.academic.config.dto.AcademicConfigDtos.SubjectTeachersRequest;
import com.vidya.tenantdata.academic.config.dto.AcademicConfigDtos.SyllabusRequest;
import com.vidya.tenantdata.academic.config.dto.AcademicConfigDtos.SyllabusResponse;
import com.vidya.tenantdata.academic.config.dto.AcademicConfigDtos.SyllabusStatusRequest;
import com.vidya.tenantdata.academic.config.dto.AcademicConfigDtos.TeacherLoad;
import com.vidya.tenantdata.academic.config.enums.LessonPlanStatus;
import com.vidya.tenantdata.academic.config.enums.SyllabusStatus;
import com.vidya.tenantdata.academic.config.service.LessonPlanService;
import com.vidya.tenantdata.academic.config.service.SyllabusService;
import com.vidya.tenantdata.academic.config.service.TeacherAllocationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

/** Academic configuration: who teaches what (class teachers, subject teachers), syllabus and lesson plans. */
@RestController
@RequestMapping("/api/v1/academic-config")
@RequiredArgsConstructor
@Tag(name = "Academic Configuration", description = "Class-teacher and subject-teacher mapping, syllabus and lesson plans. "
        + "Subjects: /api/v1/subjects; subjects per class: /api/v1/academic-structure and /api/v1/class-subject-assignments")
public class AcademicConfigController {

    private static final String ALL_STAFF = "@dataAccess.hasFullScope(authentication, 'STAFF')";

    private final TeacherAllocationService allocationService;
    private final SyllabusService syllabusService;
    private final LessonPlanService lessonPlanService;

    // ── Overview & allocation ────────────────────────────────────────────────

    @GetMapping("/overview")
    @PreAuthorize("hasAuthority('CLASS_TEACHER_ASSIGNMENT_READ') and hasAuthority('TEACHER_SUBJECT_ASSIGNMENT_READ') and " + ALL_STAFF)
    @Operation(summary = "Who teaches what",
            description = "Every class and section of the year with its class teacher, subjects, subject teachers and syllabus coverage, "
                    + "plus the problems to fix. Default: the current year.")
    public ResponseEntity<ApiResponse<ConfigOverview>> overview(@RequestParam(required = false) UUID academicYearId) {
        return ResponseEntity.ok(ApiResponse.success("ACADEMIC_CONFIG_OVERVIEW", "Academic configuration", allocationService.overview(academicYearId)));
    }

    @GetMapping("/workload")
    @PreAuthorize("hasAuthority('STAFF_READ') and " + ALL_STAFF)
    @Operation(summary = "Teacher workload", description = "Per teacher: class-teacher sections, subjects taught, planned and timetabled periods a week")
    public ResponseEntity<ApiResponse<List<TeacherLoad>>> workload(@RequestParam(required = false) UUID academicYearId) {
        return ResponseEntity.ok(ApiResponse.success("TEACHER_WORKLOAD", "Teacher workload", allocationService.workload(academicYearId)));
    }

    @PutMapping("/class-teachers")
    @PreAuthorize("hasAuthority('CLASS_TEACHER_ASSIGNMENT_CREATE') and hasAuthority('CLASS_TEACHER_ASSIGNMENT_UPDATE') and " + ALL_STAFF)
    @Operation(summary = "Set class teachers for many sections",
            description = "Assigns, changes (history kept) or removes (staffId null). All or nothing: 422 with per-row errors when any row is invalid. dryRun previews.")
    public ResponseEntity<ApiResponse<AllocationResult>> classTeachers(@Valid @RequestBody ClassTeachersRequest request) {
        return allocation(allocationService.setClassTeachers(request), "CLASS_TEACHERS_SAVED", "Class teachers saved");
    }

    @PutMapping("/subject-teachers")
    @PreAuthorize("hasAuthority('TEACHER_SUBJECT_ASSIGNMENT_CREATE') and hasAuthority('TEACHER_SUBJECT_ASSIGNMENT_UPDATE') and " + ALL_STAFF)
    @Operation(summary = "Set subject teachers for many classes and sections",
            description = "One row per class × section × subject (no section = every section of the class). Assigns, changes (history kept) or "
                    + "removes (teacherId null) the primary teacher. All or nothing: 422 with per-row errors. dryRun previews, including workload warnings.")
    public ResponseEntity<ApiResponse<AllocationResult>> subjectTeachers(@Valid @RequestBody SubjectTeachersRequest request) {
        return allocation(allocationService.setSubjectTeachers(request), "SUBJECT_TEACHERS_SAVED", "Subject teachers saved");
    }

    // ── Syllabus ─────────────────────────────────────────────────────────────

    @GetMapping("/syllabi")
    @PreAuthorize("hasAuthority('SYLLABUS_READ')")
    @Operation(summary = "Syllabi of a year", description = "With coverage per section; archived ones only with status=ARCHIVED")
    public ResponseEntity<ApiResponse<List<SyllabusResponse>>> syllabi(@RequestParam UUID academicYearId,
                                                                       @RequestParam(required = false) UUID academicClassId,
                                                                       @RequestParam(required = false) UUID subjectId,
                                                                       @RequestParam(required = false) SyllabusStatus status) {
        return ResponseEntity.ok(ApiResponse.success("SYLLABI", "Syllabi", syllabusService.list(academicYearId, academicClassId, subjectId, status)));
    }

    @GetMapping("/syllabi/{id}")
    @PreAuthorize("hasAuthority('SYLLABUS_READ')")
    @Operation(summary = "One syllabus with units and topics", description = "With sectionId each topic carries that section's status")
    public ResponseEntity<ApiResponse<SyllabusResponse>> syllabus(@PathVariable UUID id, @RequestParam(required = false) UUID sectionId) {
        return ResponseEntity.ok(ApiResponse.success("SYLLABUS", "Syllabus", syllabusService.get(id, sectionId)));
    }

    @PutMapping("/syllabi")
    @PreAuthorize("hasAuthority('SYLLABUS_MANAGE')")
    @Operation(summary = "Create or replace the syllabus of a class × subject × year",
            description = "The whole document: units and topics in order. Send ids of units / topics to keep (their coverage stays); "
                    + "the rest is removed. Without STAFF scope ALL only for subjects you teach.")
    public ResponseEntity<ApiResponse<SyllabusResponse>> saveSyllabus(@Valid @RequestBody SyllabusRequest request) {
        return ResponseEntity.ok(ApiResponse.success("SYLLABUS_SAVED", "Syllabus saved", syllabusService.save(request)));
    }

    @PatchMapping("/syllabi/{id}/status")
    @PreAuthorize("hasAuthority('SYLLABUS_MANAGE')")
    @Operation(summary = "Publish, archive or move a syllabus back to draft")
    public ResponseEntity<ApiResponse<SyllabusResponse>> syllabusStatus(@PathVariable UUID id, @Valid @RequestBody SyllabusStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.success("SYLLABUS_STATUS_UPDATED", "Syllabus status updated", syllabusService.setStatus(id, request.status())));
    }

    @DeleteMapping("/syllabi/{id}")
    @PreAuthorize("hasAuthority('SYLLABUS_MANAGE') and " + ALL_STAFF)
    public ResponseEntity<ApiResponse<Void>> deleteSyllabus(@PathVariable UUID id) {
        syllabusService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("SYLLABUS_DELETED", "Syllabus deleted"));
    }

    @PostMapping("/syllabi/copy")
    @PreAuthorize("hasAuthority('SYLLABUS_MANAGE') and " + ALL_STAFF)
    @Operation(summary = "Copy last year's syllabi into a new year", description = "Units and topics as drafts; existing ones are skipped")
    public ResponseEntity<ApiResponse<CopyResult>> copySyllabi(@Valid @RequestBody CopySyllabiRequest request) {
        return ResponseEntity.ok(ApiResponse.success("SYLLABI_COPIED", "Syllabi copied", syllabusService.copy(request)));
    }

    @PutMapping("/syllabi/{id}/progress")
    @PreAuthorize("hasAuthority('SYLLABUS_PROGRESS_UPDATE')")
    @Operation(summary = "Mark topics covered in a section", description = "Teachers: only for the subject and section they teach")
    public ResponseEntity<ApiResponse<SyllabusResponse>> progress(@PathVariable UUID id, @Valid @RequestBody ProgressRequest request) {
        return ResponseEntity.ok(ApiResponse.success("SYLLABUS_PROGRESS_UPDATED", "Syllabus coverage updated", syllabusService.updateProgress(id, request)));
    }

    // ── Lesson plans ─────────────────────────────────────────────────────────

    @GetMapping("/lesson-plans")
    @PreAuthorize("hasAuthority('LESSON_PLAN_READ')")
    @Operation(summary = "Lesson plans", description = "Newest first. Without STAFF scope ALL: your own, and submitted plans you may review")
    public ResponseEntity<ApiResponse<List<LessonPlanResponse>>> lessonPlans(
            @RequestParam(required = false) UUID academicYearId, @RequestParam(required = false) UUID teacherId,
            @RequestParam(required = false) UUID academicClassId, @RequestParam(required = false) UUID sectionId,
            @RequestParam(required = false) UUID subjectId, @RequestParam(required = false) LessonPlanStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(ApiResponse.success("LESSON_PLANS", "Lesson plans",
                lessonPlanService.list(academicYearId, teacherId, academicClassId, sectionId, subjectId, status, from, to, limit)));
    }

    @GetMapping("/lesson-plans/{id}")
    @PreAuthorize("hasAuthority('LESSON_PLAN_READ')")
    public ResponseEntity<ApiResponse<LessonPlanResponse>> lessonPlan(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("LESSON_PLAN", "Lesson plan", lessonPlanService.get(id)));
    }

    @PostMapping("/lesson-plans")
    @PreAuthorize("hasAuthority('LESSON_PLAN_MANAGE')")
    @Operation(summary = "Write a lesson plan", description = "For a subject and class the teacher teaches; submit=true sends it for review")
    public ResponseEntity<ApiResponse<LessonPlanResponse>> createLessonPlan(@Valid @RequestBody LessonPlanRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("LESSON_PLAN_CREATED", "Lesson plan created", lessonPlanService.create(request)));
    }

    @PutMapping("/lesson-plans/{id}")
    @PreAuthorize("hasAuthority('LESSON_PLAN_MANAGE')")
    public ResponseEntity<ApiResponse<LessonPlanResponse>> updateLessonPlan(@PathVariable UUID id, @Valid @RequestBody LessonPlanRequest request) {
        return ResponseEntity.ok(ApiResponse.success("LESSON_PLAN_UPDATED", "Lesson plan updated", lessonPlanService.update(id, request)));
    }

    @PostMapping("/lesson-plans/{id}/submit")
    @PreAuthorize("hasAuthority('LESSON_PLAN_MANAGE')")
    public ResponseEntity<ApiResponse<LessonPlanResponse>> submitLessonPlan(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("LESSON_PLAN_SUBMITTED", "Lesson plan submitted", lessonPlanService.submit(id)));
    }

    @PostMapping("/lesson-plans/{id}/review")
    @PreAuthorize("hasAuthority('LESSON_PLAN_REVIEW')")
    @Operation(summary = "Approve or return a submitted lesson plan",
            description = "STAFF scope ALL, or a duty (subject in-charge, coordinator) for the plan's subject, class or stage")
    public ResponseEntity<ApiResponse<LessonPlanResponse>> reviewLessonPlan(@PathVariable UUID id, @Valid @RequestBody ReviewRequest request) {
        return ResponseEntity.ok(ApiResponse.success("LESSON_PLAN_REVIEWED", "Lesson plan reviewed", lessonPlanService.review(id, request)));
    }

    @PostMapping("/lesson-plans/{id}/deliver")
    @PreAuthorize("hasAuthority('LESSON_PLAN_MANAGE')")
    @Operation(summary = "The lesson was taught", description = "Also marks the plan's syllabus topic completed in its section(s)")
    public ResponseEntity<ApiResponse<LessonPlanResponse>> deliverLessonPlan(@PathVariable UUID id, @Valid @RequestBody(required = false) DeliverRequest request) {
        return ResponseEntity.ok(ApiResponse.success("LESSON_PLAN_DELIVERED", "Lesson plan delivered", lessonPlanService.deliver(id, request)));
    }

    @DeleteMapping("/lesson-plans/{id}")
    @PreAuthorize("hasAuthority('LESSON_PLAN_MANAGE')")
    public ResponseEntity<ApiResponse<Void>> deleteLessonPlan(@PathVariable UUID id) {
        lessonPlanService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("LESSON_PLAN_DELETED", "Lesson plan deleted"));
    }

    private static ResponseEntity<ApiResponse<AllocationResult>> allocation(AllocationResult r, String code, String saved) {
        HttpStatus status = r.valid() ? HttpStatus.OK : HttpStatus.UNPROCESSABLE_CONTENT;
        String message = !r.valid() ? "Some rows are invalid; nothing was saved" : r.dryRun() ? "All rows are valid" : saved;
        return ResponseEntity.status(status).body(ApiResponse.success(code, message, r));
    }
}
