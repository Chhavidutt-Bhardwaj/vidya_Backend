package com.vidya.tenantdata.examination.controller;

import com.vidya.common.api.ApiResponse;
import com.vidya.common.api.PagedApiResponse;
import com.vidya.tenantdata.examination.dto.CreateGradingScaleRequest;
import com.vidya.tenantdata.examination.dto.GradingScaleResponse;
import com.vidya.tenantdata.examination.service.GradingScaleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/exams/grading-scales")
@RequiredArgsConstructor
@Tag(name = "Grading Scales", description = "Grading scales and tier configuration for school examinations")
public class GradingScaleController {

    private final GradingScaleService gradingScaleService;

    @PostMapping
    @PreAuthorize("hasAuthority('GRADING_SCALE_MANAGE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Create grading scale", description = "Defines a new grading scale with percentage boundaries and grade points")
    public ResponseEntity<ApiResponse<GradingScaleResponse>> createGradingScale(
            @Valid @RequestBody CreateGradingScaleRequest request) {
        log.info("REST: Create grading scale: {}", request.getName());
        GradingScaleResponse response = gradingScaleService.createGradingScale(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("GRADING_SCALE_CREATED", "Grading scale created successfully", response));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('GRADING_SCALE_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN') or hasRole('TEACHER')")
    @Operation(summary = "Get grading scale by ID")
    public ResponseEntity<ApiResponse<GradingScaleResponse>> getGradingScaleById(@PathVariable UUID id) {
        log.info("REST: Get grading scale by ID: {}", id);
        GradingScaleResponse response = gradingScaleService.getGradingScaleById(id);
        return ResponseEntity.ok(ApiResponse.success("GRADING_SCALE_FETCHED", "Grading scale retrieved successfully", response));
    }

    @GetMapping("/default")
    @PreAuthorize("hasAuthority('GRADING_SCALE_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN') or hasRole('TEACHER')")
    @Operation(summary = "Get default active grading scale")
    public ResponseEntity<ApiResponse<GradingScaleResponse>> getDefaultGradingScale() {
        GradingScaleResponse response = gradingScaleService.getDefaultGradingScale();
        return ResponseEntity.ok(ApiResponse.success("DEFAULT_GRADING_SCALE_FETCHED", "Default grading scale retrieved", response));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('GRADING_SCALE_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN') or hasRole('TEACHER')")
    @Operation(summary = "List all grading scales with pagination")
    public ResponseEntity<PagedApiResponse<GradingScaleResponse>> listGradingScales(
            @Parameter(hidden = true)
            @PageableDefault(size = 20) Pageable pageable) {
        Page<GradingScaleResponse> page = gradingScaleService.listGradingScales(pageable);
        return ResponseEntity.ok(PagedApiResponse.of("GRADING_SCALES_FETCHED", "Grading scales retrieved successfully", page.getContent(), page));
    }

    @PatchMapping("/{id}/default")
    @PreAuthorize("hasAuthority('GRADING_SCALE_MANAGE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Set grading scale as school default")
    public ResponseEntity<ApiResponse<Void>> setDefaultScale(@PathVariable UUID id) {
        log.info("REST: Set grading scale default: {}", id);
        gradingScaleService.setDefaultGradingScale(id);
        return ResponseEntity.ok(ApiResponse.success("GRADING_SCALE_SET_DEFAULT", "Grading scale set as default"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('GRADING_SCALE_MANAGE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Delete grading scale")
    public ResponseEntity<ApiResponse<Void>> deleteGradingScale(@PathVariable UUID id) {
        log.info("REST: Delete grading scale: {}", id);
        gradingScaleService.deleteGradingScale(id);
        return ResponseEntity.ok(ApiResponse.success("GRADING_SCALE_DELETED", "Grading scale deleted successfully"));
    }
}
