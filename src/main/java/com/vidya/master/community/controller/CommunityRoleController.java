package com.vidya.master.community.controller;

import com.vidya.common.api.ApiResponse;
import com.vidya.master.community.dto.AssignCommunityRoleRequest;
import com.vidya.master.community.dto.CommunityMemberResponse;
import com.vidya.master.community.dto.CommunityRoleAuditResponse;
import com.vidya.master.community.dto.CommunityRoleResponse;
import com.vidya.master.community.dto.CreateCommunityRoleRequest;
import com.vidya.master.community.dto.MyPermissionsResponse;
import com.vidya.master.community.dto.RolePermissionEntry;
import com.vidya.master.community.entity.CommunityType;
import com.vidya.master.community.service.CommunityRoleService;
import com.vidya.security.user.PlatformUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST Controller for managing Community Custom Roles and Universal Integer Permission Matrices.
 */
@RestController
@RequestMapping("/api/v1/communities/roles")
@RequiredArgsConstructor
@Tag(name = "Community Role Management", description = "APIs for managing custom community roles and granular permissions")
public class CommunityRoleController {

    private final CommunityRoleService communityRoleService;

    @PostMapping
    @Operation(summary = "Create custom role", description = "Create a custom role definition for a specific community type")
    public ResponseEntity<ApiResponse<CommunityRoleResponse>> createRole(
            @Valid @RequestBody CreateCommunityRoleRequest request) {
        CommunityRoleResponse response = communityRoleService.createRole(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("COMMUNITY_ROLE_CREATED", "Role created successfully", response));
    }

    @GetMapping("/type/{communityType}")
    @Operation(summary = "Get roles by community type", description = "Retrieve all active role definitions for a specific community type")
    public ResponseEntity<ApiResponse<List<CommunityRoleResponse>>> getRolesByCommunityType(
            @PathVariable CommunityType communityType) {
        List<CommunityRoleResponse> response = communityRoleService.getRolesByCommunityType(communityType);
        return ResponseEntity.ok(ApiResponse.success("RESOURCE_FOUND", "Roles retrieved successfully", response));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get role by ID")
    public ResponseEntity<ApiResponse<CommunityRoleResponse>> getRoleById(@PathVariable UUID id) {
        CommunityRoleResponse response = communityRoleService.getRoleById(id);
        return ResponseEntity.ok(ApiResponse.success("RESOURCE_FOUND", "Role retrieved successfully", response));
    }

    @GetMapping("/code/{roleCode}")
    @Operation(summary = "Get role by role code")
    public ResponseEntity<ApiResponse<CommunityRoleResponse>> getRoleByCode(@PathVariable String roleCode) {
        CommunityRoleResponse response = communityRoleService.getRoleByCode(roleCode);
        return ResponseEntity.ok(ApiResponse.success("RESOURCE_FOUND", "Role retrieved successfully", response));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update custom role")
    public ResponseEntity<ApiResponse<CommunityRoleResponse>> updateRole(
            @PathVariable UUID id,
            @Valid @RequestBody CreateCommunityRoleRequest request) {
        CommunityRoleResponse response = communityRoleService.updateRole(id, request);
        return ResponseEntity.ok(ApiResponse.success("COMMUNITY_ROLE_UPDATED", "Role updated successfully", response));
    }

    @PutMapping("/{id}/permissions")
    @Operation(summary = "Update role permissions matrix")
    public ResponseEntity<ApiResponse<CommunityRoleResponse>> updateRolePermissions(
            @PathVariable UUID id,
            @Valid @RequestBody List<RolePermissionEntry> permissions) {
        CommunityRoleResponse response = communityRoleService.updateRolePermissions(id, permissions);
        return ResponseEntity.ok(ApiResponse.success("ROLE_PERMISSIONS_UPDATED", "Role permissions updated successfully", response));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete custom role")
    public ResponseEntity<ApiResponse<Void>> deleteRole(@PathVariable UUID id) {
        communityRoleService.deleteRole(id);
        return ResponseEntity.ok(ApiResponse.success("COMMUNITY_ROLE_DELETED", "Role deleted successfully", null));
    }

    @PostMapping("/members/assign")
    @Operation(summary = "Assign custom role to community member")
    public ResponseEntity<ApiResponse<CommunityMemberResponse>> assignRoleToMember(
            @Valid @RequestBody AssignCommunityRoleRequest request) {
        CommunityMemberResponse response = communityRoleService.assignRoleToMember(request);
        return ResponseEntity.ok(ApiResponse.success("ROLE_ASSIGNED", "Role assigned successfully", response));
    }

    @GetMapping("/my-permissions")
    @Operation(summary = "Get currently authenticated user's permissions matrix")
    public ResponseEntity<ApiResponse<MyPermissionsResponse>> getMyPermissions(
            @AuthenticationPrincipal PlatformUserDetails userDetails) {
        UUID userId = userDetails != null ? userDetails.getUserId() : null;
        MyPermissionsResponse response = communityRoleService.getMyPermissions(userId);
        return ResponseEntity.ok(ApiResponse.success("PERMISSIONS_RESOLVED", "Permissions resolved successfully", response));
    }

    @GetMapping("/{communityId}/audit-logs")
    @Operation(summary = "Get role audit logs for community")
    public ResponseEntity<ApiResponse<Page<CommunityRoleAuditResponse>>> getAuditLogs(
            @PathVariable UUID communityId,
            Pageable pageable) {
        Page<CommunityRoleAuditResponse> response = communityRoleService.getAuditLogs(communityId, pageable);
        return ResponseEntity.ok(ApiResponse.success("AUDIT_LOGS_RETRIEVED", "Audit logs retrieved successfully", response));
    }
}
