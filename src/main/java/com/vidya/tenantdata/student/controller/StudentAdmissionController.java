package com.vidya.tenantdata.student.controller;

import com.vidya.common.api.ApiResponse;
import com.vidya.tenantdata.student.dto.AdmissionResponse;
import com.vidya.tenantdata.student.dto.CreateAdmissionRequest;
import com.vidya.tenantdata.student.service.StudentAdmissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping({"/api/v1/tenant/admissions", "/api/v1/admissions", "/api/v1/tenant/students/admissions", "/api/v1/students/admissions"})
@RequiredArgsConstructor
@Tag(name = "Student Admissions", description = "Transactional student admission lifecycle foundation")
public class StudentAdmissionController {

    private final StudentAdmissionService admissionService;

    @PostMapping
    @PreAuthorize("hasAuthority('STUDENT_CREATE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Complete student admission", description = "Atomically creates Student Identity, User Profile, Academic Enrollment, Guardians, and assigns initial subjects in one transaction")
    public ResponseEntity<ApiResponse<AdmissionResponse>> admitStudent(
            @Valid @RequestBody CreateAdmissionRequest request) {
        log.info("REST: Process admission for admissionNumber: [{}]", request.getAdmissionNumber());
        AdmissionResponse response = admissionService.admitStudent(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("STUDENT_ADMISSION_COMPLETED", "Student admission successfully completed", response));
    }
}
