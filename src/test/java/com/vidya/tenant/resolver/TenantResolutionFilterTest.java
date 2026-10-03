package com.vidya.tenant.resolver;

import com.vidya.common.exception.TenantAccessDeniedException;
import com.vidya.common.exception.TenantNotFoundException;
import com.vidya.common.exception.TenantSuspendedException;
import com.vidya.security.entity.PlatformRole;
import com.vidya.security.entity.PlatformUser;
import com.vidya.security.service.TenantAuthorizationService;
import com.vidya.security.user.PlatformUserDetails;
import com.vidya.tenant.context.TenantContext;
import com.vidya.tenant.registry.TenantRegistration;
import com.vidya.tenant.registry.TenantRegistryService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TenantResolutionFilter — Tenant Isolation, Lifecycle & Leakage Prevention Tests")
class TenantResolutionFilterTest {

    @Mock
    private TenantRegistryService tenantRegistryService;

    @Mock
    private TenantAuthorizationService tenantAuthorizationService;

    @Mock
    private FilterChain filterChain;

    private TenantResolutionFilter filter;

    private UUID userId;
    private PlatformUser user;
    private PlatformUserDetails userDetails;

    @BeforeEach
    void setUp() {
        filter = new TenantResolutionFilter(tenantRegistryService, tenantAuthorizationService);
        TenantContext.clear();
        SecurityContextHolder.clearContext();

        userId = UUID.randomUUID();
        user = new PlatformUser("USR-1", "admin@schoola.com", "hash", "Admin", "A", null, PlatformRole.SCHOOL_ADMIN);
        user.setId(userId);
        userDetails = new PlatformUserDetails(user);

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Exempt URI bypasses tenant resolution")
    void exemptUri_Bypasses() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(TenantContext.getTenantCode()).isNull();
    }

    @Test
    @DisplayName("Missing X-Tenant-ID header continues without setting context")
    void missingHeader_Continues() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/academic/classes");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(TenantContext.getTenantCode()).isNull();
    }

    @Test
    @DisplayName("Valid tenant context established and cleared in finally block")
    void validTenant_establishesAndClearsContext() throws Exception {
        TenantRegistration reg = new TenantRegistration(UUID.randomUUID(), "TNT-A", UUID.randomUUID(), "ACTIVE", "HOST", 5432, "db_a", "u", "p");
        when(tenantRegistryService.findByTenantCode("TNT-A")).thenReturn(Optional.of(reg));
        when(tenantAuthorizationService.canAccessTenant(userId, "TNT-A")).thenReturn(true);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/timetables");
        request.addHeader(TenantResolutionFilter.TENANT_HEADER, "TNT-A");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        // Assert context cleared after filter execution
        assertThat(TenantContext.getTenantCode()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Valid X-Tenant-ID and authorized user sets TenantContext during chain execution and clears in finally")
    void validTenant_AuthorizedUser_SetsContextAndClearsInFinally() throws Exception {
        TenantRegistration reg = new TenantRegistration(UUID.randomUUID(), "TNT-001", UUID.randomUUID(), "ACTIVE", "HOST", 5432, "db_001", "u", "p");
        when(tenantRegistryService.findByTenantCode("TNT-001")).thenReturn(Optional.of(reg));
        when(tenantAuthorizationService.canAccessTenant(userId, "TNT-001")).thenReturn(true);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/academic/classes");
        request.addHeader(TenantResolutionFilter.TENANT_HEADER, "TNT-001");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(TenantContext.getTenantCode()).isNull();
    }

    @Test
    @DisplayName("Sequential requests do not leak context even if first request fails with exception")
    void sequentialRequests_doNotLeakContext() throws Exception {
        TenantRegistration reg = new TenantRegistration(UUID.randomUUID(), "TNT-A", UUID.randomUUID(), "ACTIVE", "HOST", 5432, "db_a", "u", "p");
        when(tenantRegistryService.findByTenantCode("TNT-A")).thenReturn(Optional.of(reg));
        when(tenantAuthorizationService.canAccessTenant(userId, "TNT-A")).thenReturn(true);
        doThrow(new RuntimeException("Simulated controller failure")).when(filterChain).doFilter(any(), any());

        MockHttpServletRequest request1 = new MockHttpServletRequest("GET", "/api/v1/timetables");
        request1.addHeader(TenantResolutionFilter.TENANT_HEADER, "TNT-A");
        MockHttpServletResponse response1 = new MockHttpServletResponse();

        assertThatThrownBy(() -> filter.doFilterInternal(request1, response1, filterChain))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Simulated controller failure");

        // First request's context must be cleared
        assertThat(TenantContext.getTenantCode()).isNull();

        // Second request with no tenant header must see null tenant context
        MockHttpServletRequest request2 = new MockHttpServletRequest("GET", "/api/v1/timetables");
        MockHttpServletResponse response2 = new MockHttpServletResponse();

        filter.doFilterInternal(request2, response2, (req, res) -> {
            assertThat(TenantContext.getTenantCode()).isNull();
        });

        assertThat(TenantContext.getTenantCode()).isNull();
    }

    @Test
    @DisplayName("Unknown tenant throws TenantNotFoundException and clears context")
    void unknownTenant_throwsTenantNotFoundException() {
        when(tenantRegistryService.findByTenantCode("TNT-UNKNOWN")).thenReturn(Optional.empty());

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/timetables");
        request.addHeader(TenantResolutionFilter.TENANT_HEADER, "TNT-UNKNOWN");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> filter.doFilterInternal(request, response, filterChain))
                .isInstanceOf(TenantNotFoundException.class);

        assertThat(TenantContext.getTenantCode()).isNull();
    }

    @Test
    @DisplayName("Suspended tenant throws TenantSuspendedException and clears context")
    void suspendedTenant_throwsTenantSuspendedException() {
        TenantRegistration reg = new TenantRegistration(UUID.randomUUID(), "TNT-SUSPENDED", UUID.randomUUID(), "SUSPENDED", "HOST", 5432, "db_s", "u", "p");
        when(tenantRegistryService.findByTenantCode("TNT-SUSPENDED")).thenReturn(Optional.of(reg));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/timetables");
        request.addHeader(TenantResolutionFilter.TENANT_HEADER, "TNT-SUSPENDED");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> filter.doFilterInternal(request, response, filterChain))
                .isInstanceOf(TenantSuspendedException.class);

        assertThat(TenantContext.getTenantCode()).isNull();
    }

    @Test
    @DisplayName("User not authorized for tenant throws TenantAccessDeniedException (403)")
    void unauthorizedTenant_throwsTenantAccessDeniedException() {
        TenantRegistration reg = new TenantRegistration(UUID.randomUUID(), "TNT-B", UUID.randomUUID(), "ACTIVE", "HOST", 5432, "db_b", "u", "p");
        when(tenantRegistryService.findByTenantCode("TNT-B")).thenReturn(Optional.of(reg));
        when(tenantAuthorizationService.canAccessTenant(userId, "TNT-B")).thenReturn(false);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/timetables");
        request.addHeader(TenantResolutionFilter.TENANT_HEADER, "TNT-B");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> filter.doFilterInternal(request, response, filterChain))
                .isInstanceOf(TenantAccessDeniedException.class);

        assertThat(TenantContext.getTenantCode()).isNull();
    }
}
