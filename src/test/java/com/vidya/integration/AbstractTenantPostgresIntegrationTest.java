package com.vidya.integration;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;

/**
 * Base class for integration tests running against a real PostgreSQL container.
 *
 * <p>Applies all tenant Flyway migrations (V1 through V13) to verify schema,
 * partial unique indexes, check constraints, foreign keys, and soft delete coexistence.
 */
@Testcontainers
public abstract class AbstractTenantPostgresIntegrationTest {

    @Container
    protected static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("tenant_integration_test")
            .withUsername("test_user")
            .withPassword("test_password");

    protected static DataSource dataSource;
    protected static JdbcTemplate jdbcTemplate;

    @BeforeAll
    static void setUpDatabase() {
        postgres.start();

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(postgres.getJdbcUrl());
        config.setUsername(postgres.getUsername());
        config.setPassword(postgres.getPassword());
        config.setDriverClassName(postgres.getDriverClassName());
        config.setMaximumPoolSize(15);

        dataSource = new HikariDataSource(config);
        jdbcTemplate = new JdbcTemplate(dataSource);

        // Run tenant Flyway migrations V1..V13
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration/tenant")
                .table("flyway_schema_history")
                .baselineOnMigrate(false)
                .load();

        flyway.migrate();
    }

    @AfterAll
    static void tearDownDatabase() {
        if (dataSource instanceof HikariDataSource hikari) {
            hikari.close();
        }
    }
}
