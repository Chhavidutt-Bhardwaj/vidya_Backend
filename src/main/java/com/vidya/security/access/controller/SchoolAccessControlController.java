package com.vidya.security.access.controller;

import com.vidya.common.api.ApiResponse;
import com.vidya.common.api.ApiResponseCode;
import com.vidya.security.access.dto.AssignRoleRequest;
import com.vidya.security.access.dto.EffectiveAccessResponse;
import com.vidya.security.access.dto.PermissionCodesRequest;
import com.vidya.security.access.dto.PermissionResponse;
import com.vidya.security.access.dto.RoleRequest;
import com.vidya.security.access.dto.RoleResponse;
import com.vidya.security.access.dto.SchoolUserAccessResponse;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * School self-service access control: a school admin creates the school's own roles
 * (e.g. HOSTEL_WARDEN) from SCHOOL-scope permissions and assigns roles to the school's users.
 * Global platform roles are visible and assignable but read-only.
 */
@RestController
@RequestMapping("/api/v1/schools/{schoolId}/access")
@RequiredArgsConstructor
@Tag(name = "Access Control (School)", description = "School custom roles and staff role assignments")
@SecurityRequirement(name = "bearerAuth")
public class SchoolAccessControlController {

    private static final String READ =
            "hasAuthority('SCHOOL_ROLE_READ') and @tenantAuthorizationService.canAccessSchool(authentication, #schoolId)";
    private static final String MANAGE =
            "hasAuthority('SCHOOL_ROLE_MANAGE') and @tenantAuthorizationService.canAccessSchool(authentication, #schoolId)";

    private final AccessControlService service;

    @GetMapping("/permissions")
    @PreAuthorize(READ)
    @Operation(summary = "Permissions the school can use in its custom roles")
    public ResponseEntity<ApiResponse<List<PermissionResponse>>> assignablePermissions(@PathVariable UUID schoolId) {
        return ok("Assignable permissions retrieved", service.listSchoolAssignablePermissions());
    }

    @GetMapping("/roles")
    @PreAuthorize(READ)
    @Operation(summary = "Roles available to the school: global school roles + the school's custom roles")
    public ResponseEntity<ApiResponse<List<RoleResponse>>> listRoles(@PathVariable UUID schoolId) {
        return ok("Roles retrieved", service.listRoles(schoolId, true));
    }

    @GetMapping("/roles/{roleId}")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<RoleResponse>> getRole(@PathVariable UUID schoolId, @PathVariable UUID roleId) {
        return ok("Role retrieved", service.getRole(roleId, schoolId));
    }

    @PostMapping("/roles")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Create a custom role for this school")
    public ResponseEntity<ApiResponse<RoleResponse>> createRole(@PathVariable UUID schoolId,
                                                                @Valid @RequestBody RoleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(ApiResponseCode.SUCCESS, "Role created", service.createRole(request, schoolId)));
    }

    @PutMapping("/roles/{roleId}")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<RoleResponse>> updateRole(@PathVariable UUID schoolId, @PathVariable UUID roleId,
                                                                @Valid @RequestBody RoleRequest request) {
        return ok("Role updated", service.updateRole(roleId, request, schoolId));
    }

    @PutMapping("/roles/{roleId}/permissions")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<RoleResponse>> replaceRolePermissions(@PathVariable UUID schoolId, @PathVariable UUID roleId,
                                                                            @Valid @RequestBody PermissionCodesRequest request) {
        return ok("Role permissions replaced", service.replaceRolePermissions(roleId, request.permissionCodes(), schoolId));
    }

    @DeleteMapping("/roles/{roleId}")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<Void>> deleteRole(@PathVariable UUID schoolId, @PathVariable UUID roleId) {
        service.deleteRole(roleId, schoolId);
        return ResponseEntity.ok(ApiResponse.success(ApiResponseCode.SUCCESS, "Role deleted"));
    }

    @GetMapping("/users")
    @PreAuthorize(READ)
    @Operation(summary = "The school's users with the roles each one holds")
    public ResponseEntity<ApiResponse<List<SchoolUserAccessResponse>>> listUsers(@PathVariable UUID schoolId) {
        return ok("School users retrieved", service.listSchoolUsers(schoolId));
    }

    @GetMapping("/users/{userId}/roles")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<UserRoleResponse>>> listUserRoles(@PathVariable UUID schoolId, @PathVariable UUID userId) {
        return ok("User roles retrieved", service.listUserRoles(userId, schoolId));
    }

    @PostMapping("/users/{userId}/roles")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Assign a role to a user of this school")
    public ResponseEntity<ApiResponse<UserRoleResponse>> assignRole(@PathVariable UUID schoolId, @PathVariable UUID userId,
                                                                    @Valid @RequestBody AssignRoleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(ApiResponseCode.SUCCESS, "Role assigned", service.assignRole(userId, request, schoolId)));
    }

    @DeleteMapping("/users/{userId}/roles/{assignmentId}")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<Void>> removeAssignment(@PathVariable UUID schoolId, @PathVariable UUID userId,
                                                              @PathVariable UUID assignmentId) {
        service.removeAssignment(userId, assignmentId, schoolId);
        return ResponseEntity.ok(ApiResponse.success(ApiResponseCode.SUCCESS, "Role removed from user"));
    }

    @GetMapping("/users/{userId}/effective-access")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<EffectiveAccessResponse>> effectiveAccess(@PathVariable UUID schoolId, @PathVariable UUID userId) {
        return ok("Effective access resolved", service.effectiveAccess(userId, schoolId));
    }

    private static <T> ResponseEntity<ApiResponse<T>> ok(String message, T data) {
        return ResponseEntity.ok(ApiResponse.success(ApiResponseCode.SUCCESS, message, data));
    }
}
