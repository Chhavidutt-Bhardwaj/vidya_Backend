package com.vidya.tenant.registry;

import com.vidya.master.tenant.entity.Tenant;
import com.vidya.master.tenant.repository.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Default implementation of {@link TenantRegistryService}.
 *
 * <p>Uses a Redis cache on top of the master DB for fast tenant lookups
 * on every request. Cache miss falls through to the database.
 *
 * <p><strong>Cache strategy:</strong>
 * <ul>
 *   <li>Key: {@code vidya:tenant:code:{tenantCode}}</li>
 *   <li>TTL: configurable (default 10 minutes)</li>
 *   <li>Eviction: explicit on status change or reconnect detail change</li>
 * </ul>
 *
 * <p><strong>Access control:</strong> Platform super admins and platform admins
 * have cross-tenant access. All other roles are restricted to their own school's tenant.
 */
@Service
@Transactional(readOnly = true, transactionManager = "masterTransactionManager")
public class TenantRegistryServiceImpl implements TenantRegistryService {

    private static final Logger log = LoggerFactory.getLogger(TenantRegistryServiceImpl.class);

    static final String CACHE_KEY_PREFIX = "vidya:tenant:code:";

    /** Platform roles that can access any tenant. */
    private static final Set<String> PLATFORM_ADMIN_ROLES = Set.of(
            "PLATFORM_SUPER_ADMIN",
            "PLATFORM_ADMIN"
    );

    private final TenantRepository tenantRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final long cacheTtlSeconds;

    public TenantRegistryServiceImpl(
            TenantRepository tenantRepository,
            RedisTemplate<String, Object> redisTemplate,
            @org.springframework.beans.factory.annotation.Value(
                    "${vidya.redis.ttl.tenant-registry-seconds:600}") long cacheTtlSeconds) {
        this.tenantRepository = tenantRepository;
        this.redisTemplate    = redisTemplate;
        this.cacheTtlSeconds  = cacheTtlSeconds;
    }

    // -------------------------------------------------------------------------
    // Lookups
    // -------------------------------------------------------------------------

    @Override
    public Optional<TenantRegistration> findByTenantCode(String tenantCode) {
        String cacheKey = CACHE_KEY_PREFIX + tenantCode;

        // 1. Try cache
        try {
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached instanceof TenantRegistration registration) {
                log.debug("Tenant cache hit for code: {}", tenantCode);
                return Optional.of(registration);
            }
        } catch (Exception e) {
            // Redis failure must not prevent requests — fall through to DB
            log.warn("Redis lookup failed for tenant [{}]; falling back to DB: {}", tenantCode, e.getMessage());
        }

        // 2. Cache miss — load from master DB
        Optional<Tenant> tenant = tenantRepository.findByTenantCode(tenantCode);

        tenant.ifPresent(t -> {
            TenantRegistration registration = toRegistration(t);
            try {
                redisTemplate.opsForValue().set(cacheKey, registration, Duration.ofSeconds(cacheTtlSeconds));
                log.debug("Tenant cached for code: {}", tenantCode);
            } catch (Exception e) {
                log.warn("Failed to cache tenant [{}] in Redis: {}", tenantCode, e.getMessage());
            }
        });

        return tenant.map(this::toRegistration);
    }

    @Override
    public Optional<TenantRegistration> findBySchoolId(UUID schoolId) {
        return tenantRepository.findBySchoolId(schoolId).map(this::toRegistration);
    }

    // -------------------------------------------------------------------------
    // Cache eviction
    // -------------------------------------------------------------------------

    @Override
    public void evictCache(String tenantCode) {
        String cacheKey = CACHE_KEY_PREFIX + tenantCode;
        try {
            Boolean deleted = redisTemplate.delete(cacheKey);
            log.info("Evicted tenant cache for code: {} (deleted={})", tenantCode, deleted);
        } catch (Exception e) {
            log.warn("Failed to evict tenant cache for [{}]: {}", tenantCode, e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Access control
    // -------------------------------------------------------------------------

    @Override
    public boolean hasAccess(UUID userSchoolId, String userPlatformRole, String tenantCode) {
        // Platform admins can access any tenant
        if (userPlatformRole != null && PLATFORM_ADMIN_ROLES.contains(userPlatformRole)) {
            return true;
        }

        // School-level users: their school's tenant must match the requested tenantCode
        if (userSchoolId == null) {
            log.warn("Non-platform user has no schoolId claim; denying access to tenant [{}]", tenantCode);
            return false;
        }

        Optional<TenantRegistration> registration = findBySchoolId(userSchoolId);
        boolean allowed = registration
                .map(r -> r.tenantCode().equals(tenantCode))
                .orElse(false);

        if (!allowed) {
            log.warn("User with schoolId [{}] attempted unauthorized access to tenant [{}]",
                    userSchoolId, tenantCode);
        }
        return allowed;
    }

    // -------------------------------------------------------------------------
    // Mapper
    // -------------------------------------------------------------------------

    private TenantRegistration toRegistration(Tenant tenant) {
        return new TenantRegistration(
                tenant.getId(),
                tenant.getTenantCode(),
                tenant.getSchoolId(),
                tenant.getStatus().name(),
                tenant.getDatabaseHost(),
                tenant.getDatabasePort(),
                tenant.getDatabaseName(),
                tenant.getDatabaseUsernameRef(),
                tenant.getDatabasePasswordRef()
        );
    }
}
