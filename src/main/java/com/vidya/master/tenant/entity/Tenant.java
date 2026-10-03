package com.vidya.master.tenant.entity;

import com.vidya.common.audit.AuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Technical isolation boundary for a school's operational data.
 *
 * <p>Each tenant maps to one PostgreSQL database.
 * The relationship is: Platform → School → Tenant → TenantDatabase.
 *
 * <p><strong>Credential storage:</strong> {@code databaseUsernameRef} and
 * {@code databasePasswordRef} store reference keys to environment variables
 * or a secrets manager — NEVER the actual credentials.
 *
 * <p><strong>Circular relationship:</strong> {@code School.tenantId → Tenant.id}
 * is populated after provisioning completes. During onboarding, school.tenantId is null.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "tenant", schema = "public",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_tenant_code",    columnNames = "tenant_code"),
                @UniqueConstraint(name = "uq_tenant_school",  columnNames = "school_id"),
                @UniqueConstraint(name = "uq_tenant_db_name", columnNames = "database_name"),
                @UniqueConstraint(name = "uq_tenant_db_id",   columnNames = "database_identifier")
        })
public class Tenant extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "tenant_code", nullable = false, length = 50)
    private String tenantCode;

    /** FK to school; not a JPA relationship to avoid eager loading overhead. */
    @Column(name = "school_id", nullable = false)
    private UUID schoolId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private TenantStatus status = TenantStatus.CREATED;

    @Column(name = "database_identifier", nullable = false, length = 100)
    private String databaseIdentifier;

    @Column(name = "database_host", nullable = false, length = 255)
    private String databaseHost;

    @Column(name = "database_port", nullable = false)
    private int databasePort = 5432;

    @Column(name = "database_name", nullable = false, length = 100)
    private String databaseName;

    /**
     * Environment variable name or vault path for the DB username.
     * Example: {@code TENANT_ABC123_DB_USER}
     * NEVER store the actual username here.
     */
    @Column(name = "database_username_ref", nullable = false, length = 500)
    private String databaseUsernameRef;

    /**
     * Environment variable name or vault path for the DB password.
     * Example: {@code TENANT_ABC123_DB_PASS}
     * NEVER store the actual password here.
     */
    @Column(name = "database_password_ref", nullable = false, length = 500)
    private String databasePasswordRef;

    @Column(name = "schema_version", length = 50)
    private String schemaVersion;

    @Column(name = "provisioning_status", length = 50)
    private String provisioningStatus;

    @Column(name = "provisioned_at")
    private Instant provisionedAt;

    @Column(name = "suspended_at")
    private Instant suspendedAt;

    // -------------------------------------------------------------------------
    // Factory
    // -------------------------------------------------------------------------

    public static Tenant create(UUID schoolId,
                                String tenantCode,
                                String databaseHost,
                                int databasePort,
                                String databaseName,
                                String databaseUsernameRef,
                                String databasePasswordRef) {
        Tenant tenant = new Tenant();
        tenant.schoolId             = schoolId;
        tenant.tenantCode           = tenantCode;
        tenant.databaseIdentifier   = databaseName; // same as dbName for PG database-per-tenant
        tenant.databaseHost         = databaseHost;
        tenant.databasePort         = databasePort;
        tenant.databaseName         = databaseName;
        tenant.databaseUsernameRef  = databaseUsernameRef;
        tenant.databasePasswordRef  = databasePasswordRef;
        tenant.status               = TenantStatus.CREATED;
        return tenant;
    }

    // -------------------------------------------------------------------------
    // Business methods
    // -------------------------------------------------------------------------

    public boolean isActive() {
        return TenantStatus.ACTIVE == this.status;
    }

    public boolean isSuspended() {
        return TenantStatus.SUSPENDED == this.status;
    }

    public boolean isProvisionable() {
        return this.status == TenantStatus.CREATED
                || this.status == TenantStatus.PROVISIONING_FAILED;
    }

    public void markProvisioning() {
        this.status = TenantStatus.PROVISIONING;
    }

    public void markProvisioned() {
        this.status      = TenantStatus.PROVISIONED;
        this.provisionedAt = Instant.now();
    }

    public void markActive() {
        this.status = TenantStatus.ACTIVE;
    }

    public void markSuspended() {
        this.status      = TenantStatus.SUSPENDED;
        this.suspendedAt = Instant.now();
    }

    public void markProvisioningFailed() {
        this.status = TenantStatus.PROVISIONING_FAILED;
    }
}
