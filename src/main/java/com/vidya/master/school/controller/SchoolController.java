package com.vidya.master.school.controller;

import com.vidya.common.api.ApiResponse;
import com.vidya.common.api.ApiResponseCode;
import com.vidya.common.api.PagedApiResponse;
import com.vidya.master.school.dto.CreateBranchRequest;
import com.vidya.master.school.dto.CreateSchoolRequest;
import com.vidya.master.school.dto.MasterStatsResponse;
import com.vidya.master.school.dto.SchoolBranchResponse;
import com.vidya.master.school.dto.SchoolResponse;
import com.vidya.master.school.dto.UpdateBranchRequest;
import com.vidya.master.school.dto.UpdateSchoolRequest;
import com.vidya.master.school.entity.SchoolBoard;
import com.vidya.master.school.entity.SchoolStatus;
import com.vidya.master.school.service.SchoolService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

/**
 * REST Controller for managing School master records and Branches.
 */
@RestController
@RequestMapping("/api/v1/schools")
@RequiredArgsConstructor
public class SchoolController {

    private final SchoolService schoolService;

    @PostMapping
    public ResponseEntity<ApiResponse<SchoolResponse>> createSchool(
            @Valid @RequestBody CreateSchoolRequest request) {
        SchoolResponse response = schoolService.createSchool(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        ApiResponseCode.SCHOOL_FOUND,
                        "School registered successfully",
                        response
                ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SchoolResponse>> getSchoolById(@PathVariable UUID id) {
        SchoolResponse response = schoolService.getSchoolById(id);
        return ResponseEntity.ok(ApiResponse.success(
                ApiResponseCode.SCHOOL_FOUND,
                "School retrieved successfully",
                response
        ));
    }

    @GetMapping("/code/{code}")
    public ResponseEntity<ApiResponse<SchoolResponse>> getSchoolByCode(@PathVariable String code) {
        SchoolResponse response = schoolService.getSchoolByCode(code);
        return ResponseEntity.ok(ApiResponse.success(
                ApiResponseCode.SCHOOL_FOUND,
                "School retrieved successfully",
                response
        ));
    }

    @GetMapping("/tenant/{tenantId}")
    public ResponseEntity<ApiResponse<SchoolResponse>> getSchoolByTenantId(@PathVariable UUID tenantId) {
        SchoolResponse response = schoolService.getSchoolByTenantId(tenantId);
        return ResponseEntity.ok(ApiResponse.success(
                ApiResponseCode.SCHOOL_FOUND,
                "School retrieved successfully",
                response
        ));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<MasterStatsResponse>> getMasterStats() {
        MasterStatsResponse response = schoolService.getMasterStats();
        return ResponseEntity.ok(ApiResponse.success(
                ApiResponseCode.SUCCESS,
                "Master statistics retrieved successfully",
                response
        ));
    }

    @GetMapping
    public ResponseEntity<PagedApiResponse<SchoolResponse>> getAllSchools(
            @RequestParam(required = false) SchoolStatus status,
            @RequestParam(required = false) SchoolBoard board,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "schoolName") Pageable pageable) {

        Page<SchoolResponse> page = schoolService.getAllSchools(pageable, status, board, search);
        return ResponseEntity.ok(PagedApiResponse.of(
                ApiResponseCode.SCHOOLS_FOUND,
                "Schools retrieved successfully",
                page.getContent(),
                page
        ));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<SchoolResponse>> updateSchool(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateSchoolRequest request) {
        SchoolResponse response = schoolService.updateSchool(id, request);
        return ResponseEntity.ok(ApiResponse.success(
                ApiResponseCode.SCHOOL_FOUND,
                "School updated successfully",
                response
        ));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<SchoolResponse>> changeSchoolStatus(
            @PathVariable UUID id,
            @RequestParam SchoolStatus status) {
        SchoolResponse response = schoolService.changeSchoolStatus(id, status);
        return ResponseEntity.ok(ApiResponse.success(
                ApiResponseCode.SCHOOL_ACTIVATED,
                "School status updated successfully",
                response
        ));
    }

    @PostMapping("/{id}/branches")
    public ResponseEntity<ApiResponse<SchoolBranchResponse>> addBranch(
            @PathVariable UUID id,
            @Valid @RequestBody CreateBranchRequest request) {
        SchoolBranchResponse response = schoolService.addBranch(id, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        ApiResponseCode.SUCCESS,
                        "School branch created successfully",
                        response
                ));
    }

    @GetMapping("/{id}/branches")
    public ResponseEntity<PagedApiResponse<SchoolBranchResponse>> getSchoolBranches(
            @PathVariable UUID id,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<SchoolBranchResponse> page = schoolService.getSchoolBranches(id, pageable);
        return ResponseEntity.ok(PagedApiResponse.of(
                ApiResponseCode.SUCCESS,
                "School branches retrieved successfully",
                page.getContent(),
                page
        ));
    }

    @GetMapping("/{id}/branches/{branchId}")
    public ResponseEntity<ApiResponse<SchoolBranchResponse>> getBranchById(
            @PathVariable UUID id,
            @PathVariable UUID branchId) {
        SchoolBranchResponse response = schoolService.getBranchById(id, branchId);
        return ResponseEntity.ok(ApiResponse.success(
                ApiResponseCode.SUCCESS,
                "School branch retrieved successfully",
                response
        ));
    }

    @PutMapping("/{id}/branches/{branchId}")
    public ResponseEntity<ApiResponse<SchoolBranchResponse>> updateBranch(
            @PathVariable UUID id,
            @PathVariable UUID branchId,
            @Valid @RequestBody UpdateBranchRequest request) {
        SchoolBranchResponse response = schoolService.updateBranch(id, branchId, request);
        return ResponseEntity.ok(ApiResponse.success(
                ApiResponseCode.SUCCESS,
                "School branch updated successfully",
                response
        ));
    }
}
