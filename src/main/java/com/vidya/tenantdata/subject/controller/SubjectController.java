package com.vidya.tenantdata.subject.controller;

import com.vidya.common.api.ApiResponse;
import com.vidya.common.api.PagedApiResponse;
import com.vidya.tenantdata.subject.dto.CreateSubjectRequest;
import com.vidya.tenantdata.subject.dto.SubjectDetailResponse;
import com.vidya.tenantdata.subject.dto.SubjectResponse;
import com.vidya.tenantdata.subject.dto.UpdateSubjectRequest;
import com.vidya.tenantdata.subject.dto.UpdateSubjectStatusRequest;
import com.vidya.tenantdata.subject.enums.SubjectStatus;
import com.vidya.tenantdata.subject.enums.SubjectType;
import com.vidya.tenantdata.subject.service.SubjectService;
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

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping({"/api/v1/tenant/subjects", "/api/v1/subjects"})
@RequiredArgsConstructor
@Tag(name = "Subjects", description = "Academic subject definitions management for the active school tenant")
public class SubjectController {

    private final SubjectService subjectService;

    @PostMapping
    @PreAuthorize("hasAuthority('SUBJECT_CREATE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Create subject", description = "Creates a new academic subject definition")
    public ResponseEntity<ApiResponse<SubjectDetailResponse>> createSubject(
            @Valid @RequestBody CreateSubjectRequest request) {
        log.info("REST: Create subject [{}]", request.getName());
        SubjectDetailResponse response = subjectService.createSubject(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("SUBJECT_CREATED", "Subject successfully created", response));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SUBJECT_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Search subjects", description = "Lists subjects with optional filtering by type, status, or search query")
    public ResponseEntity<PagedApiResponse<SubjectResponse>> searchSubjects(
            @RequestParam(required = false) SubjectType subjectType,
            @RequestParam(required = false) SubjectStatus status,
            @RequestParam(required = false) String query,
            @PageableDefault(size = 20, sort = "displayOrder", direction = Sort.Direction.ASC) Pageable pageable) {
        log.info("REST: Search subjects type: [{}], status: [{}], query: [{}]", subjectType, status, query);
        Page<SubjectResponse> page = subjectService.searchSubjects(subjectType, status, query, pageable);
        return ResponseEntity.ok(PagedApiResponse.of("SUBJECTS_RETRIEVED", "Subjects retrieved successfully", page.getContent(), page));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SUBJECT_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Get subject by ID", description = "Retrieves subject details by its UUID")
    public ResponseEntity<ApiResponse<SubjectDetailResponse>> getSubject(
            @Parameter(description = "Subject UUID") @PathVariable UUID id) {
        log.info("REST: Get subject by id: [{}]", id);
        SubjectDetailResponse response = subjectService.getSubject(id);
        return ResponseEntity.ok(ApiResponse.success("SUBJECT_RETRIEVED", "Subject retrieved successfully", response));
    }

    @GetMapping("/code/{subjectCode}")
    @PreAuthorize("hasAuthority('SUBJECT_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Get subject by code", description = "Retrieves subject details by its stable business code")
    public ResponseEntity<ApiResponse<SubjectDetailResponse>> getSubjectByCode(
            @Parameter(description = "Subject business code") @PathVariable String subjectCode) {
        log.info("REST: Get subject by code: [{}]", subjectCode);
        SubjectDetailResponse response = subjectService.getSubjectByCode(subjectCode);
        return ResponseEntity.ok(ApiResponse.success("SUBJECT_RETRIEVED", "Subject retrieved successfully", response));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('SUBJECT_UPDATE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Update subject", description = "Updates subject properties")
    public ResponseEntity<ApiResponse<SubjectDetailResponse>> updateSubject(
            @Parameter(description = "Subject UUID") @PathVariable UUID id,
            @Valid @RequestBody UpdateSubjectRequest request) {
        log.info("REST: Update subject id: [{}]", id);
        SubjectDetailResponse response = subjectService.updateSubject(id, request);
        return ResponseEntity.ok(ApiResponse.success("SUBJECT_UPDATED", "Subject successfully updated", response));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('SUBJECT_STATUS_UPDATE') or hasAuthority('SUBJECT_UPDATE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Update subject status", description = "Transitions subject lifecycle status")
    public ResponseEntity<ApiResponse<SubjectDetailResponse>> updateStatus(
            @Parameter(description = "Subject UUID") @PathVariable UUID id,
            @Valid @RequestBody UpdateSubjectStatusRequest request) {
        log.info("REST: Update status for subject id: [{}] to [{}]", id, request.getStatus());
        SubjectDetailResponse response = subjectService.updateStatus(id, request);
        return ResponseEntity.ok(ApiResponse.success("SUBJECT_STATUS_UPDATED", "Subject status updated successfully", response));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('SUBJECT_DELETE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Delete subject", description = "Soft-deletes an academic subject")
    public ResponseEntity<ApiResponse<Void>> deleteSubject(
            @Parameter(description = "Subject UUID") @PathVariable UUID id) {
        log.info("REST: Delete subject id: [{}]", id);
        subjectService.deleteSubject(id);
        return ResponseEntity.ok(ApiResponse.success("SUBJECT_DELETED", "Subject deleted successfully", null));
    }
}
