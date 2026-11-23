package com.vidya.security.access.controller;

import com.vidya.common.api.ApiResponse;
import com.vidya.common.api.ApiResponseCode;
import com.vidya.security.access.dto.AssignRoleRequest;
import com.vidya.security.access.dto.EffectiveAccessResponse;
import com.vidya.security.access.dto.PermissionCodesRequest;
import com.vidya.security.access.dto.PermissionOverrideRequest;
import com.vidya.security.access.dto.PermissionOverrideResponse;
import com.vidya.security.access.dto.PermissionRequest;
import com.vidya.security.access.dto.PermissionResponse;
import com.vidya.security.access.dto.RoleRequest;
import com.vidya.security.access.dto.RoleResponse;
import com.vidya.security.access.dto.UserRoleResponse;
import com.vidya.security.access.service.AccessControlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

import java.util.List;
import java.util.UUID;

/**
 * Platform-level access control: the permission catalog, global (and any school's) roles,
 * role assignments for any user, per-user overrides and effective-access inspection.
 */
@RestController
@RequestMapping("/api/v1/admin/access")
@RequiredArgsConstructor
@Tag(name = "Access Control (Platform)", description = "DB-driven roles, permissions and user role assignments")
@SecurityRequirement(name = "bearerAuth")
public class AccessControlAdminController {

    private final AccessControlService service;

    // ---------------- permissions ----------------

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('ACCESS_CONTROL_READ')")
    @Operation(summary = "List the permission catalog")
    public ResponseEntity<ApiResponse<List<PermissionResponse>>> listPermissions() {
        return ok("Permissions retrieved", service.listPermissions());
    }

    @PostMapping("/permissions")
    @PreAuthorize("hasAuthority('ACCESS_CONTROL_MANAGE')")
    @Operation(summary = "Create a permission (then reference it in @PreAuthorize or grant it to roles)")
    public ResponseEntity<ApiResponse<PermissionResponse>> createPermission(@Valid @RequestBody PermissionRequest request) {
        return created("Permission created", service.createPermission(request));
    }

    @PutMapping("/permissions/{permissionId}")
    @PreAuthorize("hasAuthority('ACCESS_CONTROL_MANAGE')")
    @Operation(summary = "Update a permission (name, module, scope, feature gate, active)")
    public ResponseEntity<ApiResponse<PermissionResponse>> updatePermission(@PathVariable UUID permissionId,
                                                                            @Valid @RequestBody PermissionRequest request) {
        return ok("Permission updated", service.updatePermission(permissionId, request));
    }

    // ---------------- roles ----------------

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('ACCESS_CONTROL_READ')")
    @Operation(summary = "List all roles (global and school custom)")
    public ResponseEntity<ApiResponse<List<RoleResponse>>> listRoles(
            @RequestParam(defaultValue = "false") boolean includePermissions) {
        return ok("Roles retrieved", service.listRoles(null, includePermissions));
    }

    @GetMapping("/roles/{roleId}")
    @PreAuthorize("hasAuthority('ACCESS_CONTROL_READ')")
    public ResponseEntity<ApiResponse<RoleResponse>> getRole(@PathVariable UUID roleId) {
        return ok("Role retrieved", service.getRole(roleId, null));
    }

    @PostMapping("/roles")
    @PreAuthorize("hasAuthority('ACCESS_CONTROL_MANAGE')")
    @Operation(summary = "Create a global role (scope PLATFORM, SCHOOL, SCHOOL_PORTAL or COMMUNITY)")
    public ResponseEntity<ApiResponse<RoleResponse>> createRole(@Valid @RequestBody RoleRequest request) {
        return created("Role created", service.createRole(request, null));
    }

    @PutMapping("/roles/{roleId}")
    @PreAuthorize("hasAuthority('ACCESS_CONTROL_MANAGE')")
    public ResponseEntity<ApiResponse<RoleResponse>> updateRole(@PathVariable UUID roleId,
                                                                @Valid @RequestBody RoleRequest request) {
        return ok("Role updated", service.updateRole(roleId, request, null));
    }

    @PutMapping("/roles/{roleId}/permissions")
    @PreAuthorize("hasAuthority('ACCESS_CONTROL_MANAGE')")
    @Operation(summary = "Replace a role's permissions")
    public ResponseEntity<ApiResponse<RoleResponse>> replaceRolePermissions(@PathVariable UUID roleId,
                                                                            @Valid @RequestBody PermissionCodesRequest request) {
        return ok("Role permissions replaced", service.replaceRolePermissions(roleId, request.permissionCodes(), null));
    }

    @PostMapping("/roles/{roleId}/permissions")
    @PreAuthorize("hasAuthority('ACCESS_CONTROL_MANAGE')")
    @Operation(summary = "Add permissions to a role")
    public ResponseEntity<ApiResponse<RoleResponse>> addRolePermissions(@PathVariable UUID roleId,
                                                                        @Valid @RequestBody PermissionCodesRequest request) {
        return ok("Role permissions added", service.addRolePermissions(roleId, request.permissionCodes(), null));
    }

    @DeleteMapping("/roles/{roleId}/permissions/{permissionCode}")
    @PreAuthorize("hasAuthority('ACCESS_CONTROL_MANAGE')")
    public ResponseEntity<ApiResponse<RoleResponse>> removeRolePermission(@PathVariable UUID roleId,
                                                                          @PathVariable String permissionCode) {
        return ok("Role permission removed", service.removeRolePermission(roleId, permissionCode, null));
    }

    @DeleteMapping("/roles/{roleId}")
    @PreAuthorize("hasAuthority('ACCESS_CONTROL_MANAGE')")
    @Operation(summary = "Delete a non-system role (removes its assignments)")
    public ResponseEntity<ApiResponse<Void>> deleteRole(@PathVariable UUID roleId) {
        service.deleteRole(roleId, null);
        return ResponseEntity.ok(ApiResponse.success(ApiResponseCode.SUCCESS, "Role deleted"));
    }

    // ---------------- users ----------------

    @GetMapping("/users/{userId}/roles")
    @PreAuthorize("hasAuthority('ACCESS_CONTROL_READ')")
    public ResponseEntity<ApiResponse<List<UserRoleResponse>>> listUserRoles(@PathVariable UUID userId) {
        return ok("User roles retrieved", service.listUserRoles(userId, null));
    }

    @PostMapping("/users/{userId}/roles")
    @PreAuthorize("hasAuthority('ACCESS_CONTROL_MANAGE')")
    @Operation(summary = "Assign a role to a user (optionally time-boxed)")
    public ResponseEntity<ApiResponse<UserRoleResponse>> assignRole(@PathVariable UUID userId,
                                                                    @Valid @RequestBody AssignRoleRequest request) {
        return created("Role assigned", service.assignRole(userId, request, null));
    }

    @DeleteMapping("/users/{userId}/roles/{assignmentId}")
    @PreAuthorize("hasAuthority('ACCESS_CONTROL_MANAGE')")
    public ResponseEntity<ApiResponse<Void>> removeAssignment(@PathVariable UUID userId, @PathVariable UUID assignmentId) {
        service.removeAssignment(userId, assignmentId, null);
        return ResponseEntity.ok(ApiResponse.success(ApiResponseCode.SUCCESS, "Role removed from user"));
    }

    @GetMapping("/users/{userId}/permission-overrides")
    @PreAuthorize("hasAuthority('ACCESS_CONTROL_READ')")
    public ResponseEntity<ApiResponse<List<PermissionOverrideResponse>>> listOverrides(@PathVariable UUID userId) {
        return ok("Overrides retrieved", service.listOverrides(userId));
    }

    @PutMapping("/users/{userId}/permission-overrides")
    @PreAuthorize("hasAuthority('ACCESS_CONTROL_MANAGE')")
    @Operation(summary = "Grant or deny one permission for one user, regardless of roles")
    public ResponseEntity<ApiResponse<PermissionOverrideResponse>> upsertOverride(@PathVariable UUID userId,
                                                                                  @Valid @RequestBody PermissionOverrideRequest request) {
        return ok("Override saved", service.upsertOverride(userId, request));
    }

    @DeleteMapping("/users/{userId}/permission-overrides/{overrideId}")
    @PreAuthorize("hasAuthority('ACCESS_CONTROL_MANAGE')")
    public ResponseEntity<ApiResponse<Void>> deleteOverride(@PathVariable UUID userId, @PathVariable UUID overrideId) {
        service.deleteOverride(userId, overrideId);
        return ResponseEntity.ok(ApiResponse.success(ApiResponseCode.SUCCESS, "Override removed"));
    }

    @GetMapping("/users/{userId}/effective-access")
    @PreAuthorize("hasAuthority('ACCESS_CONTROL_READ')")
    @Operation(summary = "What the user can do right now (roles, permissions after overrides and feature gating)")
    public ResponseEntity<ApiResponse<EffectiveAccessResponse>> effectiveAccess(@PathVariable UUID userId) {
        return ok("Effective access resolved", service.effectiveAccess(userId, null));
    }

    @PostMapping("/cache/evict")
    @PreAuthorize("hasAuthority('ACCESS_CONTROL_MANAGE')")
    @Operation(summary = "Apply direct DB edits immediately on this instance (otherwise they apply within the cache TTL)")
    public ResponseEntity<ApiResponse<Void>> evictCache() {
        service.evictCache();
        return ResponseEntity.ok(ApiResponse.success(ApiResponseCode.SUCCESS, "Access cache cleared"));
    }

    private static <T> ResponseEntity<ApiResponse<T>> ok(String message, T data) {
        return ResponseEntity.ok(ApiResponse.success(ApiResponseCode.SUCCESS, message, data));
    }

    private static <T> ResponseEntity<ApiResponse<T>> created(String message, T data) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(ApiResponseCode.SUCCESS, message, data));
    }
}
