package com.vidya.tenantdata.student.controller;

import com.vidya.common.api.ApiResponse;
import com.vidya.common.api.PagedApiResponse;
import com.vidya.tenantdata.student.dto.CreateStudentRequest;
import com.vidya.tenantdata.student.dto.StudentDetailResponse;
import com.vidya.tenantdata.student.dto.StudentResponse;
import com.vidya.tenantdata.student.dto.UpdateStudentRequest;
import com.vidya.tenantdata.student.dto.UpdateStudentStatusRequest;
import com.vidya.tenantdata.student.entity.StudentStatus;
import com.vidya.tenantdata.student.service.StudentService;
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
@RequestMapping({"/api/v1/tenant/students", "/api/v1/students"})
@RequiredArgsConstructor
@Tag(name = "Students", description = "Student domain operations for the active school tenant")
public class StudentController {

    private final StudentService studentService;

    @PostMapping
    @PreAuthorize("hasAuthority('STUDENT_CREATE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Enroll/create a student", description = "Enrolls a new student linked to an existing tenant operational user")
    public ResponseEntity<ApiResponse<StudentDetailResponse>> createStudent(
            @Valid @RequestBody CreateStudentRequest request) {
        log.info("REST: Create student with admissionNumber: [{}]", request.getAdmissionNumber());
        StudentDetailResponse response = studentService.createStudent(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("STUDENT_CREATED", "Student successfully enrolled", response));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('STUDENT_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Get student by UUID", description = "Retrieves student record including operational profile")
    public ResponseEntity<ApiResponse<StudentDetailResponse>> getStudentById(
            @PathVariable UUID id) {
        log.info("REST: Get student by ID: [{}]", id);
        StudentDetailResponse response = studentService.getStudentById(id);
        return ResponseEntity.ok(ApiResponse.success("STUDENT_FETCHED", "Student retrieved successfully", response));
    }

    @GetMapping("/code/{studentCode}")
    @PreAuthorize("hasAuthority('STUDENT_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Get student by code", description = "Retrieves student record by human-readable student code (e.g. STU-A1B2C3D4)")
    public ResponseEntity<ApiResponse<StudentDetailResponse>> getStudentByCode(
            @PathVariable String studentCode) {
        log.info("REST: Get student by code: [{}]", studentCode);
        StudentDetailResponse response = studentService.getStudentByStudentCode(studentCode);
        return ResponseEntity.ok(ApiResponse.success("STUDENT_FETCHED", "Student retrieved successfully", response));
    }

    @GetMapping("/{id}/profile")
    @PreAuthorize("hasAuthority('STUDENT_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Get student profile", description = "Retrieves comprehensive student profile including identity, current active enrollment, subjects, and guardians")
    public ResponseEntity<ApiResponse<com.vidya.tenantdata.student.dto.StudentProfileResponse>> getStudentProfile(
            @PathVariable UUID id) {
        log.info("REST: Get student profile ID: [{}]", id);
        com.vidya.tenantdata.student.dto.StudentProfileResponse response = studentService.getStudentProfile(id);
        return ResponseEntity.ok(ApiResponse.success("STUDENT_PROFILE_FETCHED", "Student profile retrieved successfully", response));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('STUDENT_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Search and list students", description = "Lists students in current tenant database with search and pagination")
    public ResponseEntity<PagedApiResponse<StudentResponse>> listStudents(
            @RequestParam(required = false) StudentStatus status,
            @RequestParam(required = false) String admissionNumber,
            @RequestParam(required = false) String studentCode,
            @RequestParam(required = false) String q,
            @Parameter(hidden = true)
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        log.info("REST: List students - status: {}, admissionNumber: {}, studentCode: {}, query: {}", status, admissionNumber, studentCode, q);
        Page<StudentResponse> page = studentService.searchStudents(status, admissionNumber, studentCode, q, pageable);
        return ResponseEntity.ok(PagedApiResponse.of("STUDENTS_FETCHED", "Students retrieved successfully", page.getContent(), page));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('STUDENT_UPDATE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Update student", description = "Updates student admission number and date")
    public ResponseEntity<ApiResponse<StudentDetailResponse>> updateStudent(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateStudentRequest request) {
        log.info("REST: Update student ID: [{}]", id);
        StudentDetailResponse response = studentService.updateStudent(id, request);
        return ResponseEntity.ok(ApiResponse.success("STUDENT_UPDATED", "Student successfully updated", response));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('STUDENT_UPDATE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Update student status", description = "Transitions student lifecycle status")
    public ResponseEntity<ApiResponse<StudentDetailResponse>> updateStudentStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateStudentStatusRequest request) {
        log.info("REST: Update student ID: [{}] status to: [{}]", id, request.getStatus());
        StudentDetailResponse response = studentService.updateStudentStatus(id, request);
        return ResponseEntity.ok(ApiResponse.success("STUDENT_STATUS_UPDATED", "Student status successfully updated", response));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('STUDENT_DELETE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Soft delete student", description = "Soft deletes a student operational record")
    public ResponseEntity<ApiResponse<Void>> deleteStudent(@PathVariable UUID id) {
        log.info("REST: Delete student ID: [{}]", id);
        studentService.deleteStudent(id);
        return ResponseEntity.ok(ApiResponse.success("STUDENT_DELETED", "Student successfully deleted", null));
    }
}
