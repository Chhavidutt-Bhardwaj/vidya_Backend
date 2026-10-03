package com.vidya.tenantdata.student.controller;

import com.vidya.common.api.ApiResponse;
import com.vidya.tenantdata.student.dto.CreateStudentGuardianRequest;
import com.vidya.tenantdata.student.dto.StudentGuardianResponse;
import com.vidya.tenantdata.student.dto.UpdateStudentGuardianRequest;
import com.vidya.tenantdata.student.service.StudentGuardianService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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
@RequestMapping({"/api/v1/tenant/students/{studentId}/guardians", "/api/v1/students/{studentId}/guardians"})
@RequiredArgsConstructor
@Tag(name = "Student Guardians", description = "Parent, guardian, and emergency contact management for students")
public class StudentGuardianController {

    private final StudentGuardianService guardianService;

    @GetMapping
    @PreAuthorize("hasAuthority('STUDENT_GUARDIAN_READ') or hasAuthority('STUDENT_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Get guardians for student", description = "Retrieves all parent/guardian records for a student")
    public ResponseEntity<ApiResponse<List<StudentGuardianResponse>>> getGuardians(
            @PathVariable UUID studentId) {
        log.info("REST: Get guardians for student ID [{}]", studentId);
        List<StudentGuardianResponse> list = guardianService.getGuardiansByStudentId(studentId);
        return ResponseEntity.ok(ApiResponse.success("GUARDIANS_FETCHED", "Guardians retrieved successfully", list));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('STUDENT_GUARDIAN_CREATE') or hasAuthority('STUDENT_UPDATE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Add guardian for student", description = "Records a parent/guardian for a student")
    public ResponseEntity<ApiResponse<StudentGuardianResponse>> createGuardian(
            @PathVariable UUID studentId,
            @Valid @RequestBody CreateStudentGuardianRequest request) {
        log.info("REST: Create guardian for student ID [{}]", studentId);
        StudentGuardianResponse response = guardianService.createGuardian(studentId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("GUARDIAN_CREATED", "Guardian successfully recorded", response));
    }

    @GetMapping("/{guardianId}")
    @PreAuthorize("hasAuthority('STUDENT_GUARDIAN_READ') or hasAuthority('STUDENT_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Get guardian by ID", description = "Retrieves guardian details")
    public ResponseEntity<ApiResponse<StudentGuardianResponse>> getGuardianById(
            @PathVariable UUID studentId,
            @PathVariable UUID guardianId) {
        log.info("REST: Get guardian ID [{}] for student [{}]", guardianId, studentId);
        StudentGuardianResponse response = guardianService.getGuardianById(studentId, guardianId);
        return ResponseEntity.ok(ApiResponse.success("GUARDIAN_FETCHED", "Guardian retrieved successfully", response));
    }

    @PutMapping("/{guardianId}")
    @PreAuthorize("hasAuthority('STUDENT_GUARDIAN_UPDATE') or hasAuthority('STUDENT_UPDATE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Update guardian", description = "Updates guardian contact, occupation, or status")
    public ResponseEntity<ApiResponse<StudentGuardianResponse>> updateGuardian(
            @PathVariable UUID studentId,
            @PathVariable UUID guardianId,
            @Valid @RequestBody UpdateStudentGuardianRequest request) {
        log.info("REST: Update guardian ID [{}] for student [{}]", guardianId, studentId);
        StudentGuardianResponse response = guardianService.updateGuardian(studentId, guardianId, request);
        return ResponseEntity.ok(ApiResponse.success("GUARDIAN_UPDATED", "Guardian successfully updated", response));
    }

    @DeleteMapping("/{guardianId}")
    @PreAuthorize("hasAuthority('STUDENT_GUARDIAN_DELETE') or hasAuthority('STUDENT_UPDATE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Delete guardian", description = "Soft deletes a guardian record")
    public ResponseEntity<ApiResponse<Void>> deleteGuardian(
            @PathVariable UUID studentId,
            @PathVariable UUID guardianId) {
        log.info("REST: Delete guardian ID [{}] for student [{}]", guardianId, studentId);
        guardianService.deleteGuardian(studentId, guardianId);
        return ResponseEntity.ok(ApiResponse.success("GUARDIAN_DELETED", "Guardian successfully deleted", null));
    }
}
