package com.vidya.tenant.provisioning;

import com.vidya.common.exception.TenantProvisioningException;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.output.MigrateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * Runs Flyway migrations against a newly created tenant database.
 *
 * <p>This service runs the {@code db/migration/tenant/**} migrations against
 * the tenant's DataSource. It is called during provisioning after the
 * database is created.
 *
 * <p><strong>Idempotency:</strong> Flyway is inherently idempotent — if all
 * migrations have already been applied, it is a no-op. This is safe to call
 * on a retry after a previous partial run.
 *
 * <p><strong>Schema history table:</strong> Uses the default
 * {@code flyway_schema_history} table within each tenant database.
 * This is the same table name as the master, but it lives in a different DB.
 */
@Component
public class TenantMigrationService {

    private static final Logger log = LoggerFactory.getLogger(TenantMigrationService.class);

    @Value("${vidya.flyway.tenant.locations:classpath:db/migration/tenant}")
    private String tenantMigrationLocations;

    /**
     * Applies all pending tenant Flyway migrations to the given DataSource.
     *
     * @param tenantDataSource the tenant's DataSource (pointing at the tenant DB)
     * @param tenantCode       used for logging
     * @throws TenantProvisioningException if migration fails
     */
    public void migrate(DataSource tenantDataSource, String tenantCode) {
        log.info("[Provisioning][{}] Running tenant Flyway migrations from: {}",
                tenantCode, tenantMigrationLocations);

        try {
            Flyway flyway = Flyway.configure()
                    .dataSource(tenantDataSource)
                    .locations(tenantMigrationLocations)
                    .table("flyway_schema_history")
                    .baselineOnMigrate(false)
                    .outOfOrder(false)
                    .validateOnMigrate(true)
                    .cleanDisabled(true)   // NEVER allow clean on tenant DB
                    .loggers("slf4j")
                    .load();

            // Log pending migrations before running
            MigrationInfo[] pending = flyway.info().pending();
            log.info("[Provisioning][{}] {} pending migration(s) to apply", tenantCode, pending.length);

            MigrateResult result = flyway.migrate();

            log.info("[Provisioning][{}] Migrations complete: {} applied, {} total version: {}",
                    tenantCode,
                    result.migrationsExecuted,
                    result.success,
                    result.targetSchemaVersion);

            if (!result.success) {
                throw new TenantProvisioningException(tenantCode, "MIGRATION_RUNNING",
                        "Flyway migration did not complete successfully");
            }

        } catch (TenantProvisioningException e) {
            throw e;
        } catch (Exception e) {
            throw new TenantProvisioningException(tenantCode, "MIGRATION_RUNNING",
                    "Flyway migration failed: " + e.getMessage(), e);
        }
    }
}
