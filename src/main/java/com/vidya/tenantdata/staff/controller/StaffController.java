package com.vidya.tenantdata.staff.controller;

import com.vidya.common.api.ApiResponse;
import com.vidya.common.api.PagedApiResponse;
import com.vidya.tenantdata.staff.dto.CreateStaffRequest;
import com.vidya.tenantdata.staff.dto.StaffDetailResponse;
import com.vidya.tenantdata.staff.dto.StaffResponse;
import com.vidya.tenantdata.staff.dto.UpdateStaffRequest;
import com.vidya.tenantdata.staff.dto.UpdateStaffStatusRequest;
import com.vidya.tenantdata.staff.enums.EmploymentStatus;
import com.vidya.tenantdata.staff.enums.StaffType;
import com.vidya.tenantdata.staff.service.StaffService;
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
@RequestMapping("/api/v1/staff")
@RequiredArgsConstructor
@Tag(name = "Staff", description = "Staff & Teacher management operations for the active school tenant")
public class StaffController {

    private final StaffService staffService;

    @PostMapping
    @PreAuthorize("hasAuthority('STAFF_CREATE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Create staff member", description = "Creates a new staff record linked to an existing tenant operational user")
    public ResponseEntity<ApiResponse<StaffDetailResponse>> createStaff(
            @Valid @RequestBody CreateStaffRequest request) {
        log.info("REST: Create staff for tenantUserId: [{}]", request.getTenantUserId());
        StaffDetailResponse response = staffService.createStaff(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("STAFF_CREATED", "Staff member successfully created", response));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('STAFF_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Search and list staff", description = "Lists staff in the current tenant database with search and pagination")
    public ResponseEntity<PagedApiResponse<StaffResponse>> listStaff(
            @RequestParam(required = false) EmploymentStatus status,
            @RequestParam(required = false) StaffType staffType,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String designation,
            @RequestParam(required = false) String q,
            @Parameter(hidden = true)
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        log.info("REST: List staff - status: {}, staffType: {}, department: {}, designation: {}, query: {}",
                status, staffType, department, designation, q);
        Page<StaffResponse> page = staffService.searchStaff(status, staffType, department, designation, q, pageable);
        return ResponseEntity.ok(PagedApiResponse.of("STAFF_FETCHED", "Staff retrieved successfully", page.getContent(), page));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('STAFF_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Get staff by UUID", description = "Retrieves staff record including operational profile")
    public ResponseEntity<ApiResponse<StaffDetailResponse>> getStaffById(
            @PathVariable UUID id) {
        log.info("REST: Get staff by ID: [{}]", id);
        StaffDetailResponse response = staffService.getStaffById(id);
        return ResponseEntity.ok(ApiResponse.success("STAFF_FETCHED", "Staff retrieved successfully", response));
    }

    @GetMapping("/code/{staffCode}")
    @PreAuthorize("hasAuthority('STAFF_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Get staff by code", description = "Retrieves staff record by stable staff code (e.g. STF-8A92K3LM)")
    public ResponseEntity<ApiResponse<StaffDetailResponse>> getStaffByCode(
            @PathVariable String staffCode) {
        log.info("REST: Get staff by code: [{}]", staffCode);
        StaffDetailResponse response = staffService.getStaffByStaffCode(staffCode);
        return ResponseEntity.ok(ApiResponse.success("STAFF_FETCHED", "Staff retrieved successfully", response));
    }

    @GetMapping("/tenant-user/{tenantUserId}")
    @PreAuthorize("hasAuthority('STAFF_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Get staff by TenantUser ID", description = "Retrieves staff record linked to a specific tenant user")
    public ResponseEntity<ApiResponse<StaffDetailResponse>> getStaffByTenantUserId(
            @PathVariable UUID tenantUserId) {
        log.info("REST: Get staff by tenantUserId: [{}]", tenantUserId);
        StaffDetailResponse response = staffService.getStaffByTenantUserId(tenantUserId);
        return ResponseEntity.ok(ApiResponse.success("STAFF_FETCHED", "Staff retrieved successfully", response));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('STAFF_UPDATE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Update staff", description = "Updates mutable staff operational fields")
    public ResponseEntity<ApiResponse<StaffDetailResponse>> updateStaff(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateStaffRequest request) {
        log.info("REST: Update staff ID: [{}]", id);
        StaffDetailResponse response = staffService.updateStaff(id, request);
        return ResponseEntity.ok(ApiResponse.success("STAFF_UPDATED", "Staff successfully updated", response));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('STAFF_STATUS_UPDATE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Update staff status", description = "Transitions staff employment lifecycle status")
    public ResponseEntity<ApiResponse<StaffDetailResponse>> updateStaffStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateStaffStatusRequest request) {
        log.info("REST: Update staff ID: [{}] status to: [{}]", id, request.getStatus());
        StaffDetailResponse response = staffService.updateStaffStatus(id, request);
        return ResponseEntity.ok(ApiResponse.success("STAFF_STATUS_UPDATED", "Staff status successfully updated", response));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('STAFF_DELETE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Soft delete staff", description = "Soft deletes a staff operational record")
    public ResponseEntity<ApiResponse<Void>> deleteStaff(@PathVariable UUID id) {
        log.info("REST: Delete staff ID: [{}]", id);
        staffService.deleteStaff(id);
        return ResponseEntity.ok(ApiResponse.success("STAFF_DELETED", "Staff successfully deleted", null));
    }
}
