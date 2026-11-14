package com.vidya.tenantdata.assessment.online;

import com.vidya.common.api.ApiResponse;
import com.vidya.tenantdata.assessment.online.OnlineDtos.AttemptSummary;
import com.vidya.tenantdata.assessment.online.OnlineDtos.AttemptView;
import com.vidya.tenantdata.assessment.online.OnlineDtos.ExamRequest;
import com.vidya.tenantdata.assessment.online.OnlineDtos.ExamView;
import com.vidya.tenantdata.assessment.online.OnlineDtos.GradeRequest;
import com.vidya.tenantdata.assessment.online.OnlineDtos.ManualItem;
import com.vidya.tenantdata.assessment.online.OnlineDtos.Posted;
import com.vidya.tenantdata.assessment.online.OnlineDtos.SaveRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/online-exams")
@RequiredArgsConstructor
@Tag(name = "Online exams", description = "Timed exams on finalised question papers: per-student shuffling, autosave, automatic marking "
        + "of objective questions, teacher marking of written answers, result release and posting to exam / assessment marks")
public class OnlineExamController {

    private final OnlineExamService service;

    @PostMapping
    @PreAuthorize("hasAuthority('ONLINE_EXAM_MANAGE')")
    public ResponseEntity<ApiResponse<ExamView>> create(@Valid @RequestBody ExamRequest request) {
        return ResponseEntity.ok(ApiResponse.success("ONLINE_EXAM_CREATED", "Online exam created", service.create(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ONLINE_EXAM_MANAGE')")
    @Operation(summary = "Change an exam", description = "Until the first attempt")
    public ResponseEntity<ApiResponse<ExamView>> update(@PathVariable UUID id, @Valid @RequestBody ExamRequest request) {
        return ResponseEntity.ok(ApiResponse.success("ONLINE_EXAM_UPDATED", "Online exam updated", service.update(id, request)));
    }

    @PostMapping("/{id}/schedule")
    @PreAuthorize("hasAuthority('ONLINE_EXAM_MANAGE')")
    @Operation(summary = "Schedule", description = "Students of the class / section see the exam and can start it inside the window")
    public ResponseEntity<ApiResponse<ExamView>> schedule(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("ONLINE_EXAM_SCHEDULED", "Online exam scheduled", service.schedule(id)));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('ONLINE_EXAM_MANAGE')")
    public ResponseEntity<ApiResponse<ExamView>> cancel(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("ONLINE_EXAM_CANCELLED", "Online exam cancelled", service.cancel(id)));
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ONLINE_EXAM_MANAGE', 'ONLINE_EXAM_TAKE')")
    @Operation(summary = "Online exams", description = "Students: the scheduled exams of their class; staff: exams of the subjects they teach")
    public ResponseEntity<ApiResponse<List<ExamView>>> list(@RequestParam(required = false) UUID academicClassId,
                                                            @RequestParam(required = false) UUID sectionId) {
        return ResponseEntity.ok(ApiResponse.success("ONLINE_EXAMS", "Online exams", service.list(academicClassId, sectionId)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ONLINE_EXAM_MANAGE', 'ONLINE_EXAM_TAKE')")
    public ResponseEntity<ApiResponse<ExamView>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("ONLINE_EXAM", "Online exam", service.get(id)));
    }

    // ── student ──

    @PostMapping("/{id}/attempts")
    @PreAuthorize("hasAuthority('ONLINE_EXAM_TAKE')")
    @Operation(summary = "Start or resume", description = "Returns the questions in this student's order, saved answers and the seconds left")
    public ResponseEntity<ApiResponse<AttemptView>> start(@PathVariable UUID id,
                                                          @RequestHeader(value = "User-Agent", required = false) String userAgent) {
        return ResponseEntity.ok(ApiResponse.success("ATTEMPT_STARTED", "Attempt started", service.start(id, userAgent)));
    }

    @GetMapping("/attempts/{attemptId}")
    @PreAuthorize("hasAnyAuthority('ONLINE_EXAM_TAKE', 'ONLINE_EXAM_MANAGE', 'REPORT_CARD_READ')")
    @Operation(summary = "Attempt", description = "With the result once the exam's result setting allows")
    public ResponseEntity<ApiResponse<AttemptView>> attempt(@PathVariable UUID attemptId) {
        return ResponseEntity.ok(ApiResponse.success("ATTEMPT", "Attempt", service.attempt(attemptId)));
    }

    @PutMapping("/attempts/{attemptId}/answers")
    @PreAuthorize("hasAuthority('ONLINE_EXAM_TAKE')")
    @Operation(summary = "Save answers", description = "Autosave; accepted until the deadline plus 30 seconds")
    public ResponseEntity<ApiResponse<AttemptView>> save(@PathVariable UUID attemptId, @Valid @RequestBody SaveRequest request) {
        return ResponseEntity.ok(ApiResponse.success("ANSWERS_SAVED", "Answers saved", service.save(attemptId, request.answers())));
    }

    @PostMapping("/attempts/{attemptId}/submit")
    @PreAuthorize("hasAuthority('ONLINE_EXAM_TAKE')")
    public ResponseEntity<ApiResponse<AttemptView>> submit(@PathVariable UUID attemptId) {
        return ResponseEntity.ok(ApiResponse.success("ATTEMPT_SUBMITTED", "Attempt submitted", service.submit(attemptId)));
    }

    // ── staff ──

    @GetMapping("/{id}/attempts")
    @PreAuthorize("hasAuthority('ONLINE_EXAM_MANAGE')")
    public ResponseEntity<ApiResponse<List<AttemptSummary>>> attempts(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("ATTEMPTS", "Attempts", service.attempts(id)));
    }

    @GetMapping("/{id}/manual-grading")
    @PreAuthorize("hasAuthority('ONLINE_EXAM_MANAGE')")
    @Operation(summary = "Written answers to mark", description = "pendingOnly=false lists marked answers too")
    public ResponseEntity<ApiResponse<List<ManualItem>>> manual(@PathVariable UUID id, @RequestParam(defaultValue = "true") boolean pendingOnly) {
        return ResponseEntity.ok(ApiResponse.success("MANUAL_GRADING", "Written answers", service.manualQueue(id, pendingOnly)));
    }

    @PutMapping("/{id}/manual-grading")
    @PreAuthorize("hasAuthority('ONLINE_EXAM_MANAGE')")
    public ResponseEntity<ApiResponse<List<AttemptSummary>>> grade(@PathVariable UUID id, @Valid @RequestBody GradeRequest request) {
        return ResponseEntity.ok(ApiResponse.success("ANSWERS_GRADED", "Marks saved", service.grade(id, request.grades())));
    }

    @PostMapping("/{id}/publish-results")
    @PreAuthorize("hasAuthority('ONLINE_EXAM_MANAGE')")
    @Operation(summary = "Release results", description = "For exams with result visibility AFTER_PUBLISH; needs all answers marked")
    public ResponseEntity<ApiResponse<ExamView>> publish(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("RESULTS_PUBLISHED", "Results released", service.publishResults(id)));
    }

    @PostMapping("/{id}/post-marks")
    @PreAuthorize("hasAuthority('ONLINE_EXAM_MANAGE') and hasAuthority('MARKS_ENTRY')")
    @Operation(summary = "Post marks", description = "Best attempt per student, scaled to the linked exam paper / assessment; "
            + "students who did not attempt are marked absent")
    public ResponseEntity<ApiResponse<Posted>> post(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("MARKS_POSTED", "Marks posted", service.postMarks(id)));
    }
}
