package com.vidya.tenant.context;

/**
 * ThreadLocal-backed tenant context holder.
 *
 * <p><strong>Thread Safety:</strong> Uses {@link InheritableThreadLocal} deliberately
 * NOT used here — child threads (async tasks) must explicitly copy the tenant context
 * rather than inheriting it silently, which would risk tenant context leakage in
 * thread pools where threads are reused across tenants.
 *
 * <p><strong>Cleanup Contract:</strong> Callers (filters, interceptors) MUST call
 * {@link #clear()} in a {@code finally} block after the request/task is complete.
 * The {@link com.vidya.tenant.resolver.TenantResolutionFilter} enforces this guarantee.
 *
 * <p><strong>Do NOT use {@link InheritableThreadLocal}</strong> — in thread pools
 * (e.g., {@code @Async}, virtual threads), the parent context leaks into pooled
 * threads even after the parent request completes. This would cause Tenant A's
 * context to appear in Tenant B's database operations.
 */
public final class TenantContext {

    private static final ThreadLocal<String> TENANT_ID = new ThreadLocal<>();

    private TenantContext() {}

    /**
     * Sets the current tenant identifier for this thread.
     *
     * @param tenantCode the tenant's unique code (not the database name)
     * @throws IllegalArgumentException if tenantCode is null or blank
     */
    public static void setTenantCode(String tenantCode) {
        if (tenantCode == null || tenantCode.isBlank()) {
            throw new IllegalArgumentException("Tenant code must not be null or blank");
        }
        TENANT_ID.set(tenantCode.trim());
    }

    /**
     * Returns the current tenant identifier, or {@code null} if none is set.
     *
     * @return current tenant code, or {@code null}
     */
    public static String getTenantCode() {
        return TENANT_ID.get();
    }

    /**
     * Returns {@code true} if a tenant context is currently set.
     */
    public static boolean hasTenant() {
        return TENANT_ID.get() != null;
    }

    /**
     * Removes the tenant context from the current thread.
     *
     * <p>MUST be called in a {@code finally} block after every request or
     * async task that sets a tenant context.
     */
    public static void clear() {
        TENANT_ID.remove();
    }
}
