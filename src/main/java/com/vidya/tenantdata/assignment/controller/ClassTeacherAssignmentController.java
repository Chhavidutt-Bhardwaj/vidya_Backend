package com.vidya.tenantdata.assignment.controller;

import com.vidya.common.api.ApiResponse;
import com.vidya.common.api.PagedApiResponse;
import com.vidya.tenantdata.assignment.dto.ClassTeacherAssignmentDetailResponse;
import com.vidya.tenantdata.assignment.dto.ClassTeacherAssignmentResponse;
import com.vidya.tenantdata.assignment.dto.CreateClassTeacherAssignmentRequest;
import com.vidya.tenantdata.assignment.dto.ReplaceClassTeacherRequest;
import com.vidya.tenantdata.assignment.dto.UpdateClassTeacherAssignmentRequest;
import com.vidya.tenantdata.assignment.dto.UpdateClassTeacherAssignmentStatusRequest;
import com.vidya.tenantdata.assignment.enums.ClassTeacherAssignmentStatus;
import com.vidya.tenantdata.assignment.service.ClassTeacherAssignmentService;
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
@RequestMapping({"/api/v1/tenant/class-teacher-assignments", "/api/v1/class-teacher-assignments"})
@RequiredArgsConstructor
@Tag(name = "Class Teacher Assignments", description = "Class/Section Teacher designations for the active school tenant")
public class ClassTeacherAssignmentController {

    private final ClassTeacherAssignmentService classTeacherAssignmentService;

    @PostMapping
    @PreAuthorize("hasAuthority('CLASS_TEACHER_ASSIGNMENT_CREATE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Create class teacher assignment", description = "Designates a teacher as the class/section teacher for a specific section")
    public ResponseEntity<ApiResponse<ClassTeacherAssignmentDetailResponse>> createAssignment(
            @Valid @RequestBody CreateClassTeacherAssignmentRequest request) {
        log.info("REST: Create class teacher assignment for staff [{}] section [{}]",
                request.getStaffId(), request.getSectionId());
        ClassTeacherAssignmentDetailResponse response = classTeacherAssignmentService.createAssignment(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("CLASS_TEACHER_ASSIGNMENT_CREATED", "Class teacher assignment successfully created", response));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('CLASS_TEACHER_ASSIGNMENT_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Search class teacher assignments", description = "Lists class teacher assignments with filtering and pagination")
    public ResponseEntity<PagedApiResponse<ClassTeacherAssignmentResponse>> searchAssignments(
            @RequestParam(required = false) UUID staffId,
            @RequestParam(required = false) UUID academicYearId,
            @RequestParam(required = false) UUID academicClassId,
            @RequestParam(required = false) UUID sectionId,
            @RequestParam(required = false) ClassTeacherAssignmentStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        log.info("REST: Search class teacher assignments staffId: [{}], yearId: [{}], classId: [{}], sectionId: [{}]",
                staffId, academicYearId, academicClassId, sectionId);
        Page<ClassTeacherAssignmentResponse> page = classTeacherAssignmentService.searchAssignments(
                staffId, academicYearId, academicClassId, sectionId, status, pageable);
        return ResponseEntity.ok(PagedApiResponse.of(
                "CLASS_TEACHER_ASSIGNMENTS_RETRIEVED", "Class teacher assignments retrieved successfully",
                page.getContent(), page));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('CLASS_TEACHER_ASSIGNMENT_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Get class teacher assignment by ID", description = "Retrieves class teacher assignment details by UUID")
    public ResponseEntity<ApiResponse<ClassTeacherAssignmentDetailResponse>> getAssignment(
            @Parameter(description = "Assignment UUID") @PathVariable UUID id) {
        log.info("REST: Get class teacher assignment by id: [{}]", id);
        ClassTeacherAssignmentDetailResponse response = classTeacherAssignmentService.getAssignment(id);
        return ResponseEntity.ok(ApiResponse.success("CLASS_TEACHER_ASSIGNMENT_RETRIEVED",
                "Class teacher assignment retrieved successfully", response));
    }

    @GetMapping("/code/{assignmentCode}")
    @PreAuthorize("hasAuthority('CLASS_TEACHER_ASSIGNMENT_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Get class teacher assignment by code", description = "Retrieves class teacher assignment details by business code")
    public ResponseEntity<ApiResponse<ClassTeacherAssignmentDetailResponse>> getAssignmentByCode(
            @Parameter(description = "Assignment business code") @PathVariable String assignmentCode) {
        log.info("REST: Get class teacher assignment by code: [{}]", assignmentCode);
        ClassTeacherAssignmentDetailResponse response = classTeacherAssignmentService.getAssignmentByCode(assignmentCode);
        return ResponseEntity.ok(ApiResponse.success("CLASS_TEACHER_ASSIGNMENT_RETRIEVED",
                "Class teacher assignment retrieved successfully", response));
    }

    @GetMapping("/academic-year/{academicYearId}")
    @PreAuthorize("hasAuthority('CLASS_TEACHER_ASSIGNMENT_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Get class teacher assignments by academic year", description = "Retrieves all class teacher assignments for a given academic year")
    public ResponseEntity<ApiResponse<List<ClassTeacherAssignmentResponse>>> getByAcademicYear(
            @Parameter(description = "Academic Year UUID") @PathVariable UUID academicYearId) {
        log.info("REST: Get class teacher assignments for academic year [{}]", academicYearId);
        List<ClassTeacherAssignmentResponse> response = classTeacherAssignmentService.getAssignmentsByAcademicYear(academicYearId);
        return ResponseEntity.ok(ApiResponse.success("CLASS_TEACHER_ASSIGNMENTS_RETRIEVED",
                "Class teacher assignments retrieved successfully", response));
    }

    @GetMapping("/section/{sectionId}")
    @PreAuthorize("hasAuthority('CLASS_TEACHER_ASSIGNMENT_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Get class teacher history for a section", description = "Retrieves all (historical + active) class teacher assignments for a section and academic year")
    public ResponseEntity<ApiResponse<List<ClassTeacherAssignmentResponse>>> getBySection(
            @Parameter(description = "Section UUID") @PathVariable UUID sectionId,
            @RequestParam UUID academicYearId) {
        log.info("REST: Get class teacher assignments for section [{}] year [{}]", sectionId, academicYearId);
        List<ClassTeacherAssignmentResponse> response = classTeacherAssignmentService.getAssignmentsBySection(sectionId, academicYearId);
        return ResponseEntity.ok(ApiResponse.success("CLASS_TEACHER_ASSIGNMENTS_RETRIEVED",
                "Class teacher section history retrieved successfully", response));
    }

    @GetMapping("/section/{sectionId}/current")
    @PreAuthorize("hasAuthority('CLASS_TEACHER_ASSIGNMENT_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Get current class teacher for a section", description = "Retrieves the current ACTIVE class teacher for a specific section and academic year")
    public ResponseEntity<ApiResponse<ClassTeacherAssignmentDetailResponse>> getCurrentClassTeacher(
            @Parameter(description = "Section UUID") @PathVariable UUID sectionId,
            @RequestParam UUID academicYearId) {
        log.info("REST: Get current class teacher for section [{}] year [{}]", sectionId, academicYearId);
        ClassTeacherAssignmentDetailResponse response = classTeacherAssignmentService.getCurrentClassTeacher(sectionId, academicYearId);
        return ResponseEntity.ok(ApiResponse.success("CLASS_TEACHER_RETRIEVED",
                "Current class teacher retrieved successfully", response));
    }

    @GetMapping("/staff/{staffId}")
    @PreAuthorize("hasAuthority('CLASS_TEACHER_ASSIGNMENT_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Get active class teacher assignments for a staff member", description = "Retrieves all active class teacher assignments for a specific staff member")
    public ResponseEntity<ApiResponse<List<ClassTeacherAssignmentResponse>>> getActiveByStaff(
            @Parameter(description = "Staff UUID") @PathVariable UUID staffId,
            @RequestParam(required = false) UUID academicYearId) {
        log.info("REST: Get active class teacher assignments for staff [{}] year [{}]", staffId, academicYearId);
        List<ClassTeacherAssignmentResponse> response = classTeacherAssignmentService.getActiveAssignmentsByStaff(staffId, academicYearId);
        return ResponseEntity.ok(ApiResponse.success("CLASS_TEACHER_ASSIGNMENTS_RETRIEVED",
                "Active class teacher assignments retrieved successfully", response));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('CLASS_TEACHER_ASSIGNMENT_UPDATE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Update class teacher assignment", description = "Updates mutable fields (startDate, endDate, notes) of the assignment")
    public ResponseEntity<ApiResponse<ClassTeacherAssignmentDetailResponse>> updateAssignment(
            @Parameter(description = "Assignment UUID") @PathVariable UUID id,
            @Valid @RequestBody UpdateClassTeacherAssignmentRequest request) {
        log.info("REST: Update class teacher assignment id: [{}]", id);
        ClassTeacherAssignmentDetailResponse response = classTeacherAssignmentService.updateAssignment(id, request);
        return ResponseEntity.ok(ApiResponse.success("CLASS_TEACHER_ASSIGNMENT_UPDATED",
                "Class teacher assignment updated successfully", response));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('CLASS_TEACHER_ASSIGNMENT_STATUS_UPDATE') or hasAuthority('CLASS_TEACHER_ASSIGNMENT_UPDATE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Update class teacher assignment status", description = "Transitions the class teacher assignment lifecycle status")
    public ResponseEntity<ApiResponse<ClassTeacherAssignmentDetailResponse>> updateStatus(
            @Parameter(description = "Assignment UUID") @PathVariable UUID id,
            @Valid @RequestBody UpdateClassTeacherAssignmentStatusRequest request) {
        log.info("REST: Update status for class teacher assignment id: [{}] to [{}]", id, request.getStatus());
        ClassTeacherAssignmentDetailResponse response = classTeacherAssignmentService.updateStatus(id, request);
        return ResponseEntity.ok(ApiResponse.success("CLASS_TEACHER_ASSIGNMENT_STATUS_UPDATED",
                "Class teacher assignment status updated successfully", response));
    }

    @PostMapping("/{id}/replace")
    @PreAuthorize("hasAuthority('CLASS_TEACHER_ASSIGNMENT_UPDATE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Replace class teacher", description = "Atomically completes the current ACTIVE assignment and creates a new one with a different teacher")
    public ResponseEntity<ApiResponse<ClassTeacherAssignmentDetailResponse>> replaceClassTeacher(
            @Parameter(description = "Existing assignment UUID to replace") @PathVariable UUID id,
            @Valid @RequestBody ReplaceClassTeacherRequest request) {
        log.info("REST: Replace class teacher for assignment id: [{}] with new staff [{}]", id, request.getNewStaffId());
        ClassTeacherAssignmentDetailResponse response = classTeacherAssignmentService.replaceClassTeacher(id, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("CLASS_TEACHER_REPLACED",
                        "Class teacher successfully replaced", response));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('CLASS_TEACHER_ASSIGNMENT_DELETE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Delete class teacher assignment", description = "Soft-deletes a class teacher assignment")
    public ResponseEntity<ApiResponse<Void>> deleteAssignment(
            @Parameter(description = "Assignment UUID") @PathVariable UUID id) {
        log.info("REST: Delete class teacher assignment id: [{}]", id);
        classTeacherAssignmentService.deleteAssignment(id);
        return ResponseEntity.ok(ApiResponse.success("CLASS_TEACHER_ASSIGNMENT_DELETED",
                "Class teacher assignment successfully deleted", null));
    }
}
