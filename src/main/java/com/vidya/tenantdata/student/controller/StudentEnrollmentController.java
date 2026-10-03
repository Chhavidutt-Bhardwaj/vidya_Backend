package com.vidya.tenantdata.student.controller;

import com.vidya.common.api.ApiResponse;
import com.vidya.common.api.PagedApiResponse;
import com.vidya.tenantdata.student.dto.CreateEnrollmentRequest;
import com.vidya.tenantdata.student.dto.CurrentEnrollmentResponse;
import com.vidya.tenantdata.student.dto.EnrollmentDetailResponse;
import com.vidya.tenantdata.student.dto.EnrollmentResponse;
import com.vidya.tenantdata.student.dto.TransferEnrollmentRequest;
import com.vidya.tenantdata.student.dto.UpdateEnrollmentRequest;
import com.vidya.tenantdata.student.dto.WithdrawEnrollmentRequest;
import com.vidya.tenantdata.student.entity.StudentEnrollmentStatus;
import com.vidya.tenantdata.student.service.StudentEnrollmentService;
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
import org.springframework.web.bind.annotation.GetMapping;
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
@RequiredArgsConstructor
@Tag(name = "Student Academic Enrollments", description = "Student academic year placement, class/section enrollment, transfer and withdrawal operations")
public class StudentEnrollmentController {

    private final StudentEnrollmentService enrollmentService;

    @PostMapping({"/api/v1/tenant/students/{studentId}/enrollments", "/api/v1/students/{studentId}/enrollments"})
    @PreAuthorize("hasAuthority('STUDENT_ENROLL') or hasAuthority('STUDENT_CREATE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Create academic enrollment for student", description = "Enrolls a student in an academic year, class, and optional section")
    public ResponseEntity<ApiResponse<EnrollmentDetailResponse>> createEnrollment(
            @PathVariable UUID studentId,
            @Valid @RequestBody CreateEnrollmentRequest request) {
        log.info("REST: Create enrollment for student [{}] in class [{}]", studentId, request.getAcademicClassId());
        EnrollmentDetailResponse response = enrollmentService.createEnrollment(studentId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("ENROLLMENT_CREATED", "Student successfully enrolled in academic year", response));
    }

    @GetMapping({"/api/v1/tenant/students/{studentId}/enrollments", "/api/v1/students/{studentId}/enrollments"})
    @PreAuthorize("hasAuthority('STUDENT_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "List academic enrollments for student", description = "Retrieves complete historical and active enrollments of a student")
    public ResponseEntity<ApiResponse<List<EnrollmentResponse>>> getStudentEnrollments(
            @PathVariable UUID studentId) {
        log.info("REST: Get enrollments for student [{}]", studentId);
        List<EnrollmentResponse> list = enrollmentService.getEnrollmentsByStudentId(studentId);
        return ResponseEntity.ok(ApiResponse.success("STUDENT_ENROLLMENTS_FETCHED", "Student enrollments retrieved successfully", list));
    }

    @GetMapping({"/api/v1/tenant/students/{studentId}/current-enrollment", "/api/v1/students/{studentId}/current-enrollment"})
    @PreAuthorize("hasAuthority('STUDENT_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Get current active enrollment for student", description = "Retrieves active placement in the current academic year along with enrolled subjects")
    public ResponseEntity<ApiResponse<CurrentEnrollmentResponse>> getCurrentEnrollment(
            @PathVariable UUID studentId) {
        log.info("REST: Get current enrollment for student [{}]", studentId);
        CurrentEnrollmentResponse response = enrollmentService.getCurrentEnrollment(studentId);
        return ResponseEntity.ok(ApiResponse.success("CURRENT_ENROLLMENT_FETCHED", "Current enrollment retrieved successfully", response));
    }

    @GetMapping({"/api/v1/tenant/enrollments/{id}", "/api/v1/enrollments/{id}"})
    @PreAuthorize("hasAuthority('STUDENT_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Get enrollment by UUID", description = "Retrieves full enrollment details including allocated subjects")
    public ResponseEntity<ApiResponse<EnrollmentDetailResponse>> getEnrollmentById(
            @PathVariable UUID id) {
        log.info("REST: Get enrollment by ID [{}]", id);
        EnrollmentDetailResponse response = enrollmentService.getEnrollmentById(id);
        return ResponseEntity.ok(ApiResponse.success("ENROLLMENT_FETCHED", "Enrollment retrieved successfully", response));
    }

    @GetMapping({"/api/v1/tenant/enrollments/code/{code}", "/api/v1/enrollments/code/{code}"})
    @PreAuthorize("hasAuthority('STUDENT_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Get enrollment by business code", description = "Retrieves enrollment details by code (e.g. ENR-A1B2C3D4)")
    public ResponseEntity<ApiResponse<EnrollmentDetailResponse>> getEnrollmentByCode(
            @PathVariable String code) {
        log.info("REST: Get enrollment by code [{}]", code);
        EnrollmentDetailResponse response = enrollmentService.getEnrollmentByCode(code);
        return ResponseEntity.ok(ApiResponse.success("ENROLLMENT_FETCHED", "Enrollment retrieved successfully", response));
    }

    @PutMapping({"/api/v1/tenant/enrollments/{id}", "/api/v1/enrollments/{id}"})
    @PreAuthorize("hasAuthority('STUDENT_UPDATE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Update enrollment", description = "Updates roll number, section, or remarks for an enrollment")
    public ResponseEntity<ApiResponse<EnrollmentDetailResponse>> updateEnrollment(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateEnrollmentRequest request) {
        log.info("REST: Update enrollment ID [{}]", id);
        EnrollmentDetailResponse response = enrollmentService.updateEnrollment(id, request);
        return ResponseEntity.ok(ApiResponse.success("ENROLLMENT_UPDATED", "Enrollment updated successfully", response));
    }

    @PostMapping({"/api/v1/tenant/enrollments/{id}/withdraw", "/api/v1/enrollments/{id}/withdraw"})
    @PreAuthorize("hasAuthority('STUDENT_WITHDRAW') or hasAuthority('STUDENT_UPDATE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Withdraw student enrollment", description = "Marks enrollment as WITHDRAWN with effective date and reason")
    public ResponseEntity<ApiResponse<EnrollmentDetailResponse>> withdrawStudent(
            @PathVariable UUID id,
            @Valid @RequestBody WithdrawEnrollmentRequest request) {
        log.info("REST: Withdraw student enrollment ID [{}]", id);
        EnrollmentDetailResponse response = enrollmentService.withdrawStudent(id, request);
        return ResponseEntity.ok(ApiResponse.success("STUDENT_WITHDRAWN", "Student successfully withdrawn from enrollment", response));
    }

    @PostMapping({"/api/v1/tenant/enrollments/{id}/transfer", "/api/v1/enrollments/{id}/transfer"})
    @PreAuthorize("hasAuthority('STUDENT_TRANSFER') or hasAuthority('STUDENT_UPDATE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Transfer student enrollment", description = "Transfers student to a new class or section preserving historical enrollment")
    public ResponseEntity<ApiResponse<EnrollmentDetailResponse>> transferStudent(
            @PathVariable UUID id,
            @Valid @RequestBody TransferEnrollmentRequest request) {
        log.info("REST: Transfer student enrollment ID [{}] to class [{}] section [{}]", id, request.getNewAcademicClassId(), request.getNewSectionId());
        EnrollmentDetailResponse response = enrollmentService.transferStudent(id, request);
        return ResponseEntity.ok(ApiResponse.success("STUDENT_TRANSFERRED", "Student successfully transferred to new class/section", response));
    }

    @GetMapping({"/api/v1/tenant/enrollments", "/api/v1/enrollments"})
    @PreAuthorize("hasAuthority('STUDENT_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Search enrollments", description = "Searches student academic enrollments across years, classes, and sections with pagination")
    public ResponseEntity<PagedApiResponse<EnrollmentResponse>> searchEnrollments(
            @RequestParam(required = false) UUID studentId,
            @RequestParam(required = false) UUID academicYearId,
            @RequestParam(required = false) UUID academicClassId,
            @RequestParam(required = false) UUID sectionId,
            @RequestParam(required = false) StudentEnrollmentStatus status,
            @RequestParam(required = false) String enrollmentCode,
            @Parameter(hidden = true)
            @PageableDefault(size = 20, sort = "enrollmentDate", direction = Sort.Direction.DESC) Pageable pageable) {
        log.info("REST: Search enrollments - studentId: {}, yearId: {}, classId: {}, sectionId: {}, status: {}",
                studentId, academicYearId, academicClassId, sectionId, status);
        Page<EnrollmentResponse> page = enrollmentService.searchEnrollments(studentId, academicYearId, academicClassId, sectionId, status, enrollmentCode, pageable);
        return ResponseEntity.ok(PagedApiResponse.of("ENROLLMENTS_FETCHED", "Enrollments retrieved successfully", page.getContent(), page));
    }
}
