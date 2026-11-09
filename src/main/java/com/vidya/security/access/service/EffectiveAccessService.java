package com.vidya.security.access.service;

import com.vidya.master.subscription.service.SubscriptionEntitlementService;
import com.vidya.security.access.AccessScope;
import com.vidya.security.access.entity.AccessPermission;
import com.vidya.security.access.entity.AccessRole;
import com.vidya.security.access.entity.UserPermissionOverride;
import com.vidya.security.access.entity.UserRoleAssignment;
import com.vidya.security.access.repository.AccessPermissionRepository;
import com.vidya.security.access.repository.AccessRoleRepository;
import com.vidya.security.access.repository.UserPermissionOverrideRepository;
import com.vidya.security.access.repository.UserRoleAssignmentRepository;
import com.vidya.security.entity.PlatformRole;
import com.vidya.security.entity.PlatformUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves a user's effective access from the database:
 *
 * <ol>
 *   <li>Roles = the system role matching {@code platform_user.platform_role} + every effective
 *       {@code user_role_assignment}. School custom roles only count for users of that school, and
 *       a role's scope must match the account's scope.</li>
 *   <li>Permissions = union of the roles' active grants ({@code grants_all_permissions} = every
 *       active permission), then user GRANT overrides added, then user DENY overrides removed.</li>
 *   <li>School-bound users lose PLATFORM-scope permissions, and (with feature gating on) every
 *       permission whose {@code feature_code} is not enabled for their school.</li>
 * </ol>
 *
 * <p>Results are cached per user for {@code vidya.security.access.cache-ttl-seconds} (default 60).
 * Changes made through the APIs evict the cache after commit; changes written straight to the DB
 * apply when the TTL runs out. The cache is per instance.
 */
@Service
@Slf4j
public class EffectiveAccessService {

    private final AccessRoleRepository roleRepository;
    private final AccessPermissionRepository permissionRepository;
    private final UserRoleAssignmentRepository assignmentRepository;
    private final UserPermissionOverrideRepository overrideRepository;
    private final SubscriptionEntitlementService entitlementService;
    private final Duration ttl;
    private final boolean featureGatingEnabled;

    private final Map<UUID, CachedAccess> cache = new ConcurrentHashMap<>();

    public EffectiveAccessService(AccessRoleRepository roleRepository,
                                  AccessPermissionRepository permissionRepository,
                                  UserRoleAssignmentRepository assignmentRepository,
                                  UserPermissionOverrideRepository overrideRepository,
                                  SubscriptionEntitlementService entitlementService,
                                  @Value("${vidya.security.access.cache-ttl-seconds:60}") long ttlSeconds,
                                  @Value("${vidya.security.access.feature-gating-enabled:true}") boolean featureGatingEnabled) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.assignmentRepository = assignmentRepository;
        this.overrideRepository = overrideRepository;
        this.entitlementService = entitlementService;
        this.ttl = Duration.ofSeconds(Math.max(0, ttlSeconds));
        this.featureGatingEnabled = featureGatingEnabled;
    }

    /** Cached effective access for the user (recomputed when the TTL expires or the account type/school changed). */
    @Transactional(readOnly = true, transactionManager = "masterTransactionManager")
    public EffectiveAccess getAccess(PlatformUser user) {
        Objects.requireNonNull(user, "user");
        Instant now = Instant.now();
        CachedAccess cached = cache.get(user.getId());
        if (cached != null && cached.expiresAt.isAfter(now)
                && cached.access.accountType() == user.getPlatformRole()
                && Objects.equals(cached.access.schoolId(), user.getSchoolId())) {
            return cached.access;
        }
        EffectiveAccess access = resolve(user, now);
        if (!ttl.isZero()) {
            cache.put(user.getId(), new CachedAccess(access, now.plus(ttl)));
        }
        return access;
    }

    /** Always recomputes from the database (bypasses the cache). */
    @Transactional(readOnly = true, transactionManager = "masterTransactionManager")
    public EffectiveAccess resolveFresh(PlatformUser user) {
        evict(user.getId());
        return getAccess(user);
    }

    public void evict(UUID userId) {
        if (userId != null) {
            cache.remove(userId);
        }
    }

    public void evictAll() {
        cache.clear();
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void onAccessChanged(AccessChangedEvent event) {
        if (event.userId() == null) {
            log.info("Access cache cleared: {}", event.reason());
            evictAll();
        } else {
            log.debug("Access cache evicted for user [{}]: {}", event.userId(), event.reason());
            evict(event.userId());
        }
    }

    // ------------------------------------------------------------------------------------------

    private EffectiveAccess resolve(PlatformUser user, Instant now) {
        PlatformRole accountType = user.getPlatformRole();
        AccessScope scope = accountType != null ? accountType.getScope() : AccessScope.SCHOOL_PORTAL;
        UUID schoolId = user.getSchoolId();

        Map<UUID, AccessRole> roles = new LinkedHashMap<>();
        if (accountType != null) {
            roleRepository.findGlobalByCode(accountType.name())
                    .filter(AccessRole::isActive)
                    .ifPresent(r -> roles.put(r.getId(), r));
        }

        for (UserRoleAssignment assignment : assignmentRepository.findByUserIdWithRole(user.getId())) {
            if (!assignment.isEffective(now)) {
                continue;
            }
            AccessRole role = assignment.getRole();
            if (!role.isGlobal() && !role.getSchoolId().equals(schoolId)) {
                log.warn("Ignoring role [{}] of school [{}] assigned to user [{}] of school [{}]",
                        role.getCode(), role.getSchoolId(), user.getId(), schoolId);
                continue;
            }
            if (assignment.getSchoolId() != null && !assignment.getSchoolId().equals(schoolId)) {
                log.warn("Ignoring assignment [{}]: school [{}] differs from user's school [{}]",
                        assignment.getId(), assignment.getSchoolId(), schoolId);
                continue;
            }
            if (!isScopeCompatible(scope, role.getScope())) {
                log.warn("Ignoring role [{}] (scope {}) assigned to {} account [{}]",
                        role.getCode(), role.getScope(), scope, user.getId());
                continue;
            }
            roles.put(role.getId(), role);
        }

        boolean superUser = roles.values().stream().anyMatch(AccessRole::isGrantsAllPermissions);

        Map<String, AccessPermission> granted = new LinkedHashMap<>();
        List<AccessPermission> rolePermissions = superUser
                ? permissionRepository.findByActiveTrue()
                : (roles.isEmpty() ? List.of() : permissionRepository.findActiveGrantedByRoles(roles.keySet()));
        for (AccessPermission p : rolePermissions) {
            granted.put(p.getCode(), p);
        }

        Set<String> denied = new HashSet<>();
        for (UserPermissionOverride override : overrideRepository.findByUserIdWithPermission(user.getId())) {
            if (!override.isEffective(now)) {
                continue;
            }
            AccessPermission p = override.getPermission();
            if (override.getEffect() == UserPermissionOverride.Effect.DENY) {
                denied.add(p.getCode());
            } else if (p.isActive()) {
                granted.put(p.getCode(), p);
            }
        }
        denied.forEach(granted::remove);

        Set<String> enabledFeatures = Set.of();
        if (scope.isSchoolBound()) {
            granted.values().removeIf(p -> p.getScope() == AccessPermission.PermissionScope.PLATFORM);
            enabledFeatures = schoolId != null ? entitlementService.getEnabledFeatureCodes(schoolId) : Set.of();
            if (featureGatingEnabled) {
                Set<String> features = enabledFeatures;
                granted.values().removeIf(p -> p.getFeatureCode() != null
                        && !features.contains(p.getFeatureCode().toUpperCase()));
            }
        }

        Set<String> roleCodes = new java.util.LinkedHashSet<>();
        Set<String> globalRoleCodes = new java.util.LinkedHashSet<>();
        for (AccessRole r : roles.values()) {
            roleCodes.add(r.getCode());
            if (r.isGlobal()) {
                globalRoleCodes.add(r.getCode());
            }
        }

        return new EffectiveAccess(user.getId(), accountType, scope, schoolId, roleCodes, globalRoleCodes,
                granted.keySet(), enabledFeatures, superUser, now);
    }

    /** Which role scopes an account of the given scope may hold. */
    public static boolean isScopeCompatible(AccessScope accountScope, AccessScope roleScope) {
        if (accountScope == roleScope) {
            return true;
        }
        return accountScope == AccessScope.SCHOOL && roleScope == AccessScope.SCHOOL_PORTAL;
    }

    private record CachedAccess(EffectiveAccess access, Instant expiresAt) {
    }
}
