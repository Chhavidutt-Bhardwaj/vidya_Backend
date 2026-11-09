package com.vidya.security.user;

import com.vidya.security.access.AccessScope;
import com.vidya.security.access.service.EffectiveAccess;
import com.vidya.security.entity.PlatformRole;
import com.vidya.security.entity.PlatformUser;
import com.vidya.security.entity.UserStatus;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Spring Security principal for authenticated platform users.
 *
 * <p>Authorities come from {@link EffectiveAccess}, which is resolved from the DB-driven role and
 * permission tables. The principal also carries the id of the server-side session the request
 * was authenticated with.
 */
@Getter
public class PlatformUserDetails implements UserDetails {

    private final UUID userId;
    private final String userCode;
    private final String email;
    private final String passwordHash;
    private final PlatformRole platformRole;
    private final UUID schoolId;
    private final String tenantCode;
    private final UserStatus status;
    private final boolean accountLocked;
    private final UUID sessionId;
    private final EffectiveAccess effectiveAccess;
    private final Collection<? extends GrantedAuthority> authorities;
    private final Set<String> authorityNames;

    /** Principal without resolved permissions: carries only {@code ROLE_<accountType>} (tests and internal use). */
    public PlatformUserDetails(PlatformUser user) {
        this(user, null);
    }

    public PlatformUserDetails(PlatformUser user, String tenantCode) {
        this(user, tenantCode, null, null);
    }

    /** Main constructor used by request authentication. */
    public PlatformUserDetails(PlatformUser user, String tenantCode, EffectiveAccess access, UUID sessionId) {
        this.userId = user.getId();
        this.userCode = user.getUserCode();
        this.email = user.getEmail();
        this.passwordHash = user.getPasswordHash();
        this.platformRole = user.getPlatformRole();
        this.schoolId = user.getSchoolId();
        this.tenantCode = tenantCode;
        this.status = user.getStatus();
        this.accountLocked = user.isAccountLocked();
        this.sessionId = sessionId;
        this.effectiveAccess = access;

        List<GrantedAuthority> auths;
        if (access != null) {
            auths = access.toAuthorities();
        } else {
            auths = new ArrayList<>();
            if (user.getPlatformRole() != null) {
                auths.add(new SimpleGrantedAuthority("ROLE_" + user.getPlatformRole().name()));
            }
        }
        this.authorities = Collections.unmodifiableList(auths);
        this.authorityNames = names(this.authorities);
    }

    public PlatformUserDetails(UUID userId,
                               String email,
                               String passwordHash,
                               String platformRole,
                               Collection<? extends GrantedAuthority> authorities) {
        this(userId, email, passwordHash, platformRole, null, null, authorities);
    }

    /** Explicit-authorities constructor (tests, system actors). */
    public PlatformUserDetails(UUID userId,
                               String email,
                               String passwordHash,
                               String platformRole,
                               UUID schoolId,
                               String tenantCode,
                               Collection<? extends GrantedAuthority> authorities) {
        this.userId = userId;
        this.userCode = null;
        this.email = email;
        this.passwordHash = passwordHash;
        this.platformRole = platformRole != null ? PlatformRole.valueOf(platformRole) : null;
        this.schoolId = schoolId;
        this.tenantCode = tenantCode;
        this.status = UserStatus.ACTIVE;
        this.accountLocked = false;
        this.sessionId = null;
        this.effectiveAccess = null;

        List<GrantedAuthority> resolved = new ArrayList<>();
        if (authorities != null) {
            resolved.addAll(authorities);
        }
        if (this.platformRole == PlatformRole.PLATFORM_SUPER_ADMIN) {
            for (PlatformRole r : PlatformRole.values()) {
                SimpleGrantedAuthority roleAuth = new SimpleGrantedAuthority("ROLE_" + r.name());
                if (!resolved.contains(roleAuth)) {
                    resolved.add(roleAuth);
                }
            }
        }
        this.authorities = Collections.unmodifiableList(resolved);
        this.authorityNames = names(this.authorities);
    }

    /** True when the user currently holds the permission (DB-resolved, after overrides and feature gating). */
    public boolean hasPermission(String permissionCode) {
        if (permissionCode == null) {
            return false;
        }
        if (effectiveAccess != null && effectiveAccess.superUser()) {
            return true;
        }
        return authorityNames.contains(permissionCode);
    }

    public boolean hasRole(PlatformRole role) {
        if (this.platformRole == PlatformRole.PLATFORM_SUPER_ADMIN) {
            return true;
        }
        return this.platformRole == role || authorityNames.contains("ROLE_" + role.name());
    }

    public AccessScope getScope() {
        return platformRole != null ? platformRole.getScope() : AccessScope.SCHOOL_PORTAL;
    }

    public boolean isPlatformAdmin() {
        return platformRole != null && platformRole.isPlatformAdmin();
    }

    public String getPlatformRoleName() {
        return platformRole != null ? platformRole.name() : null;
    }

    private static Set<String> names(Collection<? extends GrantedAuthority> authorities) {
        return authorities.stream().map(GrantedAuthority::getAuthority).collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return !accountLocked && status != UserStatus.SUSPENDED;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return status == UserStatus.ACTIVE;
    }
}
