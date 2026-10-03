package com.vidya.tenant.registry;

import java.util.Optional;
import java.util.UUID;

/**
 * Contract for resolving tenant connection information.
 *
 * <p>The registry is the authoritative source of truth for which tenants
 * exist, their connection details, and their current status.
 *
 * <p>Implementations may use a Redis-backed cache on top of the master DB
 * to avoid a database round-trip on every request.
 */
public interface TenantRegistryService {

    /**
     * Finds a tenant by its code.
     *
     * @param tenantCode the unique tenant code (e.g. {@code abc12345})
     * @return the registration, or empty if not found
     */
    Optional<TenantRegistration> findByTenantCode(String tenantCode);

    /**
     * Finds a tenant by school ID.
     *
     * @param schoolId the school's UUID
     * @return the registration, or empty if no tenant provisioned for this school
     */
    Optional<TenantRegistration> findBySchoolId(UUID schoolId);

    /**
     * Evicts the cached registration for the given tenant code.
     * Must be called when a tenant's status or connection details change.
     *
     * @param tenantCode the tenant code to evict
     */
    void evictCache(String tenantCode);

    /**
     * Returns whether the given user has access to the given tenant.
     *
     * <p>In Phase 01:
     * <ul>
     *   <li>PLATFORM_SUPER_ADMIN and PLATFORM_ADMIN → access any tenant</li>
     *   <li>School-level users → only the tenant matching their schoolId</li>
     * </ul>
     *
     * @param userSchoolId the school ID from the authenticated user's JWT (may be null for platform users)
     * @param userPlatformRole the user's platform role string
     * @param tenantCode the requested tenant code
     * @return true if access is permitted
     */
    boolean hasAccess(UUID userSchoolId, String userPlatformRole, String tenantCode);
}
