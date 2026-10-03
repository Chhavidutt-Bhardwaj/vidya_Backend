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
    private final UserStatus status;
    private final boolean accountLocked;
    private final Collection<? extends GrantedAuthority> authorities;

    public PlatformUserDetails(PlatformUser user) {
        this.userId = user.getId();
        this.userCode = user.getUserCode();
        this.email = user.getEmail();
        this.passwordHash = user.getPasswordHash();
        this.platformRole = user.getPlatformRole();
        this.schoolId = user.getSchoolId();
        this.status = user.getStatus();
        this.accountLocked = user.isAccountLocked();

        List<GrantedAuthority> auths = new ArrayList<>();
        // Role authority
        auths.add(new SimpleGrantedAuthority("ROLE_" + user.getPlatformRole().name()));

        // Permissions authorities
        if (user.getPlatformRole() != null && user.getPlatformRole().getPermissions() != null) {
            for (PlatformPermission permission : user.getPlatformRole().getPermissions()) {
                auths.add(new SimpleGrantedAuthority(permission.name()));
                auths.add(new SimpleGrantedAuthority("PERMISSION_" + permission.name()));
            }
        }
        this.authorities = Collections.unmodifiableList(auths);
    }

    public PlatformUserDetails(UUID userId,
                               String email,
                               String passwordHash,
                               String platformRole,
                               Collection<? extends GrantedAuthority> authorities) {
        this.userId = userId;
        this.userCode = null;
        this.email = email;
        this.passwordHash = passwordHash;
        this.platformRole = platformRole != null ? PlatformRole.valueOf(platformRole) : null;
        this.schoolId = null;
        this.status = UserStatus.ACTIVE;
        this.accountLocked = false;
        this.authorities = authorities != null ? authorities : List.of();
    }

    public boolean hasPermission(PlatformPermission permission) {
        return platformRole != null && platformRole.hasPermission(permission);
    }

    public boolean hasRole(PlatformRole role) {
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
