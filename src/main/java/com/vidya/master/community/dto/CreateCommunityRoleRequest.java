package com.vidya.master.community.dto;

import com.vidya.master.community.entity.CommunityType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request payload for creating or updating a custom community role definition.
 */
public record CreateCommunityRoleRequest(
        String roleCode,

        @NotBlank(message = "Role name is required")
        @Size(max = 100, message = "Role name must not exceed 100 characters")
        String roleName,

        @Size(max = 2000, message = "Description must not exceed 2000 characters")
        String description,

        CommunityType communityType,

        Integer level,

        @Valid
        List<RolePermissionEntry> permissions
) {}
