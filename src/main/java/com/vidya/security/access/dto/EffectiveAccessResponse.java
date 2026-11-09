package com.vidya.security.access.dto;

import com.vidya.security.access.AccessScope;
import com.vidya.security.access.service.EffectiveAccess;
import com.vidya.security.entity.PlatformRole;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record EffectiveAccessResponse(
        UUID userId,
        PlatformRole accountType,
        AccessScope scope,
        UUID schoolId,
        boolean superUser,
        Set<String> roles,
        Set<String> permissions,
        Set<String> enabledFeatures,
        List<UserRoleResponse> roleAssignments,
        Instant resolvedAt
) {
    public static EffectiveAccessResponse from(EffectiveAccess a, List<UserRoleResponse> assignments) {
        return new EffectiveAccessResponse(a.userId(), a.accountType(), a.scope(), a.schoolId(), a.superUser(),
                a.roles(), a.permissions(), a.enabledFeatures(), assignments, a.resolvedAt());
    }
}
