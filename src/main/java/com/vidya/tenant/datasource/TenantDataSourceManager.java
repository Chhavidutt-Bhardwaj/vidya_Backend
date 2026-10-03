package com.vidya.tenant.datasource;

import com.vidya.tenant.registry.TenantRegistration;
import com.vidya.tenant.registry.TenantRegistryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the lifecycle of per-tenant {@link DataSource} instances.
 *
 * <p><strong>Caching:</strong> DataSources are lazily created on first access
 * and stored in a {@link ConcurrentHashMap}. The cache prevents opening a new
 * physical connection pool on every request.
 *
 * <p><strong>Thread safety:</strong> The underlying map is a
 * {@link ConcurrentHashMap}. However, DataSource creation is synchronized
 * per tenant code using {@code computeIfAbsent} to prevent duplicate pools
 * being created in high-concurrency scenarios (the double-checked locking
 * problem is avoided by {@code computeIfAbsent}'s atomicity guarantee).
 *
 * <p><strong>Cache size:</strong> Bounded by {@code vidya.tenant.datasource.cache.max-size}.
 * If the cache exceeds the limit, the caller receives a warning log.
 * Full LRU eviction is a Phase 02 enhancement.
 *
 * <p><strong>Eviction:</strong> {@link #evict(String)} closes the pool and
 * removes it from the cache. Must be called when a tenant is suspended,
 * deactivated, or its connection details change.
 */
@Component
public class TenantDataSourceManager {

    private static final Logger log = LoggerFactory.getLogger(TenantDataSourceManager.class);

    private final ConcurrentHashMap<String, DataSource> dataSourceCache = new ConcurrentHashMap<>();

    private final TenantRegistryService tenantRegistryService;
    private final TenantDataSourceFactory dataSourceFactory;

    @Value("${vidya.tenant.datasource.cache.max-size:200}")
    private int maxCacheSize;

    public TenantDataSourceManager(TenantRegistryService tenantRegistryService,
                                   TenantDataSourceFactory dataSourceFactory) {
        this.tenantRegistryService = tenantRegistryService;
        this.dataSourceFactory     = dataSourceFactory;
    }

    /**
     * Returns the {@link DataSource} for the given tenant code.
     *
     * <p>Creates and caches the DataSource on the first call for a tenant.
     * Subsequent calls return the cached instance.
     *
     * @param tenantCode the tenant's unique code
     * @return the tenant's DataSource
     * @throws com.vidya.common.exception.TenantNotFoundException if tenant not found in registry
     * @throws com.vidya.common.exception.TenantSuspendedException if tenant is suspended
     */
    public DataSource getDataSource(String tenantCode) {
        return dataSourceCache.computeIfAbsent(tenantCode, code -> {
            log.info("DataSource cache miss for tenant [{}]; loading from registry...", code);

            if (dataSourceCache.size() >= maxCacheSize) {
                log.warn("Tenant DataSource cache is at capacity ({}). " +
                        "Consider increasing vidya.tenant.datasource.cache.max-size " +
                        "or implementing LRU eviction.", maxCacheSize);
            }

            TenantRegistration registration = tenantRegistryService
                    .findByTenantCode(code)
                    .orElseThrow(() -> new com.vidya.common.exception.TenantNotFoundException(code));

            if (registration.isSuspended()) {
                throw new com.vidya.common.exception.TenantSuspendedException(code);
            }

            if (!registration.isAccessible()) {
                throw new com.vidya.common.exception.TenantNotFoundException(
                        "Tenant [" + code + "] is not in an accessible state: " + registration.status());
            }

            return dataSourceFactory.create(registration);
        });
    }

    /**
     * Evicts and closes the DataSource for the given tenant.
     *
     * <p>Must be called when:
     * <ul>
     *   <li>A tenant is suspended or deactivated</li>
     *   <li>A tenant's database host/name changes (migration)</li>
     *   <li>A tenant's credentials are rotated</li>
     * </ul>
     *
     * @param tenantCode the tenant to evict
     */
    public void evict(String tenantCode) {
        DataSource removed = dataSourceCache.remove(tenantCode);
        if (removed != null) {
            dataSourceFactory.close(removed, tenantCode);
            log.info("Evicted and closed DataSource for tenant [{}]", tenantCode);
        } else {
            log.debug("No cached DataSource found for tenant [{}]; nothing to evict", tenantCode);
        }
    }

    /**
     * Registers a pre-built DataSource for a tenant.
     *
     * <p>Used during tenant provisioning to register the freshly created
     * DataSource immediately, before the first tenant request arrives.
     *
     * @param tenantCode the tenant code
     * @param dataSource the fully initialized DataSource
     */
    public void register(String tenantCode, DataSource dataSource) {
        DataSource existing = dataSourceCache.put(tenantCode, dataSource);
        if (existing != null) {
            log.warn("Replaced existing DataSource for tenant [{}]", tenantCode);
            dataSourceFactory.close(existing, tenantCode);
        }
        log.info("Registered DataSource for tenant [{}]", tenantCode);
    }

    /**
     * Returns the number of currently cached DataSources.
     */
    public int cacheSize() {
        return dataSourceCache.size();
    }

    /**
     * Returns true if a DataSource is currently cached for the given tenant.
     */
    public boolean isCached(String tenantCode) {
        return dataSourceCache.containsKey(tenantCode);
    }
}
