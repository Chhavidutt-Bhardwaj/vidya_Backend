package com.vidya.security.service;

import com.vidya.security.entity.PlatformPermission;
import org.springframework.security.core.Authentication;

import java.util.UUID;

public interface TenantAuthorizationService {

    boolean canAccessTenant(UUID userId, String tenantCode);

    boolean canAccessSchool(UUID userId, UUID schoolId);

    boolean canAccessOnboarding(UUID userId, UUID onboardingId);

    boolean hasPermission(UUID userId, PlatformPermission permission);

    // SpEL-friendly overloads
    boolean canAccessTenant(Authentication authentication, String tenantCode);

    boolean canAccessSchool(Authentication authentication, UUID schoolId);

    boolean canAccessOnboarding(Authentication authentication, UUID onboardingId);

    boolean hasPermission(Authentication authentication, String permissionName);
}
