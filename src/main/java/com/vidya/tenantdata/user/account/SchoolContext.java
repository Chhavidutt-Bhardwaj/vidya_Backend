package com.vidya.tenantdata.user.account;

import com.vidya.common.exception.BusinessException;
import com.vidya.security.user.PlatformUserDetails;
import com.vidya.tenant.context.TenantContext;
import com.vidya.tenant.registry.TenantRegistration;
import com.vidya.tenant.registry.TenantRegistryService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Current school, signed-in user and tenant ↔ login links, for services that touch both databases. */
@Component
@RequiredArgsConstructor
public class SchoolContext {

    private final TenantRegistryService tenantRegistryService;

    @PersistenceContext(unitName = "tenant")
    private EntityManager em;

    /** School that owns the current tenant. */
    public UUID schoolId() {
        String code = TenantContext.getTenantCode();
        return tenantRegistryService.findByTenantCode(code).map(TenantRegistration::schoolId)
                .orElseThrow(() -> new BusinessException("No school is registered for tenant " + code));
    }

    public UUID actorId() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a != null && a.getPrincipal() instanceof PlatformUserDetails p ? p.getUserId() : null;
    }

    /** Login ids already linked to any person of this school (staff, students, guardians). */
    @SuppressWarnings("unchecked")
    public Set<UUID> linkedMasterUserIds() {
        return new HashSet<>((List<UUID>) em.createNativeQuery("SELECT master_user_id FROM tenant_user WHERE master_user_id IS NOT NULL")
                .getResultList());
    }

    /** For permissions that depend on the request body (e.g. a login is created only for some rows). */
    public void requireAuthority(String authority, String forWhat) {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || a.getAuthorities().stream().noneMatch(g -> authority.equals(g.getAuthority()))) {
            throw new AccessDeniedException(authority + " is required to " + forWhat);
        }
    }
}
