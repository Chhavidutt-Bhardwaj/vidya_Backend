package com.vidya.security.entity;

import com.vidya.security.access.AccessScope;

/**
 * Account type of a platform user ({@code platform_user.platform_role}).
 *
 * <p>This enum no longer carries permissions. Each value is backed by a global system role row in
 * {@code access_role} (same code), and that row's grants in {@code access_role_permission} are the
 * source of truth. Extra roles, including school custom roles, are attached through
 * {@code user_role_assignment}. See {@code docs/security/DYNAMIC_ACCESS_CONTROL.md}.
 *
 * <p>The {@link AccessScope} decides data scoping: which schools/tenants the account may reach.
 * To create a user whose permissions come only from custom roles, use {@link #SCHOOL_STAFF} or
 * {@link #PLATFORM_STAFF}.
 */
public enum PlatformRole {

    PLATFORM_SUPER_ADMIN(AccessScope.PLATFORM),
    PLATFORM_ADMIN(AccessScope.PLATFORM),
    /** Generic platform account; permissions come only from assigned roles. */
    PLATFORM_STAFF(AccessScope.PLATFORM),

    COMMUNITY_ADMIN(AccessScope.COMMUNITY),
    COMMUNITY_ROLE_MANAGER(AccessScope.COMMUNITY),
    COMMUNITY_MANAGER(AccessScope.COMMUNITY),
    FIELD_AGENT(AccessScope.COMMUNITY),

    SCHOOL_OWNER(AccessScope.SCHOOL),
    SCHOOL_ADMIN(AccessScope.SCHOOL),
    /** Generic school account; permissions come only from assigned (custom) roles. */
    SCHOOL_STAFF(AccessScope.SCHOOL),
    TEACHER(AccessScope.SCHOOL),
    PRINCIPAL(AccessScope.SCHOOL),
    COORDINATOR(AccessScope.SCHOOL),
    SUBJECT_TEACHER(AccessScope.SCHOOL),
    ACCOUNTS(AccessScope.SCHOOL),

    PARENT(AccessScope.SCHOOL_PORTAL),
    STUDENT(AccessScope.SCHOOL_PORTAL);

    private final AccessScope scope;

    PlatformRole(AccessScope scope) {
        this.scope = scope;
    }

    public AccessScope getScope() {
        return scope;
    }

    public boolean isPlatformAdmin() {
        return this == PLATFORM_SUPER_ADMIN || this == PLATFORM_ADMIN;
    }
}
