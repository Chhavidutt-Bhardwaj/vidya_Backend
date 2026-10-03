package com.vidya.tenantdata.academic.controller;

import com.vidya.common.api.ApiResponse;
import com.vidya.common.api.PagedApiResponse;
import com.vidya.tenantdata.academic.dto.AcademicYearResponse;
import com.vidya.tenantdata.academic.dto.CreateAcademicYearRequest;
import com.vidya.tenantdata.academic.dto.UpdateAcademicYearRequest;
import com.vidya.tenantdata.academic.enums.AcademicYearStatus;
import com.vidya.tenantdata.academic.service.AcademicYearService;
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
@RequestMapping({"/api/v1/tenant/academic-years", "/api/v1/academic-years"})
@RequiredArgsConstructor
@Tag(name = "Academic Years", description = "Academic year lifecycle operations for the active tenant")
public class AcademicYearController {

    private final AcademicYearService academicYearService;

    @PostMapping
    @PreAuthorize("hasAuthority('ACADEMIC_YEAR_CREATE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Create academic year", description = "Creates a new academic year in the tenant database")
    public ResponseEntity<ApiResponse<AcademicYearResponse>> createAcademicYear(
            @Valid @RequestBody CreateAcademicYearRequest request) {
        log.info("REST: Create academic year code: [{}]", request.getCode());
        AcademicYearResponse response = academicYearService.createAcademicYear(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("ACADEMIC_YEAR_CREATED", "Academic year created successfully", response));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ACADEMIC_YEAR_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Get academic year by ID")
    public ResponseEntity<ApiResponse<AcademicYearResponse>> getAcademicYearById(@PathVariable UUID id) {
        log.info("REST: Get academic year ID: [{}]", id);
        AcademicYearResponse response = academicYearService.getAcademicYearById(id);
        return ResponseEntity.ok(ApiResponse.success("ACADEMIC_YEAR_FETCHED", "Academic year retrieved successfully", response));
    }

    @GetMapping("/current")
    @PreAuthorize("hasAuthority('ACADEMIC_YEAR_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Get current operational academic year")
    public ResponseEntity<ApiResponse<AcademicYearResponse>> getCurrentAcademicYear() {
        log.info("REST: Get current academic year");
        AcademicYearResponse response = academicYearService.getCurrentAcademicYear();
        return ResponseEntity.ok(ApiResponse.success("CURRENT_ACADEMIC_YEAR_FETCHED", "Current academic year retrieved", response));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ACADEMIC_YEAR_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "List academic years with pagination and filters")
    public ResponseEntity<PagedApiResponse<AcademicYearResponse>> listAcademicYears(
            @RequestParam(required = false) AcademicYearStatus status,
            @RequestParam(required = false) Boolean isCurrent,
            @Parameter(hidden = true)
            @PageableDefault(size = 20, sort = "startDate", direction = Sort.Direction.DESC) Pageable pageable) {
        log.info("REST: List academic years - status: {}, isCurrent: {}", status, isCurrent);
        Page<AcademicYearResponse> page = academicYearService.searchAcademicYears(status, isCurrent, pageable);
        return ResponseEntity.ok(PagedApiResponse.of("ACADEMIC_YEARS_FETCHED", "Academic years retrieved successfully", page.getContent(), page));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ACADEMIC_YEAR_UPDATE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Update academic year details")
    public ResponseEntity<ApiResponse<AcademicYearResponse>> updateAcademicYear(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAcademicYearRequest request) {
        log.info("REST: Update academic year ID: [{}]", id);
        AcademicYearResponse response = academicYearService.updateAcademicYear(id, request);
        return ResponseEntity.ok(ApiResponse.success("ACADEMIC_YEAR_UPDATED", "Academic year updated successfully", response));
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('ACADEMIC_YEAR_UPDATE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Activate academic year")
    public ResponseEntity<ApiResponse<AcademicYearResponse>> activateAcademicYear(@PathVariable UUID id) {
        log.info("REST: Activate academic year ID: [{}]", id);
        AcademicYearResponse response = academicYearService.activateAcademicYear(id);
        return ResponseEntity.ok(ApiResponse.success("ACADEMIC_YEAR_ACTIVATED", "Academic year activated", response));
    }

    @PatchMapping("/{id}/close")
    @PreAuthorize("hasAuthority('ACADEMIC_YEAR_UPDATE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Close academic year")
    public ResponseEntity<ApiResponse<AcademicYearResponse>> closeAcademicYear(@PathVariable UUID id) {
        log.info("REST: Close academic year ID: [{}]", id);
        AcademicYearResponse response = academicYearService.closeAcademicYear(id);
        return ResponseEntity.ok(ApiResponse.success("ACADEMIC_YEAR_CLOSED", "Academic year closed", response));
    }

    @PatchMapping("/{id}/current")
    @PreAuthorize("hasAuthority('ACADEMIC_YEAR_UPDATE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Set academic year as the current operational year")
    public ResponseEntity<ApiResponse<AcademicYearResponse>> setCurrentAcademicYear(@PathVariable UUID id) {
        log.info("REST: Set academic year ID: [{}] as current", id);
        AcademicYearResponse response = academicYearService.setCurrentAcademicYear(id);
        return ResponseEntity.ok(ApiResponse.success("ACADEMIC_YEAR_SET_CURRENT", "Academic year set as current", response));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ACADEMIC_YEAR_DELETE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Soft delete academic year")
    public ResponseEntity<ApiResponse<Void>> deleteAcademicYear(@PathVariable UUID id) {
        log.info("REST: Delete academic year ID: [{}]", id);
        academicYearService.deleteAcademicYear(id);
        return ResponseEntity.ok(ApiResponse.success("ACADEMIC_YEAR_DELETED", "Academic year deleted successfully", null));
    }
}
