package com.vidya.tenantdata.user.controller;

import com.vidya.common.api.ApiResponse;
import com.vidya.common.api.PagedApiResponse;
import com.vidya.tenantdata.user.dto.CreateTenantUserRequest;
import com.vidya.tenantdata.user.dto.TenantUserDetailResponse;
import com.vidya.tenantdata.user.dto.TenantUserResponse;
import com.vidya.tenantdata.user.dto.UpdateTenantUserRequest;
import com.vidya.tenantdata.user.dto.UpdateTenantUserStatusRequest;
import com.vidya.tenantdata.user.entity.TenantUserStatus;
import com.vidya.tenantdata.user.entity.TenantUserType;
import com.vidya.tenantdata.user.service.TenantUserService;
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
@RequestMapping("/api/v1/tenant/users")
@RequiredArgsConstructor
@Tag(name = "Tenant Users", description = "Operational user management for the active school tenant")
public class TenantUserController {

    private final TenantUserService tenantUserService;

    @PostMapping
    @PreAuthorize("hasAuthority('USER_CREATE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Create tenant operational user", description = "Creates a new staff, teacher, student, or admin user within the current tenant database")
    public ResponseEntity<ApiResponse<TenantUserDetailResponse>> createTenantUser(
            @Valid @RequestBody CreateTenantUserRequest request) {
        log.info("REST: Create tenant user for email: {}", request.getEmail());
        TenantUserDetailResponse response = tenantUserService.createTenantUser(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("TENANT_USER_CREATED", "Tenant user successfully created", response));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Get tenant user by ID", description = "Retrieves tenant operational user and profile by unique UUID")
    public ResponseEntity<ApiResponse<TenantUserDetailResponse>> getTenantUserById(
            @PathVariable UUID id) {
        log.info("REST: Get tenant user by ID: {}", id);
        TenantUserDetailResponse response = tenantUserService.getTenantUserById(id);
        return ResponseEntity.ok(ApiResponse.success("TENANT_USER_FETCHED", "Tenant user retrieved successfully", response));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('USER_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "List tenant users", description = "Lists active operational users in the current tenant database with pagination")
    public ResponseEntity<PagedApiResponse<TenantUserResponse>> listTenantUsers(
            @RequestParam(required = false) TenantUserStatus status,
            @RequestParam(required = false) TenantUserType userType,
            @Parameter(hidden = true)
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        log.info("REST: List tenant users with status: {}, userType: {}", status, userType);
        Page<TenantUserResponse> page = tenantUserService.listTenantUsers(status, userType, pageable);
        return ResponseEntity.ok(PagedApiResponse.of("TENANT_USERS_FETCHED", "Tenant users retrieved successfully", page.getContent(), page));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_UPDATE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Update tenant user", description = "Updates profile and contact details of a tenant user")
    public ResponseEntity<ApiResponse<TenantUserDetailResponse>> updateTenantUser(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTenantUserRequest request) {
        log.info("REST: Update tenant user ID: {}", id);
        TenantUserDetailResponse response = tenantUserService.updateTenantUser(id, request);
        return ResponseEntity.ok(ApiResponse.success("TENANT_USER_UPDATED", "Tenant user successfully updated", response));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('USER_UPDATE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Update tenant user status", description = "Changes lifecycle status of a tenant user")
    public ResponseEntity<ApiResponse<TenantUserDetailResponse>> updateTenantUserStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTenantUserStatusRequest request) {
        log.info("REST: Update tenant user ID: {} status to: {}", id, request.getStatus());
        TenantUserDetailResponse response = tenantUserService.updateTenantUserStatus(id, request);
        return ResponseEntity.ok(ApiResponse.success("TENANT_USER_STATUS_UPDATED", "Tenant user status successfully updated", response));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_DELETE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Soft delete tenant user", description = "Soft deletes a tenant operational user")
    public ResponseEntity<ApiResponse<Void>> deleteTenantUser(@PathVariable UUID id) {
        log.info("REST: Delete tenant user ID: {}", id);
        tenantUserService.deleteTenantUser(id);
        return ResponseEntity.ok(ApiResponse.success("TENANT_USER_DELETED", "Tenant user successfully deleted", null));
    }
}
