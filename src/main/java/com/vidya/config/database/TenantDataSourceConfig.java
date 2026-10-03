package com.vidya.config.database;

import com.vidya.tenant.datasource.TenantDataSourceManager;
import com.vidya.tenant.datasource.TenantRoutingDataSource;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * Tenant DataSource configuration.
 *
 * <p>Wires the {@link TenantRoutingDataSource} as a secondary {@link DataSource}
 * used for all tenant operational data (the {@code tenantdata.*} packages).
 *
 * <p>Key design decisions:
 * <ul>
 *   <li>NOT {@code @Primary} — the master DataSource is primary</li>
 *   <li>Separate {@code tenantEntityManagerFactory} scanning {@code com.vidya.tenantdata.*}</li>
 *   <li>Separate {@code tenantTransactionManager} — prevents master transactions
 *       from accidentally opening tenant connections</li>
 *   <li>{@code hibernate.hbm2ddl.auto = none} and metadata access disabled at startup
 *       because tenant connections are dynamically routed per request at runtime.</li>
 * </ul>
 *
 * <p><strong>Usage in services:</strong>
 * <pre>
 *   {@code @Transactional(transactionManager = "tenantTransactionManager")}
 * </pre>
 */
@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
        basePackages = {"com.vidya.tenantdata"},
        entityManagerFactoryRef = "tenantEntityManagerFactory",
        transactionManagerRef   = "tenantTransactionManager"
)
public class TenantDataSourceConfig {

    @Value("${spring.jpa.show-sql:false}")
    private boolean showSql;

    // -------------------------------------------------------------------------
    // DataSource — TenantRoutingDataSource
    // -------------------------------------------------------------------------

    @Bean(name = "tenantDataSource")
    public DataSource tenantDataSource(TenantDataSourceManager tenantDataSourceManager) {
        // TenantRoutingDataSource does NOT pre-configure a target map —
        // it delegates 100% to TenantDataSourceManager dynamically.
        TenantRoutingDataSource routingDataSource =
                new TenantRoutingDataSource(tenantDataSourceManager);

        // AbstractRoutingDataSource requires a default target even if we override
        // determineTargetDataSource. We set null here since our override handles everything.
        // The parent method is never called in practice.
        routingDataSource.setTargetDataSources(Map.of());
        routingDataSource.afterPropertiesSet();

        return routingDataSource;
    }

    // -------------------------------------------------------------------------
    // EntityManagerFactory
    // -------------------------------------------------------------------------

    @Bean(name = "tenantEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean tenantEntityManagerFactory(
            TenantDataSourceManager tenantDataSourceManager) {

        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(tenantDataSource(tenantDataSourceManager));
        factory.setPackagesToScan("com.vidya.tenantdata");
        factory.setPersistenceUnitName("tenant");

        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        vendorAdapter.setShowSql(showSql);
        factory.setJpaVendorAdapter(vendorAdapter);
        factory.setJpaPropertyMap(jpaProperties());

        return factory;
    }

    private Map<String, Object> jpaProperties() {
        Map<String, Object> props = new HashMap<>();
        props.put("hibernate.dialect",                           "org.hibernate.dialect.PostgreSQLDialect");
        props.put("hibernate.hbm2ddl.auto",                       "none");   // Tenant DDL is managed by Flyway per tenant
        props.put("hibernate.temp.use_jdbc_metadata_defaults",   "false");  // Prevent startup connection acquisition
        props.put("hibernate.boot.allow_jdbc_metadata_access",   "false");  // Prevent startup connection acquisition
        props.put("hibernate.jdbc.time_zone",                     "UTC");
        props.put("hibernate.generate_statistics",                "false");
        props.put("hibernate.jdbc.batch_size",                    "20");
        props.put("hibernate.order_inserts",                      "true");
        props.put("hibernate.order_updates",                      "true");
        props.put("hibernate.jdbc.batch_versioned_data",          "true");
        return props;
    }

    // -------------------------------------------------------------------------
    // TransactionManager
    // -------------------------------------------------------------------------

    @Bean(name = "tenantTransactionManager")
    public PlatformTransactionManager tenantTransactionManager(
            @Qualifier("tenantEntityManagerFactory") EntityManagerFactory tenantEntityManagerFactory) {
        return new JpaTransactionManager(tenantEntityManagerFactory);
    }
}
