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
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantResolutionFilterTest {

    @Mock
    private TenantRegistryService tenantRegistryService;

    @Mock
    private TenantAuthorizationService tenantAuthorizationService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private TenantResolutionFilter filter;

    private UUID userId;
    private UUID schoolId;
    private PlatformUserDetails userDetails;

    @BeforeEach
    void setUp() {
        filter = new TenantResolutionFilter(tenantRegistryService, tenantAuthorizationService);
        TenantContext.clear();

        userId = UUID.randomUUID();
        schoolId = UUID.randomUUID();
        PlatformUser user = new PlatformUser("USR-01", "admin@school.com", "hash", "Admin", "User", null, PlatformRole.SCHOOL_ADMIN, schoolId);
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
    void exemptUri_Bypasses() throws ServletException, IOException {
        when(request.getRequestURI()).thenReturn("/api/v1/auth/login");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(TenantContext.getTenantCode()).isNull();
    }

    @Test
    @DisplayName("Missing X-Tenant-ID header continues without setting context")
    void missingHeader_Continues() throws ServletException, IOException {
        when(request.getRequestURI()).thenReturn("/api/v1/academic/classes");
        when(request.getHeader("X-Tenant-ID")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(TenantContext.getTenantCode()).isNull();
    }

    @Test
    @DisplayName("Valid X-Tenant-ID and authorized user sets TenantContext during chain execution and clears in finally")
    void validTenant_AuthorizedUser_SetsContextAndClearsInFinally() throws ServletException, IOException {
        when(request.getRequestURI()).thenReturn("/api/v1/academic/classes");
        when(request.getHeader("X-Tenant-ID")).thenReturn("TNT-001");

        TenantRegistration registration = new TenantRegistration(
                UUID.randomUUID(), "TNT-001", schoolId, "ACTIVE", "localhost", 5432, "db_001", "usr", "pwd"
        );
        when(tenantRegistryService.findByTenantCode("TNT-001")).thenReturn(Optional.of(registration));
        when(tenantAuthorizationService.canAccessTenant(userId, "TNT-001")).thenReturn(true);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        // After request execution, TenantContext MUST be cleaned up
        assertThat(TenantContext.getTenantCode()).isNull();
    }

    @Test
    @DisplayName("Non-existent tenant throws TenantNotFoundException and clears TenantContext")
    void nonExistentTenant_ThrowsNotFound() {
        when(request.getRequestURI()).thenReturn("/api/v1/academic/classes");
        when(request.getHeader("X-Tenant-ID")).thenReturn("TNT-UNKNOWN");

        when(tenantRegistryService.findByTenantCode("TNT-UNKNOWN")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> filter.doFilterInternal(request, response, filterChain))
                .isInstanceOf(TenantNotFoundException.class);

        assertThat(TenantContext.getTenantCode()).isNull();
    }

    @Test
    @DisplayName("Suspended tenant throws TenantSuspendedException and clears TenantContext")
    void suspendedTenant_ThrowsSuspendedException() {
        when(request.getRequestURI()).thenReturn("/api/v1/academic/classes");
        when(request.getHeader("X-Tenant-ID")).thenReturn("TNT-SUSPENDED");

        TenantRegistration registration = new TenantRegistration(
                UUID.randomUUID(), "TNT-SUSPENDED", schoolId, "SUSPENDED", "localhost", 5432, "db_001", "usr", "pwd"
        );
        when(tenantRegistryService.findByTenantCode("TNT-SUSPENDED")).thenReturn(Optional.of(registration));

        assertThatThrownBy(() -> filter.doFilterInternal(request, response, filterChain))
                .isInstanceOf(TenantSuspendedException.class);

        assertThat(TenantContext.getTenantCode()).isNull();
    }

    @Test
    @DisplayName("Unauthorized user accessing tenant throws TenantAccessDeniedException and clears TenantContext")
    void unauthorizedUser_ThrowsAccessDenied() {
        when(request.getRequestURI()).thenReturn("/api/v1/academic/classes");
        when(request.getHeader("X-Tenant-ID")).thenReturn("TNT-OTHER");

        TenantRegistration registration = new TenantRegistration(
                UUID.randomUUID(), "TNT-OTHER", UUID.randomUUID(), "ACTIVE", "localhost", 5432, "db_other", "usr", "pwd"
        );
        when(tenantRegistryService.findByTenantCode("TNT-OTHER")).thenReturn(Optional.of(registration));
        when(tenantAuthorizationService.canAccessTenant(userId, "TNT-OTHER")).thenReturn(false);

        assertThatThrownBy(() -> filter.doFilterInternal(request, response, filterChain))
                .isInstanceOf(TenantAccessDeniedException.class);

        assertThat(TenantContext.getTenantCode()).isNull();
    }
}
