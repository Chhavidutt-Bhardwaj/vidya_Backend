package com.vidya.config.flyway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Tenant Flyway configuration holder.
 *
 * <p>Unlike {@link MasterFlywayConfig}, tenant Flyway is NOT a Spring bean
 * that auto-runs on startup. Instead, it is instantiated programmatically
 * in {@link com.vidya.tenant.provisioning.TenantMigrationService} whenever
 * a new tenant database is provisioned.
 *
 * <p>This class simply holds the configuration values for reference.
 * The actual Flyway instance is built in {@code TenantMigrationService}
 * using the {@code vidya.flyway.tenant.locations} property.
 *
 * <p>Migration files: {@code src/main/resources/db/migration/tenant/}
 */
@Configuration
public class TenantFlywayConfig {

    @Value("${vidya.flyway.tenant.locations:classpath:db/migration/tenant}")
    private String locations;

    public String getLocations() {
        return locations;
    }
}
