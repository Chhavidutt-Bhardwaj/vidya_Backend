package com.vidya.security.service;

import com.vidya.security.access.service.EffectiveAccessService;
import com.vidya.security.entity.PlatformUser;
import com.vidya.security.repository.PlatformUserRepository;
import com.vidya.security.user.PlatformUserDetails;
import com.vidya.master.tenant.entity.Tenant;
import com.vidya.master.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Loads the authenticated principal: the user row plus DB-resolved effective access
 * (roles, permissions, feature gating).
 */
@Service
@RequiredArgsConstructor
public class PlatformUserDetailsService implements UserDetailsService {

    private final PlatformUserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final EffectiveAccessService effectiveAccessService;

    @Override
    @Transactional(readOnly = true, transactionManager = "masterTransactionManager")
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return loadPrincipal(username, null);
    }

    /** Principal for a request authenticated by an access token bound to {@code sessionId}. */
    @Transactional(readOnly = true, transactionManager = "masterTransactionManager")
    public PlatformUserDetails loadPrincipal(String email, UUID sessionId) {
        PlatformUser user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));

        String tenantCode = null;
        if (user.getSchoolId() != null && tenantRepository != null) {
            tenantCode = tenantRepository.findBySchoolId(user.getSchoolId())
                    .map(Tenant::getTenantCode)
                    .orElse(null);
        }

        return new PlatformUserDetails(user, tenantCode, effectiveAccessService.getAccess(user), sessionId);
    }
}
