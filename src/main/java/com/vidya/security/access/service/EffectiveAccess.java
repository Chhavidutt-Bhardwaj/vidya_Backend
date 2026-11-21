package com.vidya.security.access.service;

import com.vidya.security.access.DataDomain;
import com.vidya.security.access.DataScope;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeSet;

import com.vidya.security.access.AccessScope;
import com.vidya.security.entity.PlatformRole;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Resolved, immutable view of what a user may do right now: roles, permission codes after
 * overrides and feature gating, and the school's enabled features.
 *
 * @param roles           codes of every role held (global and school custom), for display / UI checks
 * @param globalRoles     codes of the global roles held; only these become {@code ROLE_*} authorities, so a
 *                        school custom role can never satisfy a {@code hasRole(...)} check
 * @param enabledFeatures features enabled for the user's school; empty for users without a school
 * @param superUser       true when a held role has {@code grants_all_permissions}
 */
public record EffectiveAccess(
        UUID userId,
        PlatformRole accountType,
        AccessScope scope,
        UUID schoolId,
        Set<String> roles,
        Set<String> globalRoles,
        Set<String> permissions,
        Set<String> enabledFeatures,
        boolean superUser,
        Instant resolvedAt
) {

    public EffectiveAccess {
        roles = Collections.unmodifiableSet(new LinkedHashSet<>(roles));
        globalRoles = Collections.unmodifiableSet(new LinkedHashSet<>(globalRoles));
        permissions = Collections.unmodifiableSet(new LinkedHashSet<>(permissions));
        enabledFeatures = Collections.unmodifiableSet(new LinkedHashSet<>(enabledFeatures));
    }

    public boolean hasPermission(String code) {
        return code != null && permissions.contains(code);
    }

    public boolean hasRole(String roleCode) {
        return roleCode != null && roles.contains(roleCode);
    }

    /** Row-level scope for one area: ALL for non-school accounts or holders of {@code <DOMAIN>_SCOPE_ALL}, else OWN. */
    public DataScope dataScope(DataDomain domain) {
        if (scope == null || !scope.isSchoolBound() || superUser || hasPermission(domain.scopeAllPermission())) {
            return DataScope.ALL;
        }
        return DataScope.OWN;
    }

    /** Scope of every domain, for the JWT snapshot and {@code /auth/me}. */
    public Map<DataDomain, DataScope> dataScopes() {
        Map<DataDomain, DataScope> result = new EnumMap<>(DataDomain.class);
        for (DataDomain d : DataDomain.values()) {
            result.put(d, dataScope(d));
        }
        return result;
    }

    /**
     * Short fingerprint of everything the frontend derives from this access (permissions, roles,
     * features, data scopes). It goes into the JWT ({@code av}) and the {@code X-Access-Version}
     * response header; when they differ the client refreshes its token.
     */
    public String version() {
        StringBuilder sb = new StringBuilder();
        sb.append(accountType).append('|').append(schoolId).append('|').append(superUser).append('|');
        new TreeSet<>(roles).forEach(r -> sb.append(r).append(','));
        sb.append('|');
        new TreeSet<>(permissions).forEach(p -> sb.append(p).append(','));
        sb.append('|');
        new TreeSet<>(enabledFeatures).forEach(f -> sb.append(f).append(','));
        sb.append('|');
        dataScopes().forEach((d, s) -> sb.append(d).append('=').append(s).append(','));
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /**
     * Spring Security authorities: {@code ROLE_<role>} per held global role, and each permission both
     * plain and as {@code PERMISSION_<code>}. Super users also get {@code ROLE_*} for every
     * account type, so the remaining {@code hasRole(...)} checks keep working.
     */
    public List<GrantedAuthority> toAuthorities() {
        Set<String> names = new LinkedHashSet<>();
        for (String role : globalRoles) {
            names.add("ROLE_" + role);
        }
        if (superUser) {
            for (PlatformRole r : PlatformRole.values()) {
                names.add("ROLE_" + r.name());
            }
        }
        for (String p : permissions) {
            names.add(p);
            names.add("PERMISSION_" + p);
        }
        List<GrantedAuthority> authorities = new ArrayList<>(names.size());
        for (String n : names) {
            authorities.add(new SimpleGrantedAuthority(n));
        }
        return Collections.unmodifiableList(authorities);
    }
}
