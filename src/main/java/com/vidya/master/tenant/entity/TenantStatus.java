package com.vidya.master.tenant.entity;

/**
 * Lifecycle states for a tenant.
 *
 * <p>Valid transitions:
 * <pre>
 * CREATED → PROVISIONING → PROVISIONED → ACTIVE → SUSPENDED → ACTIVE
 *                      ↘ PROVISIONING_FAILED (retryable)
 * ACTIVE → DEACTIVATED → ARCHIVED
 * </pre>
 *
 * <p>A tenant in SUSPENDED state still has its database intact.
 * Operations against it return {@link com.vidya.common.exception.TenantSuspendedException}.
 */
public enum TenantStatus {

    /** Tenant record created in master DB; database not yet provisioned. */
    CREATED,

    /** Database creation and migration in progress. */
    PROVISIONING,

    /** Database created and migrated successfully; awaiting school activation. */
    PROVISIONED,

    /** Fully operational; school can log in and use the system. */
    ACTIVE,

    /** Temporarily disabled (e.g. payment lapsed); database intact. */
    SUSPENDED,

    /** Permanently disabled; database may be archived or scheduled for deletion. */
    DEACTIVATED,

    /** Archived; database may have been dropped or backed up to cold storage. */
    ARCHIVED,

    /** Provisioning attempt failed; safe to retry. */
    PROVISIONING_FAILED
}
