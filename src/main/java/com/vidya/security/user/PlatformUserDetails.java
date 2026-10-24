package com.vidya.security.user;

import com.vidya.security.entity.PlatformPermission;
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
import java.util.UUID;

/**
 * Spring Security UserDetails implementation for authenticated platform users.
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
    private final Collection<? extends GrantedAuthority> authorities;

    public PlatformUserDetails(PlatformUser user) {
        this(user, null);
    }

    public PlatformUserDetails(PlatformUser user, String tenantCode) {
        this.userId = user.getId();
        this.userCode = user.getUserCode();
        this.email = user.getEmail();
        this.passwordHash = user.getPasswordHash();
        this.platformRole = user.getPlatformRole();
        this.schoolId = user.getSchoolId();
        this.tenantCode = tenantCode;
        this.status = user.getStatus();
        this.accountLocked = user.isAccountLocked();

        List<GrantedAuthority> auths = new ArrayList<>();
        // Role authority
        if (user.getPlatformRole() != null) {
            auths.add(new SimpleGrantedAuthority("ROLE_" + user.getPlatformRole().name()));
            if (user.getPlatformRole().getPermissions() != null) {
                for (PlatformPermission permission : user.getPlatformRole().getPermissions()) {
                    auths.add(new SimpleGrantedAuthority(permission.name()));
                    auths.add(new SimpleGrantedAuthority("PERMISSION_" + permission.name()));
                }
            }

            // SuperUser automatically inherits ALL platform roles (current and future)
            if (user.getPlatformRole() == PlatformRole.PLATFORM_SUPER_ADMIN) {
                for (PlatformRole r : PlatformRole.values()) {
                    if (r != PlatformRole.PLATFORM_SUPER_ADMIN) {
                        auths.add(new SimpleGrantedAuthority("ROLE_" + r.name()));
                    }
                }
            }
        }
        this.authorities = Collections.unmodifiableList(auths);
    }

    public PlatformUserDetails(UUID userId,
                               String email,
                               String passwordHash,
                               String platformRole,
                               Collection<? extends GrantedAuthority> authorities) {
        this(userId, email, passwordHash, platformRole, null, null, authorities);
    }

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

        List<GrantedAuthority> resolvedAuths = new ArrayList<>();
        if (authorities != null) {
            resolvedAuths.addAll(authorities);
        }
        if (this.platformRole == PlatformRole.PLATFORM_SUPER_ADMIN) {
            for (PlatformRole r : PlatformRole.values()) {
                SimpleGrantedAuthority roleAuth = new SimpleGrantedAuthority("ROLE_" + r.name());
                if (!resolvedAuths.contains(roleAuth)) {
                    resolvedAuths.add(roleAuth);
                }
            }
            for (PlatformPermission p : PlatformPermission.values()) {
                SimpleGrantedAuthority permAuth = new SimpleGrantedAuthority(p.name());
                SimpleGrantedAuthority permPrefixAuth = new SimpleGrantedAuthority("PERMISSION_" + p.name());
                if (!resolvedAuths.contains(permAuth)) {
                    resolvedAuths.add(permAuth);
                }
                if (!resolvedAuths.contains(permPrefixAuth)) {
                    resolvedAuths.add(permPrefixAuth);
                }
            }
        }
        this.authorities = Collections.unmodifiableList(resolvedAuths);
    }

    public boolean hasPermission(PlatformPermission permission) {
        if (this.platformRole == PlatformRole.PLATFORM_SUPER_ADMIN) {
            return true;
        }
        return platformRole != null && platformRole.hasPermission(permission);
    }

    public boolean hasRole(PlatformRole role) {
        if (this.platformRole == PlatformRole.PLATFORM_SUPER_ADMIN) {
            return true;
        }
        return this.platformRole == role;
    }

    public boolean isPlatformAdmin() {
        return platformRole == PlatformRole.PLATFORM_SUPER_ADMIN || platformRole == PlatformRole.PLATFORM_ADMIN;
    }

    public String getPlatformRoleName() {
        return platformRole != null ? platformRole.name() : null;
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
