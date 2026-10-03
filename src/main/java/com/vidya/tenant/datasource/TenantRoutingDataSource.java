package com.vidya.tenant.datasource;

import com.vidya.tenant.context.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import javax.sql.DataSource;

/**
 * Dynamic DataSource router for multi-tenant database access.
 *
 * <p>Extends Spring's {@link AbstractRoutingDataSource} to delegate
 * connection lookup to the {@link TenantDataSourceManager} based on the
 * current thread's tenant context.
 *
 * <p><strong>Routing key:</strong> {@link TenantContext#getTenantCode()} —
 * this is set by the {@link com.vidya.tenant.resolver.TenantResolutionFilter}
 * after authentication and tenant validation.
 *
 * <p><strong>Override of determineTargetDataSource:</strong> We override the
 * parent's lookup logic entirely because the parent uses a pre-configured
 * {@code Map<Object, DataSource>} that cannot support dynamic tenant creation.
 * Our implementation delegates to {@link TenantDataSourceManager} which
 * lazily creates and caches DataSources.
 *
 * <p><strong>Null check:</strong> If no tenant context is set (e.g., public
 * endpoints, health check), this datasource is not invoked — requests are
 * handled by the master DataSource only.
 */
public class TenantRoutingDataSource extends AbstractRoutingDataSource {

    private static final Logger log = LoggerFactory.getLogger(TenantRoutingDataSource.class);

    private final TenantDataSourceManager tenantDataSourceManager;

    public TenantRoutingDataSource(TenantDataSourceManager tenantDataSourceManager) {
        this.tenantDataSourceManager = tenantDataSourceManager;
    }

    /**
     * Returns the current tenant code as the routing lookup key.
     */
    @Override
    protected Object determineCurrentLookupKey() {
        return TenantContext.getTenantCode();
    }

    /**
     * Overrides parent to delegate to {@link TenantDataSourceManager}.
     *
     * <p>This bypasses the parent's static map lookup and uses the dynamic
     * per-tenant DataSource cache instead.
     */
    @Override
    protected DataSource determineTargetDataSource() {
        String tenantCode = TenantContext.getTenantCode();

        if (tenantCode == null) {
            // No tenant context — this should not happen for tenant-scoped operations.
            // The TenantResolutionFilter guarantees tenantCode is set before
            // any tenant repository is accessed.
            log.error("TenantRoutingDataSource.determineTargetDataSource() called with no tenant context. " +
                    "This indicates a missing TenantResolutionFilter or incorrect @Transactional qualifier.");
            throw new IllegalStateException(
                    "No tenant context is set. Ensure TenantResolutionFilter has run " +
                    "and use @Transactional(transactionManager=\"tenantTransactionManager\") " +
                    "for tenant-scoped operations.");
        }

        log.debug("Routing to tenant DataSource: {}", tenantCode);
        return tenantDataSourceManager.getDataSource(tenantCode);
    }
}
