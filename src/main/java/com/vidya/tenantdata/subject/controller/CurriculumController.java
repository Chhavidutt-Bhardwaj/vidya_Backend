package com.vidya.tenantdata.subject.controller;

import com.vidya.common.api.ApiResponse;
import com.vidya.common.api.PagedApiResponse;
import com.vidya.tenantdata.subject.dto.CreateCurriculumRequest;
import com.vidya.tenantdata.subject.dto.CurriculumDetailResponse;
import com.vidya.tenantdata.subject.dto.CurriculumResponse;
import com.vidya.tenantdata.subject.dto.UpdateCurriculumRequest;
import com.vidya.tenantdata.subject.dto.UpdateCurriculumStatusRequest;
import com.vidya.tenantdata.subject.enums.CurriculumStatus;
import com.vidya.tenantdata.subject.service.CurriculumService;
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
@RequestMapping({"/api/v1/tenant/curricula", "/api/v1/curricula"})
@RequiredArgsConstructor
@Tag(name = "Curricula", description = "Curriculum structures and academic program definitions for the active tenant")
public class CurriculumController {

    private final CurriculumService curriculumService;

    @PostMapping
    @PreAuthorize("hasAuthority('CURRICULUM_CREATE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Create curriculum", description = "Creates a new curriculum structure for an academic year")
    public ResponseEntity<ApiResponse<CurriculumDetailResponse>> createCurriculum(
            @Valid @RequestBody CreateCurriculumRequest request) {
        log.info("REST: Create curriculum [{}] for academic year: [{}]", request.getName(), request.getAcademicYearId());
        CurriculumDetailResponse response = curriculumService.createCurriculum(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("CURRICULUM_CREATED", "Curriculum successfully created", response));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('CURRICULUM_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Search curricula", description = "Lists curricula with optional filtering by academic year, status, or search query")
    public ResponseEntity<PagedApiResponse<CurriculumResponse>> searchCurricula(
            @RequestParam(required = false) UUID academicYearId,
            @RequestParam(required = false) CurriculumStatus status,
            @RequestParam(required = false) String query,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        log.info("REST: Search curricula academicYearId: [{}], status: [{}], query: [{}]", academicYearId, status, query);
        Page<CurriculumResponse> page = curriculumService.searchCurricula(academicYearId, status, query, pageable);
        return ResponseEntity.ok(PagedApiResponse.of("CURRICULA_RETRIEVED", "Curricula retrieved successfully", page.getContent(), page));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('CURRICULUM_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Get curriculum by ID", description = "Retrieves curriculum details with mapped subjects by its UUID")
    public ResponseEntity<ApiResponse<CurriculumDetailResponse>> getCurriculum(
            @Parameter(description = "Curriculum UUID") @PathVariable UUID id) {
        log.info("REST: Get curriculum by id: [{}]", id);
        CurriculumDetailResponse response = curriculumService.getCurriculum(id);
        return ResponseEntity.ok(ApiResponse.success("CURRICULUM_RETRIEVED", "Curriculum retrieved successfully", response));
    }

    @GetMapping("/code/{curriculumCode}")
    @PreAuthorize("hasAuthority('CURRICULUM_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Get curriculum by code", description = "Retrieves curriculum details by its stable business code")
    public ResponseEntity<ApiResponse<CurriculumDetailResponse>> getCurriculumByCode(
            @Parameter(description = "Curriculum business code") @PathVariable String curriculumCode) {
        log.info("REST: Get curriculum by code: [{}]", curriculumCode);
        CurriculumDetailResponse response = curriculumService.getCurriculumByCode(curriculumCode);
        return ResponseEntity.ok(ApiResponse.success("CURRICULUM_RETRIEVED", "Curriculum retrieved successfully", response));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('CURRICULUM_UPDATE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Update curriculum", description = "Updates curriculum name and description")
    public ResponseEntity<ApiResponse<CurriculumDetailResponse>> updateCurriculum(
            @Parameter(description = "Curriculum UUID") @PathVariable UUID id,
            @Valid @RequestBody UpdateCurriculumRequest request) {
        log.info("REST: Update curriculum id: [{}]", id);
        CurriculumDetailResponse response = curriculumService.updateCurriculum(id, request);
        return ResponseEntity.ok(ApiResponse.success("CURRICULUM_UPDATED", "Curriculum successfully updated", response));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('CURRICULUM_STATUS_UPDATE') or hasAuthority('CURRICULUM_UPDATE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Update curriculum status", description = "Transitions curriculum lifecycle status (e.g. DRAFT -> ACTIVE)")
    public ResponseEntity<ApiResponse<CurriculumDetailResponse>> updateStatus(
            @Parameter(description = "Curriculum UUID") @PathVariable UUID id,
            @Valid @RequestBody UpdateCurriculumStatusRequest request) {
        log.info("REST: Update status for curriculum id: [{}] to [{}]", id, request.getStatus());
        CurriculumDetailResponse response = curriculumService.updateStatus(id, request);
        return ResponseEntity.ok(ApiResponse.success("CURRICULUM_STATUS_UPDATED", "Curriculum status updated successfully", response));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('CURRICULUM_DELETE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Delete curriculum", description = "Soft-deletes a curriculum")
    public ResponseEntity<ApiResponse<Void>> deleteCurriculum(
            @Parameter(description = "Curriculum UUID") @PathVariable UUID id) {
        log.info("REST: Delete curriculum id: [{}]", id);
        curriculumService.deleteCurriculum(id);
        return ResponseEntity.ok(ApiResponse.success("CURRICULUM_DELETED", "Curriculum deleted successfully", null));
    }
}
