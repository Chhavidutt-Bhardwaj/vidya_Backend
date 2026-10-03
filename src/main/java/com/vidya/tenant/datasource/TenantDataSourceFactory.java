package com.vidya.tenant.datasource;

import com.vidya.tenant.registry.TenantRegistration;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * Builds a {@link HikariDataSource} for a specific tenant.
 *
 * <p>Each tenant gets its own HikariCP connection pool pointing at its
 * dedicated PostgreSQL database. Pool parameters are shared across all tenants
 * but the JDBC URL, username, and password are tenant-specific.
 *
 * <p>Credentials are resolved at DataSource creation time via
 * {@link CredentialResolver}. Actual credentials are never stored in Java objects
 * beyond the lifetime of the pool initialization.
 */
@Component
public class TenantDataSourceFactory {

    private static final Logger log = LoggerFactory.getLogger(TenantDataSourceFactory.class);

    private final CredentialResolver credentialResolver;

    @Value("${vidya.tenant.datasource.hikari.pool-name-prefix:vidya-tenant-}")
    private String poolNamePrefix;

    @Value("${vidya.tenant.datasource.hikari.maximum-pool-size:5}")
    private int maximumPoolSize;

    @Value("${vidya.tenant.datasource.hikari.minimum-idle:1}")
    private int minimumIdle;

    @Value("${vidya.tenant.datasource.hikari.connection-timeout:30000}")
    private long connectionTimeout;

    @Value("${vidya.tenant.datasource.hikari.idle-timeout:300000}")
    private long idleTimeout;

    @Value("${vidya.tenant.datasource.hikari.max-lifetime:900000}")
    private long maxLifetime;

    public TenantDataSourceFactory(CredentialResolver credentialResolver) {
        this.credentialResolver = credentialResolver;
    }

    /**
     * Creates a new {@link HikariDataSource} for the given tenant.
     *
     * <p>Credentials are resolved from environment variables / secrets manager.
     *
     * @param registration the tenant's connection metadata
     * @return a configured, connected DataSource
     * @throws CredentialResolutionException if credentials cannot be resolved
     */
    public DataSource create(TenantRegistration registration) {
        log.info("Creating DataSource for tenant [{}] → database [{}@{}:{}]",
                registration.tenantCode(),
                registration.databaseName(),
                registration.databaseHost(),
                registration.databasePort());

        String username = credentialResolver.resolve(registration.databaseUsernameRef());
        String password = credentialResolver.resolve(registration.databasePasswordRef());

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(registration.jdbcUrl());
        config.setUsername(username);
        config.setPassword(password);
        config.setPoolName(poolNamePrefix + registration.tenantCode());
        config.setMaximumPoolSize(maximumPoolSize);
        config.setMinimumIdle(minimumIdle);
        config.setConnectionTimeout(connectionTimeout);
        config.setIdleTimeout(idleTimeout);
        config.setMaxLifetime(maxLifetime);
        config.setAutoCommit(false);

        // PostgreSQL tuning
        config.addDataSourceProperty("cachePrepStmts",       "true");
        config.addDataSourceProperty("prepStmtCacheSize",     "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        HikariDataSource dataSource = new HikariDataSource(config);

        log.info("DataSource created successfully for tenant [{}]", registration.tenantCode());
        return dataSource;
    }

    /**
     * Closes a DataSource, releasing all pooled connections.
     *
     * @param dataSource the DataSource to close
     * @param tenantCode identifier for logging
     */
    public void close(DataSource dataSource, String tenantCode) {
        if (dataSource instanceof HikariDataSource hikari) {
            log.info("Closing DataSource for tenant [{}]", tenantCode);
            hikari.close();
        }
    }
}
