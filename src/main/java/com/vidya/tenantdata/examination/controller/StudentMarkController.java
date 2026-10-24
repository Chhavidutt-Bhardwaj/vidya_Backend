package com.vidya.tenantdata.examination.controller;

import com.vidya.common.api.ApiResponse;
import com.vidya.common.api.PagedApiResponse;
import com.vidya.tenantdata.examination.dto.BulkStudentMarksRequest;
import com.vidya.tenantdata.examination.dto.BulkStudentMarksResponse;
import com.vidya.tenantdata.examination.dto.StudentMarkResponse;
import com.vidya.tenantdata.examination.dto.SubmitStudentMarkRequest;
import com.vidya.tenantdata.examination.enums.MarkEntryStatus;
import com.vidya.tenantdata.examination.service.StudentMarkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
@RequestMapping("/api/v1/exams/marks")
@RequiredArgsConstructor
@Tag(name = "Student Marks & Evaluation", description = "Student examination marks entry, automated grading, and marks verification")
public class StudentMarkController {

    private final StudentMarkService studentMarkService;

    @PostMapping("/subject-config/{configId}")
    @PreAuthorize("hasAuthority('MARKS_ENTRY') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN') or hasRole('TEACHER')")
    @Operation(summary = "Submit mark for student", description = "Records marks obtained for a student in an exam subject and computes grade")
    public ResponseEntity<ApiResponse<StudentMarkResponse>> submitStudentMark(
            @PathVariable UUID configId,
            @Valid @RequestBody SubmitStudentMarkRequest request,
            @AuthenticationPrincipal Object principal) {
        log.info("REST: Submit mark for student: {} in config: {}", request.getStudentId(), configId);
        UUID staffId = extractStaffId(principal);
        StudentMarkResponse response = studentMarkService.submitStudentMark(configId, request, staffId);
        return ResponseEntity.ok(ApiResponse.success("STUDENT_MARK_RECORDED", "Student mark recorded successfully", response));
    }

    @PostMapping("/bulk")
    @PreAuthorize("hasAuthority('MARKS_ENTRY') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN') or hasRole('TEACHER')")
    @Operation(summary = "Bulk submit marks for a subject", description = "Transactional bulk mark entry upsert for all students in an exam subject")
    public ResponseEntity<ApiResponse<BulkStudentMarksResponse>> submitBulkMarks(
            @Valid @RequestBody BulkStudentMarksRequest request,
            @AuthenticationPrincipal Object principal) {
        log.info("REST: Bulk marks for config: {}, {} entries", request.getExamSubjectConfigId(), request.getEntries().size());
        UUID staffId = extractStaffId(principal);
        BulkStudentMarksResponse response = studentMarkService.submitBulkMarks(request, staffId);
        return ResponseEntity.ok(ApiResponse.success("BULK_MARKS_PROCESSED",
                "Bulk marks processed: " + response.getCreatedCount() + " created, " + response.getUpdatedCount() + " updated", response));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('MARKS_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN') or hasRole('TEACHER')")
    @Operation(summary = "Get student mark entry by ID")
    public ResponseEntity<ApiResponse<StudentMarkResponse>> getMarkById(@PathVariable UUID id) {
        StudentMarkResponse response = studentMarkService.getMarkById(id);
        return ResponseEntity.ok(ApiResponse.success("STUDENT_MARK_FETCHED", "Student mark retrieved successfully", response));
    }

    @GetMapping("/subject-config/{configId}")
    @PreAuthorize("hasAuthority('MARKS_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN') or hasRole('TEACHER')")
    @Operation(summary = "Search marks for an exam subject with pagination")
    public ResponseEntity<PagedApiResponse<StudentMarkResponse>> searchMarks(
            @PathVariable UUID configId,
            @RequestParam(required = false) MarkEntryStatus status,
            @Parameter(hidden = true)
            @PageableDefault(size = 50) Pageable pageable) {
        Page<StudentMarkResponse> page = studentMarkService.searchMarks(configId, status, pageable);
        return ResponseEntity.ok(PagedApiResponse.of("STUDENT_MARKS_FETCHED", "Student marks retrieved successfully", page.getContent(), page));
    }

    @GetMapping("/student/{studentId}")
    @PreAuthorize("hasAuthority('MARKS_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN') or hasRole('TEACHER')")
    @Operation(summary = "Get all exam marks for a student")
    public ResponseEntity<ApiResponse<List<StudentMarkResponse>>> getMarksByStudent(@PathVariable UUID studentId) {
        List<StudentMarkResponse> responses = studentMarkService.getMarksByStudent(studentId);
        return ResponseEntity.ok(ApiResponse.success("STUDENT_MARKS_FETCHED", "Student marks retrieved successfully", responses));
    }

    @PostMapping("/subject-config/{configId}/lock")
    @PreAuthorize("hasAuthority('MARKS_LOCK') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Lock marks for subject", description = "Freezes mark entries for an exam subject config, preventing further edits")
    public ResponseEntity<ApiResponse<Void>> lockMarks(@PathVariable UUID configId) {
        log.info("REST: Lock marks for config: {}", configId);
        studentMarkService.lockMarks(configId);
        return ResponseEntity.ok(ApiResponse.success("MARKS_LOCKED", "Marks locked successfully"));
    }

    private UUID extractStaffId(Object principal) {
        return null;
    }
}
