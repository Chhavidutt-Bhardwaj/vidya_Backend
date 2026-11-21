package com.vidya.security.service;

import org.springframework.security.core.Authentication;

import java.util.UUID;

public interface TenantAuthorizationService {

    boolean canAccessTenant(UUID userId, String tenantCode);

    boolean canAccessSchool(UUID userId, UUID schoolId);

    boolean canAccessOnboarding(UUID userId, UUID onboardingId);

    /** DB-resolved check (roles + overrides + feature gating) by permission code. */
    boolean hasPermission(UUID userId, String permissionCode);

    // SpEL-friendly overloads
    boolean canAccessTenant(Authentication authentication, String tenantCode);

    boolean canAccessSchool(Authentication authentication, UUID schoolId);

    boolean canAccessOnboarding(Authentication authentication, UUID onboardingId);

    boolean hasPermission(Authentication authentication, String permissionName);

    /**
     * True for platform administrators, or when the authenticated user is an active member of
     * {@code communityId}. Used to scope community users' onboarding listings.
     */
    boolean isCommunityMember(Authentication authentication, UUID communityId);

    /**
     * Validates the community attribution of a new onboarding. Platform administrators may
     * attribute any community; community users may only attribute their own membership
     * (and must attribute one); everyone else is denied.
     */
    boolean canInitiateOnboarding(Authentication authentication, UUID communityId, UUID communityMemberId);
}
