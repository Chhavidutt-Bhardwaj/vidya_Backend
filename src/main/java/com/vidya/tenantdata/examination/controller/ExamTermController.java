package com.vidya.tenantdata.examination.controller;

import com.vidya.common.api.ApiResponse;
import com.vidya.common.api.PagedApiResponse;
import com.vidya.tenantdata.examination.dto.CreateExamTermRequest;
import com.vidya.tenantdata.examination.dto.ExamTermResponse;
import com.vidya.tenantdata.examination.dto.UpdateExamTermRequest;
import com.vidya.tenantdata.examination.enums.ExamTermStatus;
import com.vidya.tenantdata.examination.service.ExamTermService;
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

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/exams/terms")
@RequiredArgsConstructor
@Tag(name = "Exam Terms", description = "Management of examination terms and evaluation cycles")
public class ExamTermController {

    private final ExamTermService examTermService;

    @PostMapping
    @PreAuthorize("hasAuthority('EXAM_CREATE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Create exam term", description = "Defines an exam cycle (e.g. Term 1, Final Exam) under an academic year")
    public ResponseEntity<ApiResponse<ExamTermResponse>> createExamTerm(
            @Valid @RequestBody CreateExamTermRequest request) {
        log.info("REST: Create exam term: {}", request.getName());
        ExamTermResponse response = examTermService.createExamTerm(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("EXAM_TERM_CREATED", "Exam term created successfully", response));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('EXAM_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN') or hasRole('TEACHER')")
    @Operation(summary = "Get exam term by ID")
    public ResponseEntity<ApiResponse<ExamTermResponse>> getExamTermById(@PathVariable UUID id) {
        log.info("REST: Get exam term ID: {}", id);
        ExamTermResponse response = examTermService.getExamTermById(id);
        return ResponseEntity.ok(ApiResponse.success("EXAM_TERM_FETCHED", "Exam term retrieved successfully", response));
    }

    @GetMapping("/academic-year/{academicYearId}")
    @PreAuthorize("hasAuthority('EXAM_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN') or hasRole('TEACHER')")
    @Operation(summary = "List exam terms for an academic year")
    public ResponseEntity<ApiResponse<List<ExamTermResponse>>> getExamTermsByAcademicYear(
            @PathVariable UUID academicYearId) {
        List<ExamTermResponse> responses = examTermService.getExamTermsByAcademicYear(academicYearId);
        return ResponseEntity.ok(ApiResponse.success("EXAM_TERMS_FETCHED", "Exam terms retrieved successfully", responses));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('EXAM_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN') or hasRole('TEACHER')")
    @Operation(summary = "Search exam terms with pagination")
    public ResponseEntity<PagedApiResponse<ExamTermResponse>> searchExamTerms(
            @RequestParam(required = false) UUID academicYearId,
            @RequestParam(required = false) ExamTermStatus status,
            @Parameter(hidden = true)
            @PageableDefault(size = 20, sort = "startDate", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<ExamTermResponse> page = examTermService.searchExamTerms(academicYearId, status, pageable);
        return ResponseEntity.ok(PagedApiResponse.of("EXAM_TERMS_FETCHED", "Exam terms retrieved successfully", page.getContent(), page));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('EXAM_UPDATE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Update exam term details")
    public ResponseEntity<ApiResponse<ExamTermResponse>> updateExamTerm(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateExamTermRequest request) {
        log.info("REST: Update exam term ID: {}", id);
        ExamTermResponse response = examTermService.updateExamTerm(id, request);
        return ResponseEntity.ok(ApiResponse.success("EXAM_TERM_UPDATED", "Exam term updated successfully", response));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('EXAM_UPDATE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Update exam term lifecycle status")
    public ResponseEntity<ApiResponse<ExamTermResponse>> updateTermStatus(
            @PathVariable UUID id,
            @RequestParam ExamTermStatus status) {
        log.info("REST: Update exam term ID: {} status to: {}", id, status);
        ExamTermResponse response = examTermService.updateTermStatus(id, status);
        return ResponseEntity.ok(ApiResponse.success("EXAM_TERM_STATUS_UPDATED", "Exam term status updated", response));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('EXAM_DELETE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Soft delete exam term")
    public ResponseEntity<ApiResponse<Void>> deleteExamTerm(@PathVariable UUID id) {
        log.info("REST: Delete exam term ID: {}", id);
        examTermService.deleteExamTerm(id);
        return ResponseEntity.ok(ApiResponse.success("EXAM_TERM_DELETED", "Exam term deleted successfully"));
    }
}
