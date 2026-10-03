package com.vidya.tenantdata.assignment.controller;

import com.vidya.common.api.ApiResponse;
import com.vidya.common.api.PagedApiResponse;
import com.vidya.tenantdata.assignment.dto.ClassSubjectAssignmentDetailResponse;
import com.vidya.tenantdata.assignment.dto.ClassSubjectAssignmentResponse;
import com.vidya.tenantdata.assignment.dto.CreateClassSubjectAssignmentRequest;
import com.vidya.tenantdata.assignment.dto.UpdateClassSubjectAssignmentRequest;
import com.vidya.tenantdata.assignment.dto.UpdateClassSubjectAssignmentStatusRequest;
import com.vidya.tenantdata.assignment.enums.ClassSubjectAssignmentStatus;
import com.vidya.tenantdata.assignment.service.ClassSubjectAssignmentService;
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
@RequestMapping({"/api/v1/tenant/class-subject-assignments", "/api/v1/class-subject-assignments"})
@RequiredArgsConstructor
@Tag(name = "Class Subject Assignments", description = "Class-level subject academic deployment management for the active school tenant")
public class ClassSubjectAssignmentController {

    private final ClassSubjectAssignmentService classSubjectAssignmentService;

    @PostMapping
    @PreAuthorize("hasAuthority('CLASS_SUBJECT_ASSIGNMENT_CREATE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Create class subject deployment", description = "Deploys a subject offering to an academic class/section")
    public ResponseEntity<ApiResponse<ClassSubjectAssignmentDetailResponse>> createAssignment(
            @Valid @RequestBody CreateClassSubjectAssignmentRequest request) {
        log.info("REST: Create class subject assignment for class [{}] and subject [{}]", request.getAcademicClassId(), request.getSubjectId());
        ClassSubjectAssignmentDetailResponse response = classSubjectAssignmentService.createAssignment(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("CLASS_SUBJECT_ASSIGNMENT_CREATED", "Class subject assignment successfully created", response));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('CLASS_SUBJECT_ASSIGNMENT_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Search class subject deployments", description = "Lists class subject deployments with filtering and pagination")
    public ResponseEntity<PagedApiResponse<ClassSubjectAssignmentResponse>> searchAssignments(
            @RequestParam(required = false) UUID academicYearId,
            @RequestParam(required = false) UUID academicClassId,
            @RequestParam(required = false) UUID sectionId,
            @RequestParam(required = false) UUID subjectId,
            @RequestParam(required = false) ClassSubjectAssignmentStatus status,
            @RequestParam(required = false) Boolean isCompulsory,
            @PageableDefault(size = 20, sort = "displayOrder", direction = Sort.Direction.ASC) Pageable pageable) {
        log.info("REST: Search class subject assignments year: [{}], class: [{}], section: [{}], subject: [{}]",
                academicYearId, academicClassId, sectionId, subjectId);
        Page<ClassSubjectAssignmentResponse> page = classSubjectAssignmentService.searchAssignments(
                academicYearId, academicClassId, sectionId, subjectId, status, isCompulsory, pageable);
        return ResponseEntity.ok(PagedApiResponse.of("CLASS_SUBJECT_ASSIGNMENTS_RETRIEVED", "Class subject assignments retrieved successfully", page.getContent(), page));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('CLASS_SUBJECT_ASSIGNMENT_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Get class subject assignment by ID", description = "Retrieves class subject deployment details by UUID")
    public ResponseEntity<ApiResponse<ClassSubjectAssignmentDetailResponse>> getAssignment(
            @Parameter(description = "Assignment UUID") @PathVariable UUID id) {
        log.info("REST: Get class subject assignment by id: [{}]", id);
        ClassSubjectAssignmentDetailResponse response = classSubjectAssignmentService.getAssignment(id);
        return ResponseEntity.ok(ApiResponse.success("CLASS_SUBJECT_ASSIGNMENT_RETRIEVED", "Class subject assignment retrieved successfully", response));
    }

    @GetMapping("/code/{assignmentCode}")
    @PreAuthorize("hasAuthority('CLASS_SUBJECT_ASSIGNMENT_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Get class subject assignment by code", description = "Retrieves class subject deployment details by business code")
    public ResponseEntity<ApiResponse<ClassSubjectAssignmentDetailResponse>> getAssignmentByCode(
            @Parameter(description = "Assignment business code") @PathVariable String assignmentCode) {
        log.info("REST: Get class subject assignment by code: [{}]", assignmentCode);
        ClassSubjectAssignmentDetailResponse response = classSubjectAssignmentService.getAssignmentByCode(assignmentCode);
        return ResponseEntity.ok(ApiResponse.success("CLASS_SUBJECT_ASSIGNMENT_RETRIEVED", "Class subject assignment retrieved successfully", response));
    }

    @GetMapping("/class/{academicClassId}")
    @PreAuthorize("hasAuthority('CLASS_SUBJECT_ASSIGNMENT_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Get active subjects for class/section", description = "Retrieves all active subject assignments for a specific class and optional section")
    public ResponseEntity<ApiResponse<List<ClassSubjectAssignmentResponse>>> getActiveAssignmentsByClass(
            @Parameter(description = "Academic Class UUID") @PathVariable UUID academicClassId,
            @RequestParam(required = false) UUID sectionId,
            @RequestParam(required = false) UUID academicYearId) {
        log.info("REST: Get active class subjects for class [{}] section [{}] year [{}]", academicClassId, sectionId, academicYearId);
        List<ClassSubjectAssignmentResponse> response = classSubjectAssignmentService.getActiveAssignmentsByClassAndSection(academicYearId, academicClassId, sectionId);
        return ResponseEntity.ok(ApiResponse.success("CLASS_SUBJECT_ASSIGNMENTS_RETRIEVED", "Active class subject assignments retrieved successfully", response));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('CLASS_SUBJECT_ASSIGNMENT_UPDATE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Update class subject assignment", description = "Updates class subject deployment properties")
    public ResponseEntity<ApiResponse<ClassSubjectAssignmentDetailResponse>> updateAssignment(
            @Parameter(description = "Assignment UUID") @PathVariable UUID id,
            @Valid @RequestBody UpdateClassSubjectAssignmentRequest request) {
        log.info("REST: Update class subject assignment id: [{}]", id);
        ClassSubjectAssignmentDetailResponse response = classSubjectAssignmentService.updateAssignment(id, request);
        return ResponseEntity.ok(ApiResponse.success("CLASS_SUBJECT_ASSIGNMENT_UPDATED", "Class subject assignment updated successfully", response));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('CLASS_SUBJECT_ASSIGNMENT_STATUS_UPDATE') or hasAuthority('CLASS_SUBJECT_ASSIGNMENT_UPDATE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Update class subject assignment status", description = "Transitions class subject deployment status")
    public ResponseEntity<ApiResponse<ClassSubjectAssignmentDetailResponse>> updateStatus(
            @Parameter(description = "Assignment UUID") @PathVariable UUID id,
            @Valid @RequestBody UpdateClassSubjectAssignmentStatusRequest request) {
        log.info("REST: Update status for class subject assignment id: [{}] to [{}]", id, request.getStatus());
        ClassSubjectAssignmentDetailResponse response = classSubjectAssignmentService.updateStatus(id, request);
        return ResponseEntity.ok(ApiResponse.success("CLASS_SUBJECT_ASSIGNMENT_STATUS_UPDATED", "Class subject assignment status updated successfully", response));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('CLASS_SUBJECT_ASSIGNMENT_DELETE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Delete class subject assignment", description = "Soft-deletes a class subject assignment")
    public ResponseEntity<ApiResponse<Void>> deleteAssignment(
            @Parameter(description = "Assignment UUID") @PathVariable UUID id) {
        log.info("REST: Delete class subject assignment id: [{}]", id);
        classSubjectAssignmentService.deleteAssignment(id);
        return ResponseEntity.ok(ApiResponse.success("CLASS_SUBJECT_ASSIGNMENT_DELETED", "Class subject assignment successfully deleted", null));
    }
}
