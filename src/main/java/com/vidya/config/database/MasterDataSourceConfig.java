package com.vidya.config.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Master DataSource configuration.
 *
 * <p>This configuration creates the {@code @Primary} DataSource used for all
 * master database operations (school, tenant, community, onboarding, subscription,
 * payment records).
 *
 * <p>Key design decisions:
 * <ul>
 *   <li>Marked {@code @Primary} so Spring defaults to this datasource for
 *       all master entity repositories</li>
 *   <li>Scans {@code com.vidya.master.*} and {@code com.vidya.security.user.*}
 *       packages for JPA entities</li>
 *   <li>Separate {@code masterTransactionManager} bean — required when using
 *       multiple DataSources to avoid accidental cross-datasource transactions</li>
 *   <li>JPA auditing enabled here with a Spring Security–aware {@code AuditorAware}
 *       that extracts the current user's UUID from the SecurityContext</li>
 * </ul>
 */
@Configuration
@EnableTransactionManagement
@EnableJpaAuditing(auditorAwareRef = "masterAuditorAware")
@EnableJpaRepositories(
        basePackages = {
                "com.vidya.master",
                "com.vidya.security"
        },
        entityManagerFactoryRef = "masterEntityManagerFactory",
        transactionManagerRef   = "masterTransactionManager"
)
public class MasterDataSourceConfig {

    @Value("${vidya.datasource.master.url}")
    private String url;

    @Value("${vidya.datasource.master.username}")
    private String username;

    @Value("${vidya.datasource.master.password}")
    private String password;

    @Value("${vidya.datasource.master.hikari.pool-name:vidya-master-pool}")
    private String poolName;

    @Value("${vidya.datasource.master.hikari.maximum-pool-size:10}")
    private int maximumPoolSize;

    @Value("${vidya.datasource.master.hikari.minimum-idle:2}")
    private int minimumIdle;

    @Value("${vidya.datasource.master.hikari.connection-timeout:30000}")
    private long connectionTimeout;

    @Value("${vidya.datasource.master.hikari.idle-timeout:600000}")
    private long idleTimeout;

    @Value("${vidya.datasource.master.hikari.max-lifetime:1800000}")
    private long maxLifetime;

    @Value("${spring.jpa.show-sql:false}")
    private boolean showSql;

    // -------------------------------------------------------------------------
    // DataSource
    // -------------------------------------------------------------------------

    @Primary
    @Bean(name = "masterDataSource")
    public DataSource masterDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setPoolName(poolName);
        config.setMaximumPoolSize(maximumPoolSize);
        config.setMinimumIdle(minimumIdle);
        config.setConnectionTimeout(connectionTimeout);
        config.setIdleTimeout(idleTimeout);
        config.setMaxLifetime(maxLifetime);
        config.setAutoCommit(false);

        // PostgreSQL-specific tuning
        config.addDataSourceProperty("cachePrepStmts",          "true");
        config.addDataSourceProperty("prepStmtCacheSize",        "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit",    "2048");
        config.addDataSourceProperty("useServerPrepStmts",       "true");

        return new HikariDataSource(config);
    }

    // -------------------------------------------------------------------------
    // EntityManagerFactory
    // -------------------------------------------------------------------------

    @Primary
    @DependsOn("masterFlyway")
    @Bean(name = "masterEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean masterEntityManagerFactory() {
        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(masterDataSource());
        factory.setPackagesToScan(
                "com.vidya.master",
                "com.vidya.security"
        );
        factory.setPersistenceUnitName("master");

        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        vendorAdapter.setShowSql(showSql);
        factory.setJpaVendorAdapter(vendorAdapter);
        factory.setJpaPropertyMap(jpaProperties());

        return factory;
    }

    private Map<String, Object> jpaProperties() {
        Map<String, Object> props = new HashMap<>();
        props.put("hibernate.dialect",                       "org.hibernate.dialect.PostgreSQLDialect");
        props.put("hibernate.hbm2ddl.auto",                  "validate");  // Flyway manages DDL
        props.put("hibernate.jdbc.time_zone",                "UTC");
        props.put("hibernate.generate_statistics",           "false");
        props.put("hibernate.jdbc.batch_size",               "20");
        props.put("hibernate.order_inserts",                 "true");
        props.put("hibernate.order_updates",                 "true");
        props.put("hibernate.jdbc.batch_versioned_data",     "true");
        return props;
    }

    // -------------------------------------------------------------------------
    // TransactionManager
    // -------------------------------------------------------------------------

    @Primary
    @Bean(name = "masterTransactionManager")
    public PlatformTransactionManager masterTransactionManager(
            @Qualifier("masterEntityManagerFactory") EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }

    // -------------------------------------------------------------------------
    // Auditing — AuditorAware
    // -------------------------------------------------------------------------

    /**
     * Provides the current user's UUID for JPA auditing.
     *
     * <p>Resolves the principal from the Spring Security context.
     * Returns {@code Optional.empty()} for system/background operations
     * (e.g., provisioning jobs) where no user context is available.
     */
    @Bean(name = "masterAuditorAware")
    public AuditorAware<UUID> masterAuditorAware() {
        return () -> {
            try {
                org.springframework.security.core.Authentication authentication =
                        org.springframework.security.core.context.SecurityContextHolder
                                .getContext().getAuthentication();

                if (authentication == null || !authentication.isAuthenticated()) {
                    return Optional.empty();
                }

                Object principal = authentication.getPrincipal();
                if (principal instanceof com.vidya.security.user.PlatformUserDetails userDetails) {
                    return Optional.of(userDetails.getUserId());
                }
            } catch (Exception ignored) {
                // No security context available (background job, test, provisioning)
            }
            return Optional.empty();
        };
    }
}
