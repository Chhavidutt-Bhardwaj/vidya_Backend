package com.vidya.tenantdata.student.controller;

import com.vidya.common.api.ApiResponse;
import com.vidya.tenantdata.student.dto.BatchAssignSubjectsRequest;
import com.vidya.tenantdata.student.dto.CreateStudentSubjectEnrollmentRequest;
import com.vidya.tenantdata.student.dto.StudentSubjectEnrollmentResponse;
import com.vidya.tenantdata.student.dto.UpdateStudentSubjectEnrollmentRequest;
import com.vidya.tenantdata.student.service.StudentSubjectEnrollmentService;
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
@RequestMapping({"/api/v1/tenant/enrollments/{enrollmentId}/subjects", "/api/v1/enrollments/{enrollmentId}/subjects"})
@RequiredArgsConstructor
@Tag(name = "Student Subject Enrollments", description = "Subject allocation and elective management for student academic enrollments")
public class StudentSubjectEnrollmentController {

    private final StudentSubjectEnrollmentService subjectEnrollmentService;

    @GetMapping
    @PreAuthorize("hasAuthority('STUDENT_SUBJECT_READ') or hasAuthority('STUDENT_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Get enrolled subjects for student enrollment", description = "Retrieves all active and historical subjects enrolled under this academic placement")
    public ResponseEntity<ApiResponse<List<StudentSubjectEnrollmentResponse>>> getEnrolledSubjects(
            @PathVariable UUID enrollmentId) {
        log.info("REST: Get enrolled subjects for enrollment ID [{}]", enrollmentId);
        List<StudentSubjectEnrollmentResponse> list = subjectEnrollmentService.getEnrolledSubjects(enrollmentId);
        return ResponseEntity.ok(ApiResponse.success("ENROLLED_SUBJECTS_FETCHED", "Enrolled subjects retrieved successfully", list));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('STUDENT_SUBJECT_ASSIGN') or hasAuthority('STUDENT_UPDATE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Enroll student in a subject", description = "Assigns a subject to a student enrollment after validating curriculum eligibility")
    public ResponseEntity<ApiResponse<StudentSubjectEnrollmentResponse>> enrollSubject(
            @PathVariable UUID enrollmentId,
            @Valid @RequestBody CreateStudentSubjectEnrollmentRequest request) {
        log.info("REST: Enroll subject [{}] for enrollment [{}]", request.getSubjectId(), enrollmentId);
        StudentSubjectEnrollmentResponse response = subjectEnrollmentService.enrollSubject(enrollmentId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("SUBJECT_ENROLLED", "Student successfully enrolled in subject", response));
    }

    @PostMapping("/batch")
    @PreAuthorize("hasAuthority('STUDENT_SUBJECT_ASSIGN') or hasAuthority('STUDENT_UPDATE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Batch enroll student in subjects", description = "Assigns multiple subjects in batch to a student enrollment")
    public ResponseEntity<ApiResponse<List<StudentSubjectEnrollmentResponse>>> batchEnrollSubjects(
            @PathVariable UUID enrollmentId,
            @Valid @RequestBody BatchAssignSubjectsRequest request) {
        log.info("REST: Batch enroll [{}] subjects for enrollment [{}]", request.getSubjectIds().size(), enrollmentId);
        List<StudentSubjectEnrollmentResponse> responses = subjectEnrollmentService.batchEnrollSubjects(enrollmentId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("SUBJECTS_BATCH_ENROLLED", "Subjects successfully enrolled in batch", responses));
    }

    @GetMapping("/{subjectEnrollmentId}")
    @PreAuthorize("hasAuthority('STUDENT_SUBJECT_READ') or hasAuthority('STUDENT_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Get subject enrollment by ID", description = "Retrieves subject enrollment details")
    public ResponseEntity<ApiResponse<StudentSubjectEnrollmentResponse>> getSubjectEnrollmentById(
            @PathVariable UUID enrollmentId,
            @PathVariable UUID subjectEnrollmentId) {
        log.info("REST: Get subject enrollment ID [{}] for enrollment [{}]", subjectEnrollmentId, enrollmentId);
        StudentSubjectEnrollmentResponse response = subjectEnrollmentService.getSubjectEnrollmentById(enrollmentId, subjectEnrollmentId);
        return ResponseEntity.ok(ApiResponse.success("SUBJECT_ENROLLMENT_FETCHED", "Subject enrollment retrieved successfully", response));
    }

    @PutMapping("/{subjectEnrollmentId}")
    @PreAuthorize("hasAuthority('STUDENT_SUBJECT_UPDATE') or hasAuthority('STUDENT_UPDATE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Update subject enrollment", description = "Updates status (DROPPED, EXEMPTED, COMPLETED) or effective dates")
    public ResponseEntity<ApiResponse<StudentSubjectEnrollmentResponse>> updateSubjectEnrollment(
            @PathVariable UUID enrollmentId,
            @PathVariable UUID subjectEnrollmentId,
            @Valid @RequestBody UpdateStudentSubjectEnrollmentRequest request) {
        log.info("REST: Update subject enrollment ID [{}] for enrollment [{}]", subjectEnrollmentId, enrollmentId);
        StudentSubjectEnrollmentResponse response = subjectEnrollmentService.updateSubjectEnrollment(enrollmentId, subjectEnrollmentId, request);
        return ResponseEntity.ok(ApiResponse.success("SUBJECT_ENROLLMENT_UPDATED", "Subject enrollment updated successfully", response));
    }

    @DeleteMapping("/{subjectEnrollmentId}")
    @PreAuthorize("hasAuthority('STUDENT_SUBJECT_DELETE') or hasAuthority('STUDENT_UPDATE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Remove subject enrollment", description = "Soft deletes subject enrollment for student")
    public ResponseEntity<ApiResponse<Void>> removeSubjectEnrollment(
            @PathVariable UUID enrollmentId,
            @PathVariable UUID subjectEnrollmentId) {
        log.info("REST: Delete subject enrollment ID [{}] for enrollment [{}]", subjectEnrollmentId, enrollmentId);
        subjectEnrollmentService.removeSubjectEnrollment(enrollmentId, subjectEnrollmentId);
        return ResponseEntity.ok(ApiResponse.success("SUBJECT_ENROLLMENT_DELETED", "Subject enrollment successfully removed", null));
    }
}
