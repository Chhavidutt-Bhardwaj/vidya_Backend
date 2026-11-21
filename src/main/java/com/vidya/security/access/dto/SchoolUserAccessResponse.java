package com.vidya.security.access.dto;

import com.vidya.security.entity.PlatformRole;
import com.vidya.security.entity.UserStatus;

import java.util.List;
import java.util.UUID;

/** A school's user with the roles they hold, for the school "Staff & Roles" screen. */
public record SchoolUserAccessResponse(
        UUID userId,
        String userCode,
        String fullName,
        String email,
        UserStatus status,
        PlatformRole accountType,
        List<UserRoleResponse> roles
) {}
