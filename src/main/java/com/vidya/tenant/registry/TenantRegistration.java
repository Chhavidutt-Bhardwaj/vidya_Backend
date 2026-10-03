package com.vidya.tenant.registry;

import java.io.Serializable;
import java.util.UUID;

/**
 * Immutable snapshot of a tenant's connection information.
 *
 * <p>This record is what the {@link TenantRegistryService} caches in Redis
 * and what the {@link com.vidya.tenant.datasource.TenantDataSourceFactory}
 * uses to build a {@link javax.sql.DataSource}.
 *
 * <p><strong>Security:</strong> The actual database username and password
 * are NOT stored here. Only the reference keys are stored, which are resolved
 * at DataSource creation time via {@link com.vidya.tenant.datasource.CredentialResolver}.
 *
 * <p>Implements {@link Serializable} for Redis serialization.
 */
public record TenantRegistration(
        UUID    tenantId,
        String  tenantCode,
        UUID    schoolId,
        String  status,
        String  databaseHost,
        int     databasePort,
        String  databaseName,
        String  databaseUsernameRef,
        String  databasePasswordRef
) implements Serializable {

    /** Returns true if this tenant is in an operational state that allows DB access. */
    public boolean isAccessible() {
        return "ACTIVE".equals(status) || "PROVISIONED".equals(status);
    }

    /** Returns true if this tenant is suspended. */
    public boolean isSuspended() {
        return "SUSPENDED".equals(status);
    }

    /** Builds the JDBC URL for this tenant's database. */
    public String jdbcUrl() {
        return String.format("jdbc:postgresql://%s:%d/%s",
                databaseHost, databasePort, databaseName);
    }
}
