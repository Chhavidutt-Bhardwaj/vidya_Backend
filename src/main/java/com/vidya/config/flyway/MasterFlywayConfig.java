package com.vidya.config.flyway;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Flyway configuration for the Master database.
 *
 * <p>Runs automatically at application startup via the {@code masterFlyway} bean
 * initialization. Manages the schema of {@code vidya_master}.
 *
 * <p>Migration files location: {@code classpath:db/migration/master/}
 * Naming convention: {@code V{n}__{description}.sql}
 *
 * <p>Design decisions:
 * <ul>
 *   <li>{@code spring.flyway.enabled=false} in application.yml — we control
 *       Flyway programmatically to support dual-datasource (master + tenant)</li>
 *   <li>Master Flyway runs exactly once at startup; tenant Flyway runs
 *       programmatically during provisioning in {@code TenantMigrationService}</li>
 *   <li>Out-of-order migrations disabled — strict sequential ordering enforced</li>
 *   <li>Baseline on migrate disabled — fail fast if schema is in an unexpected state</li>
 * </ul>
 */
@Configuration
public class MasterFlywayConfig {

    private static final Logger log = LoggerFactory.getLogger(MasterFlywayConfig.class);

    @Value("${vidya.flyway.master.locations:classpath:db/migration/master}")
    private String locations;

    /**
     * Creates and runs the master Flyway migration at application startup.
     *
     * <p>The bean is eagerly initialized, so migrations execute before
     * the rest of the application context is fully started.
     */
    @Bean(name = "masterFlyway", initMethod = "migrate")
    public Flyway masterFlyway(@Qualifier("masterDataSource") DataSource masterDataSource) {
        log.info("Initializing master Flyway migration from locations: {}", locations);

        Flyway flyway = Flyway.configure()
                .dataSource(masterDataSource)
                .locations(locations)
                .table("flyway_schema_history")           // default; explicit for clarity
                .baselineOnMigrate(false)
                .outOfOrder(false)
                .validateOnMigrate(true)
                .cleanDisabled(true)                      // NEVER allow clean in any environment
                .loggers("slf4j")
                .load();

        log.info("Master Flyway configuration loaded. Running migrations...");
        return flyway;
    }
}
