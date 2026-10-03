package com.vidya.tenantdata.academic.controller;

import com.vidya.common.api.ApiResponse;
import com.vidya.tenantdata.academic.dto.AssignStudentRequest;
import com.vidya.tenantdata.academic.dto.StudentAcademicAssignmentResponse;
import com.vidya.tenantdata.academic.dto.TransferStudentAssignmentRequest;
import com.vidya.tenantdata.academic.dto.UpdateStudentAssignmentRequest;
import com.vidya.tenantdata.academic.service.StudentAcademicAssignmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping({"/api/v1/tenant/students/{studentId}/academic-assignments", "/api/v1/students/{studentId}/academic-assignments"})
@RequiredArgsConstructor
@Tag(name = "Student Academic Assignments", description = "Student class & section placement and academic transfer operations")
public class StudentAcademicAssignmentController {

    private final StudentAcademicAssignmentService assignmentService;

    @PostMapping
    @PreAuthorize("hasAuthority('STUDENT_ACADEMIC_ASSIGNMENT_CREATE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Assign student to class & section", description = "Places student into an academic year class and section")
    public ResponseEntity<ApiResponse<StudentAcademicAssignmentResponse>> assignStudent(
            @PathVariable UUID studentId,
            @Valid @RequestBody AssignStudentRequest request) {
        log.info("REST: Assign student: [{}] to section: [{}]", studentId, request.getSectionId());
        StudentAcademicAssignmentResponse response = assignmentService.assignStudent(studentId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("STUDENT_ASSIGNED", "Student assigned to class section successfully", response));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('STUDENT_ACADEMIC_ASSIGNMENT_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Get all academic placement history for a student")
    public ResponseEntity<ApiResponse<List<StudentAcademicAssignmentResponse>>> getStudentAssignments(
            @PathVariable UUID studentId) {
        log.info("REST: Get assignments for student: [{}]", studentId);
        List<StudentAcademicAssignmentResponse> responses = assignmentService.getStudentAssignments(studentId);
        return ResponseEntity.ok(ApiResponse.success("STUDENT_ASSIGNMENTS_FETCHED", "Student assignments retrieved successfully", responses));
    }

    @GetMapping("/current")
    @PreAuthorize("hasAuthority('STUDENT_ACADEMIC_ASSIGNMENT_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Get current active academic assignment for a student")
    public ResponseEntity<ApiResponse<StudentAcademicAssignmentResponse>> getCurrentAssignment(
            @PathVariable UUID studentId) {
        log.info("REST: Get current assignment for student: [{}]", studentId);
        StudentAcademicAssignmentResponse response = assignmentService.getCurrentAssignment(studentId);
        return ResponseEntity.ok(ApiResponse.success("CURRENT_ASSIGNMENT_FETCHED", "Current assignment retrieved successfully", response));
    }

    @GetMapping("/{assignmentId}")
    @PreAuthorize("hasAuthority('STUDENT_ACADEMIC_ASSIGNMENT_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Get student assignment by ID")
    public ResponseEntity<ApiResponse<StudentAcademicAssignmentResponse>> getAssignmentById(
            @PathVariable UUID studentId,
            @PathVariable UUID assignmentId) {
        log.info("REST: Get assignment ID: [{}] for student: [{}]", assignmentId, studentId);
        StudentAcademicAssignmentResponse response = assignmentService.getAssignmentById(studentId, assignmentId);
        return ResponseEntity.ok(ApiResponse.success("STUDENT_ASSIGNMENT_FETCHED", "Student assignment retrieved successfully", response));
    }

    @PutMapping("/{assignmentId}")
    @PreAuthorize("hasAuthority('STUDENT_ACADEMIC_ASSIGNMENT_UPDATE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Update student assignment details (roll number/dates)")
    public ResponseEntity<ApiResponse<StudentAcademicAssignmentResponse>> updateAssignment(
            @PathVariable UUID studentId,
            @PathVariable UUID assignmentId,
            @Valid @RequestBody UpdateStudentAssignmentRequest request) {
        log.info("REST: Update assignment ID: [{}] for student: [{}]", assignmentId, studentId);
        StudentAcademicAssignmentResponse response = assignmentService.updateAssignment(studentId, assignmentId, request);
        return ResponseEntity.ok(ApiResponse.success("STUDENT_ASSIGNMENT_UPDATED", "Student assignment updated successfully", response));
    }

    @PatchMapping("/{assignmentId}/transfer")
    @PreAuthorize("hasAuthority('STUDENT_ACADEMIC_ASSIGNMENT_UPDATE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Transfer student to a new section or class", description = "Marks current assignment as TRANSFERRED and opens a new active placement")
    public ResponseEntity<ApiResponse<StudentAcademicAssignmentResponse>> transferStudent(
            @PathVariable UUID studentId,
            @PathVariable UUID assignmentId,
            @Valid @RequestBody TransferStudentAssignmentRequest request) {
        log.info("REST: Transfer student: [{}] from assignment: [{}] to class: [{}], section: [{}]",
                studentId, assignmentId, request.getNewAcademicClassId(), request.getNewSectionId());
        StudentAcademicAssignmentResponse response = assignmentService.transferStudent(studentId, assignmentId, request);
        return ResponseEntity.ok(ApiResponse.success("STUDENT_TRANSFERRED", "Student successfully transferred to new placement", response));
    }
}
