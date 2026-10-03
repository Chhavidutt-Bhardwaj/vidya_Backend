package com.vidya.tenantdata.assignment.controller;

import com.vidya.common.api.ApiResponse;
import com.vidya.common.api.PagedApiResponse;
import com.vidya.tenantdata.assignment.dto.CreateTeacherAssignmentRequest;
import com.vidya.tenantdata.assignment.dto.TeacherAssignmentDetailResponse;
import com.vidya.tenantdata.assignment.dto.TeacherAssignmentResponse;
import com.vidya.tenantdata.assignment.dto.UpdateTeacherAssignmentRequest;
import com.vidya.tenantdata.assignment.dto.UpdateTeacherAssignmentStatusRequest;
import com.vidya.tenantdata.assignment.enums.TeacherAssignmentStatus;
import com.vidya.tenantdata.assignment.service.TeacherSubjectAssignmentService;
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
@RequestMapping({"/api/v1/tenant/teacher-assignments", "/api/v1/teacher-assignments"})
@RequiredArgsConstructor
@Tag(name = "Teacher Subject Assignments", description = "Teacher to Subject academic assignments for the active school tenant")
public class TeacherSubjectAssignmentController {

    private final TeacherSubjectAssignmentService teacherAssignmentService;

    @PostMapping
    @PreAuthorize("hasAuthority('TEACHER_SUBJECT_ASSIGNMENT_CREATE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Create teacher subject assignment", description = "Assigns a teacher to a subject for an academic class/section")
    public ResponseEntity<ApiResponse<TeacherAssignmentDetailResponse>> createAssignment(
            @Valid @RequestBody CreateTeacherAssignmentRequest request) {
        log.info("REST: Create teacher assignment for teacher [{}] and subject [{}]", request.getTeacherId(), request.getSubjectId());
        TeacherAssignmentDetailResponse response = teacherAssignmentService.createAssignment(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("TEACHER_ASSIGNMENT_CREATED", "Teacher assignment successfully created", response));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('TEACHER_SUBJECT_ASSIGNMENT_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Search teacher assignments", description = "Lists teacher assignments with filtering and pagination")
    public ResponseEntity<PagedApiResponse<TeacherAssignmentResponse>> searchAssignments(
            @RequestParam(required = false) UUID teacherId,
            @RequestParam(required = false) UUID subjectId,
            @RequestParam(required = false) UUID academicYearId,
            @RequestParam(required = false) UUID academicClassId,
            @RequestParam(required = false) UUID sectionId,
            @RequestParam(required = false) TeacherAssignmentStatus status,
            @RequestParam(required = false) Boolean isPrimary,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        log.info("REST: Search teacher assignments teacherId: [{}], subjectId: [{}], yearId: [{}], classId: [{}]",
                teacherId, subjectId, academicYearId, academicClassId);
        Page<TeacherAssignmentResponse> page = teacherAssignmentService.searchAssignments(
                teacherId, subjectId, academicYearId, academicClassId, sectionId, status, isPrimary, pageable);
        return ResponseEntity.ok(PagedApiResponse.of("TEACHER_ASSIGNMENTS_RETRIEVED", "Teacher assignments retrieved successfully", page.getContent(), page));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('TEACHER_SUBJECT_ASSIGNMENT_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Get teacher assignment by ID", description = "Retrieves teacher assignment details by UUID")
    public ResponseEntity<ApiResponse<TeacherAssignmentDetailResponse>> getAssignment(
            @Parameter(description = "Assignment UUID") @PathVariable UUID id) {
        log.info("REST: Get teacher assignment by id: [{}]", id);
        TeacherAssignmentDetailResponse response = teacherAssignmentService.getAssignment(id);
        return ResponseEntity.ok(ApiResponse.success("TEACHER_ASSIGNMENT_RETRIEVED", "Teacher assignment retrieved successfully", response));
    }

    @GetMapping("/code/{assignmentCode}")
    @PreAuthorize("hasAuthority('TEACHER_SUBJECT_ASSIGNMENT_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Get teacher assignment by code", description = "Retrieves teacher assignment details by business code")
    public ResponseEntity<ApiResponse<TeacherAssignmentDetailResponse>> getAssignmentByCode(
            @Parameter(description = "Assignment business code") @PathVariable String assignmentCode) {
        log.info("REST: Get teacher assignment by code: [{}]", assignmentCode);
        TeacherAssignmentDetailResponse response = teacherAssignmentService.getAssignmentByCode(assignmentCode);
        return ResponseEntity.ok(ApiResponse.success("TEACHER_ASSIGNMENT_RETRIEVED", "Teacher assignment retrieved successfully", response));
    }

    @GetMapping("/teacher/{teacherId}")
    @PreAuthorize("hasAuthority('TEACHER_SUBJECT_ASSIGNMENT_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Get active assignments for teacher", description = "Retrieves all active teaching assignments for a specific teacher")
    public ResponseEntity<ApiResponse<List<TeacherAssignmentResponse>>> getActiveAssignmentsByTeacher(
            @Parameter(description = "Teacher Staff UUID") @PathVariable UUID teacherId,
            @RequestParam(required = false) UUID academicYearId) {
        log.info("REST: Get active assignments for teacher [{}] year [{}]", teacherId, academicYearId);
        List<TeacherAssignmentResponse> response = teacherAssignmentService.getActiveAssignmentsByTeacher(teacherId, academicYearId);
        return ResponseEntity.ok(ApiResponse.success("TEACHER_ASSIGNMENTS_RETRIEVED", "Active teacher assignments retrieved successfully", response));
    }

    @GetMapping("/class/{academicClassId}")
    @PreAuthorize("hasAuthority('TEACHER_SUBJECT_ASSIGNMENT_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Get active assignments for class/section", description = "Retrieves all active teacher assignments for a specific class and optional section")
    public ResponseEntity<ApiResponse<List<TeacherAssignmentResponse>>> getActiveAssignmentsByClass(
            @Parameter(description = "Academic Class UUID") @PathVariable UUID academicClassId,
            @RequestParam(required = false) UUID sectionId,
            @RequestParam(required = false) UUID academicYearId) {
        log.info("REST: Get active assignments for class [{}] section [{}] year [{}]", academicClassId, sectionId, academicYearId);
        List<TeacherAssignmentResponse> response = teacherAssignmentService.getActiveAssignmentsByClassAndSection(academicYearId, academicClassId, sectionId);
        return ResponseEntity.ok(ApiResponse.success("TEACHER_ASSIGNMENTS_RETRIEVED", "Active class teacher assignments retrieved successfully", response));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('TEACHER_SUBJECT_ASSIGNMENT_UPDATE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Update teacher assignment", description = "Updates teacher assignment properties")
    public ResponseEntity<ApiResponse<TeacherAssignmentDetailResponse>> updateAssignment(
            @Parameter(description = "Assignment UUID") @PathVariable UUID id,
            @Valid @RequestBody UpdateTeacherAssignmentRequest request) {
        log.info("REST: Update teacher assignment id: [{}]", id);
        TeacherAssignmentDetailResponse response = teacherAssignmentService.updateAssignment(id, request);
        return ResponseEntity.ok(ApiResponse.success("TEACHER_ASSIGNMENT_UPDATED", "Teacher assignment updated successfully", response));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('TEACHER_SUBJECT_ASSIGNMENT_STATUS_UPDATE') or hasAuthority('TEACHER_SUBJECT_ASSIGNMENT_UPDATE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Update teacher assignment status", description = "Transitions teacher assignment status")
    public ResponseEntity<ApiResponse<TeacherAssignmentDetailResponse>> updateStatus(
            @Parameter(description = "Assignment UUID") @PathVariable UUID id,
            @Valid @RequestBody UpdateTeacherAssignmentStatusRequest request) {
        log.info("REST: Update status for teacher assignment id: [{}] to [{}]", id, request.getStatus());
        TeacherAssignmentDetailResponse response = teacherAssignmentService.updateStatus(id, request);
        return ResponseEntity.ok(ApiResponse.success("TEACHER_ASSIGNMENT_STATUS_UPDATED", "Teacher assignment status updated successfully", response));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('TEACHER_SUBJECT_ASSIGNMENT_DELETE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Delete teacher assignment", description = "Soft-deletes a teacher subject assignment")
    public ResponseEntity<ApiResponse<Void>> deleteAssignment(
            @Parameter(description = "Assignment UUID") @PathVariable UUID id) {
        log.info("REST: Delete teacher assignment id: [{}]", id);
        teacherAssignmentService.deleteAssignment(id);
        return ResponseEntity.ok(ApiResponse.success("TEACHER_ASSIGNMENT_DELETED", "Teacher assignment successfully deleted", null));
    }
}
