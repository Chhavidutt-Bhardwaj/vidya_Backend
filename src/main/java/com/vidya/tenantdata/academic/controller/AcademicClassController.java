package com.vidya.tenantdata.academic.controller;

import com.vidya.common.api.ApiResponse;
import com.vidya.common.api.PagedApiResponse;
import com.vidya.tenantdata.academic.dto.AcademicClassResponse;
import com.vidya.tenantdata.academic.dto.CreateAcademicClassRequest;
import com.vidya.tenantdata.academic.dto.UpdateAcademicClassRequest;
import com.vidya.tenantdata.academic.enums.AcademicClassStatus;
import com.vidya.tenantdata.academic.service.AcademicClassService;
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
@RequestMapping({"/api/v1/tenant/academic-classes", "/api/v1/academic-classes"})
@RequiredArgsConstructor
@Tag(name = "Academic Classes", description = "Class and grade level management for the active tenant")
public class AcademicClassController {

    private final AcademicClassService academicClassService;

    @PostMapping
    @PreAuthorize("hasAuthority('ACADEMIC_CLASS_CREATE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Create academic class / grade")
    public ResponseEntity<ApiResponse<AcademicClassResponse>> createAcademicClass(
            @Valid @RequestBody CreateAcademicClassRequest request) {
        log.info("REST: Create academic class code: [{}]", request.getCode());
        AcademicClassResponse response = academicClassService.createAcademicClass(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("ACADEMIC_CLASS_CREATED", "Academic class created successfully", response));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ACADEMIC_CLASS_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Get academic class by ID")
    public ResponseEntity<ApiResponse<AcademicClassResponse>> getAcademicClassById(@PathVariable UUID id) {
        log.info("REST: Get academic class ID: [{}]", id);
        AcademicClassResponse response = academicClassService.getAcademicClassById(id);
        return ResponseEntity.ok(ApiResponse.success("ACADEMIC_CLASS_FETCHED", "Academic class retrieved successfully", response));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ACADEMIC_CLASS_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "List academic classes with pagination")
    public ResponseEntity<PagedApiResponse<AcademicClassResponse>> listAcademicClasses(
            @RequestParam(required = false) AcademicClassStatus status,
            @Parameter(hidden = true)
            @PageableDefault(size = 20, sort = "sortOrder", direction = Sort.Direction.ASC) Pageable pageable) {
        log.info("REST: List academic classes - status: {}", status);
        Page<AcademicClassResponse> page = academicClassService.searchAcademicClasses(status, pageable);
        return ResponseEntity.ok(PagedApiResponse.of("ACADEMIC_CLASSES_FETCHED", "Academic classes retrieved successfully", page.getContent(), page));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ACADEMIC_CLASS_UPDATE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Update academic class")
    public ResponseEntity<ApiResponse<AcademicClassResponse>> updateAcademicClass(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAcademicClassRequest request) {
        log.info("REST: Update academic class ID: [{}]", id);
        AcademicClassResponse response = academicClassService.updateAcademicClass(id, request);
        return ResponseEntity.ok(ApiResponse.success("ACADEMIC_CLASS_UPDATED", "Academic class updated successfully", response));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ACADEMIC_CLASS_DELETE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Soft delete academic class")
    public ResponseEntity<ApiResponse<Void>> deleteAcademicClass(@PathVariable UUID id) {
        log.info("REST: Delete academic class ID: [{}]", id);
        academicClassService.deleteAcademicClass(id);
        return ResponseEntity.ok(ApiResponse.success("ACADEMIC_CLASS_DELETED", "Academic class deleted successfully", null));
    }
}
