package com.vidya.tenant.resolver;

import com.vidya.common.correlation.MdcKeys;
import com.vidya.common.exception.TenantAccessDeniedException;
import com.vidya.common.exception.TenantNotFoundException;
import com.vidya.common.exception.TenantSuspendedException;
import com.vidya.security.service.TenantAuthorizationService;
import com.vidya.security.user.PlatformUserDetails;
import com.vidya.tenant.context.TenantContext;
import com.vidya.tenant.registry.TenantRegistration;
import com.vidya.tenant.registry.TenantRegistryService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Resolves and validates the current request's tenant context.
 *
 * <p><strong>Security contract:</strong> The {@code X-Tenant-ID} header is treated
 * as an UNTRUSTED hint, not as authorization. The actual validation is:
 *
 * <ol>
 *   <li>Extract {@code X-Tenant-ID} from the request header (untrusted)</li>
 *   <li>Get the authenticated user from the Spring SecurityContext</li>
 *   <li>Verify the tenant exists and is ACTIVE in the registry</li>
 *   <li>Verify the authenticated user has permission to access that tenant</li>
 *   <li>Set {@link TenantContext#setTenantCode(String)}</li>
 *   <li>Put tenantId into MDC for structured logging</li>
 *   <li>Clear everything in a {@code finally} block</li>
 * </ol>
 */
@Component
@Order(3)
public class TenantResolutionFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TenantResolutionFilter.class);

    public static final String TENANT_HEADER = "X-Tenant-ID";

    /**
     * URL prefixes that do NOT require tenant resolution.
     * Requests to these paths are master-DB-only operations.
     */
    private static final Set<String> TENANT_EXEMPT_PREFIXES = Set.of(
            "/api/v1/auth/",
            "/api/v1/onboarding",
            "/api/v1/subscription-plans",
            "/api/v1/communities",
            "/api/v1/schools",
            "/api/v1/admin/tenants",
            "/api/v1/webhooks",
            "/actuator",
            "/v3/api-docs",
            "/swagger-ui"
    );

    private final TenantRegistryService tenantRegistryService;
    private final TenantAuthorizationService tenantAuthorizationService;

    public TenantResolutionFilter(TenantRegistryService tenantRegistryService,
                                  TenantAuthorizationService tenantAuthorizationService) {
        this.tenantRegistryService = tenantRegistryService;
        this.tenantAuthorizationService = tenantAuthorizationService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        try {
            if (isTenantExemptPath(request.getRequestURI())) {
                filterChain.doFilter(request, response);
                return;
            }

            String tenantCode = request.getHeader(TENANT_HEADER);

            if (tenantCode == null || tenantCode.isBlank()) {
                filterChain.doFilter(request, response);
                return;
            }

            // ── Authentication check ──────────────────────────────────────────
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()
                    || !(authentication.getPrincipal() instanceof PlatformUserDetails userDetails)) {
                // Unauthenticated request with a tenant header — cannot establish tenant context
                filterChain.doFilter(request, response);
                return;
            }

            // ── Tenant registry lookup ────────────────────────────────────────
            TenantRegistration registration = tenantRegistryService
                    .findByTenantCode(tenantCode)
                    .orElseThrow(() -> new TenantNotFoundException(tenantCode));

            // ── Status check ──────────────────────────────────────────────────
            if (registration.isSuspended()) {
                throw new TenantSuspendedException(tenantCode);
            }

            if (!registration.isAccessible()) {
                throw new TenantNotFoundException("Tenant [" + tenantCode + "] is not active");
            }

            // ── Access control validation via TenantAuthorizationService ───────
            boolean hasAccess = tenantAuthorizationService.canAccessTenant(userDetails.getUserId(), tenantCode);

            if (!hasAccess) {
                log.warn("[{}] User [{}] with role [{}] attempted unauthorized access to tenant [{}]",
                        MDC.get(MdcKeys.CORRELATION_ID),
                        userDetails.getUserId(),
                        userDetails.getPlatformRole(),
                        tenantCode);
                throw new TenantAccessDeniedException();
            }

            // ── Set context ───────────────────────────────────────────────────
            TenantContext.setTenantCode(tenantCode);
            MDC.put(MdcKeys.TENANT_ID, tenantCode);
            if (registration.schoolId() != null) {
                MDC.put(MdcKeys.SCHOOL_ID, registration.schoolId().toString());
            }

            log.debug("Tenant context established: [{}]", tenantCode);

            filterChain.doFilter(request, response);

        } finally {
            // CRITICAL: always clear tenant context regardless of outcome
            TenantContext.clear();
            MDC.remove(MdcKeys.TENANT_ID);
            MDC.remove(MdcKeys.SCHOOL_ID);
        }
    }

    private boolean isTenantExemptPath(String uri) {
        for (String prefix : TENANT_EXEMPT_PREFIXES) {
            if (uri.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
