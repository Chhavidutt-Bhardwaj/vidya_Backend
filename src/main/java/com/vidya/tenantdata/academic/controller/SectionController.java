package com.vidya.tenantdata.academic.controller;

import com.vidya.common.api.ApiResponse;
import com.vidya.common.api.PagedApiResponse;
import com.vidya.tenantdata.academic.dto.CreateSectionRequest;
import com.vidya.tenantdata.academic.dto.SectionResponse;
import com.vidya.tenantdata.academic.dto.UpdateSectionRequest;
import com.vidya.tenantdata.academic.enums.SectionStatus;
import com.vidya.tenantdata.academic.service.SectionService;
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
@RequestMapping({"/api/v1/tenant/sections", "/api/v1/sections"})
@RequiredArgsConstructor
@Tag(name = "Sections", description = "Class section management for active tenant and academic year")
public class SectionController {

    private final SectionService sectionService;

    @PostMapping
    @PreAuthorize("hasAuthority('SECTION_CREATE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Create section")
    public ResponseEntity<ApiResponse<SectionResponse>> createSection(
            @Valid @RequestBody CreateSectionRequest request) {
        log.info("REST: Create section code: [{}] for class: [{}] in year: [{}]",
                request.getCode(), request.getAcademicClassId(), request.getAcademicYearId());
        SectionResponse response = sectionService.createSection(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("SECTION_CREATED", "Section created successfully", response));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SECTION_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Get section by ID")
    public ResponseEntity<ApiResponse<SectionResponse>> getSectionById(@PathVariable UUID id) {
        log.info("REST: Get section ID: [{}]", id);
        SectionResponse response = sectionService.getSectionById(id);
        return ResponseEntity.ok(ApiResponse.success("SECTION_FETCHED", "Section retrieved successfully", response));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SECTION_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "List sections with filters and pagination")
    public ResponseEntity<PagedApiResponse<SectionResponse>> listSections(
            @RequestParam(required = false) UUID academicYearId,
            @RequestParam(required = false) UUID academicClassId,
            @RequestParam(required = false) SectionStatus status,
            @Parameter(hidden = true)
            @PageableDefault(size = 20, sort = "code", direction = Sort.Direction.ASC) Pageable pageable) {
        log.info("REST: List sections - academicYearId: {}, academicClassId: {}, status: {}", academicYearId, academicClassId, status);
        Page<SectionResponse> page = sectionService.searchSections(academicYearId, academicClassId, status, pageable);
        return ResponseEntity.ok(PagedApiResponse.of("SECTIONS_FETCHED", "Sections retrieved successfully", page.getContent(), page));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('SECTION_UPDATE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Update section")
    public ResponseEntity<ApiResponse<SectionResponse>> updateSection(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateSectionRequest request) {
        log.info("REST: Update section ID: [{}]", id);
        SectionResponse response = sectionService.updateSection(id, request);
        return ResponseEntity.ok(ApiResponse.success("SECTION_UPDATED", "Section updated successfully", response));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('SECTION_DELETE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Soft delete section")
    public ResponseEntity<ApiResponse<Void>> deleteSection(@PathVariable UUID id) {
        log.info("REST: Delete section ID: [{}]", id);
        sectionService.deleteSection(id);
        return ResponseEntity.ok(ApiResponse.success("SECTION_DELETED", "Section deleted successfully", null));
    }
}
