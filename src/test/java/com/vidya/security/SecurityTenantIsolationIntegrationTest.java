package com.vidya.security;

import com.vidya.common.exception.TenantAccessDeniedException;
import com.vidya.master.school.entity.School;
import com.vidya.master.school.entity.SchoolBoard;
import com.vidya.master.school.entity.SchoolStatus;
import com.vidya.master.school.entity.SchoolType;
import com.vidya.master.school.repository.SchoolRepository;
import com.vidya.master.tenant.entity.Tenant;
import com.vidya.master.tenant.entity.TenantStatus;
import com.vidya.master.tenant.repository.TenantRepository;
import com.vidya.security.dto.AuthResponse;
import com.vidya.security.dto.LoginRequest;
import com.vidya.security.entity.PlatformRole;
import com.vidya.security.entity.PlatformUser;
import com.vidya.security.entity.RefreshToken;
import com.vidya.security.jwt.JwtTokenProvider;
import com.vidya.security.repository.PlatformUserRepository;
import com.vidya.security.repository.RefreshTokenRepository;
import com.vidya.security.service.AuthServiceImpl;
import com.vidya.security.service.SecurityAuditService;
import com.vidya.security.service.TenantAuthorizationServiceImpl;
import com.vidya.security.service.UserPermissionMatrixService;
import com.vidya.security.user.PlatformUserDetails;
import com.vidya.tenant.context.TenantContext;
import com.vidya.tenant.registry.TenantRegistration;
import com.vidya.tenant.registry.TenantRegistryService;
import com.vidya.tenant.resolver.TenantResolutionFilter;
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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * End-to-end multi-tenant security and isolation integration test.
 *
 * <p>Validates that:
 * 1. Admin of School A can authenticate and obtain valid tokens.
 * 2. Admin A can access School A and Tenant A.
 * 3. Admin A is blocked (403 / Access Denied) when attempting to access School B or Tenant B.
 * 4. TenantContext is isolated and strictly bounded to Tenant A during authorized requests.
 * 5. TenantContext is reliably cleared after execution.
 */
@ExtendWith(MockitoExtension.class)
class SecurityTenantIsolationIntegrationTest {

    @Mock
    private PlatformUserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private SchoolRepository schoolRepository;

    @Mock
    private TenantRegistryService tenantRegistryService;

    @Mock
    private SecurityAuditService securityAuditService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @Mock
    private UserPermissionMatrixService userPermissionMatrixService;

    private PasswordEncoder passwordEncoder;
    private JwtTokenProvider jwtTokenProvider;
    private AuthServiceImpl authService;
    private TenantAuthorizationServiceImpl tenantAuthorizationService;
    private TenantResolutionFilter tenantResolutionFilter;

    // School A entities
    private UUID schoolAId;
    private School schoolA;
    private Tenant tenantA;
    private UUID adminAId;
    private PlatformUser adminA;

    // School B entities
    private UUID schoolBId;
    private School schoolB;
    private Tenant tenantB;
    private UUID adminBId;
    private PlatformUser adminB;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        jwtTokenProvider = new JwtTokenProvider(
                "vidya-dev-secret-key-change-in-production-min-256-bits-length",
                86400,
                604800
        );

        authService = new AuthServiceImpl(
                userRepository,
                refreshTokenRepository,
                passwordEncoder,
                jwtTokenProvider,
                securityAuditService,
                userPermissionMatrixService
        );

        tenantAuthorizationService = new TenantAuthorizationServiceImpl(
                userRepository,
                tenantRepository,
                schoolRepository,
                null,
                null,
                securityAuditService
        );

        tenantResolutionFilter = new TenantResolutionFilter(
                tenantRegistryService,
                tenantAuthorizationService
        );

        // 1. Create School A, Tenant A, School Admin A
        schoolAId = UUID.randomUUID();
        schoolA = new School("SCH-001", "Springfield Academy", "Springfield Academy Legal", "REG-01", SchoolBoard.CBSE, SchoolType.ALL, "admin@springfield.edu", "9876543210", "https://springfield.edu", SchoolStatus.ACTIVE);
        schoolA.setId(schoolAId);

        tenantA = Tenant.create(schoolAId, "TNT-SPRINGFIELD", "pg-node-1.vidya.internal", 5432, "tenant_springfield", "usr_springfield", "pwd_springfield");
        tenantA.setStatus(TenantStatus.ACTIVE);

        adminAId = UUID.randomUUID();
        adminA = new PlatformUser(
                "USR-ADMIN-A",
                "admin@springfield.edu",
                passwordEncoder.encode("SpringfieldPass@2026"),
                "Homer",
                "Simpson",
                "9876543210",
                PlatformRole.SCHOOL_ADMIN,
                schoolAId
        );
        adminA.setId(adminAId);

        // 2. Create School B, Tenant B, School Admin B
        schoolBId = UUID.randomUUID();
        schoolB = new School("SCH-002", "Shelbyville High", "Shelbyville High Legal", "REG-02", SchoolBoard.CBSE, SchoolType.ALL, "admin@shelbyville.edu", "9876543211", "https://shelbyville.edu", SchoolStatus.ACTIVE);
        schoolB.setId(schoolBId);

        tenantB = Tenant.create(schoolBId, "TNT-SHELBYVILLE", "pg-node-2.vidya.internal", 5432, "tenant_shelbyville", "usr_shelbyville", "pwd_shelbyville");
        tenantB.setStatus(TenantStatus.ACTIVE);

        adminBId = UUID.randomUUID();
        adminB = new PlatformUser(
                "USR-ADMIN-B",
                "admin@shelbyville.edu",
                passwordEncoder.encode("ShelbyvillePass@2026"),
                "Ned",
                "Flanders",
                "9876543211",
                PlatformRole.SCHOOL_ADMIN,
                schoolBId
        );
        adminB.setId(adminBId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Complete End-to-End Security and Tenant Isolation Scenario")
    void executeTenantIsolationScenario() throws ServletException, IOException {

        // Step 1: Login as Admin A
        when(userRepository.findByEmailIgnoreCase("admin@springfield.edu")).thenReturn(Optional.of(adminA));
        when(userRepository.save(any(PlatformUser.class))).thenReturn(adminA);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(i -> i.getArgument(0));

        AuthResponse authResponse = authService.login(new LoginRequest("admin@springfield.edu", "SpringfieldPass@2026"));

        assertThat(authResponse).isNotNull();
        assertThat(authResponse.getAccessToken()).isNotEmpty();
        assertThat(authResponse.getUser().getEmail()).isEqualTo("admin@springfield.edu");
        assertThat(authResponse.getUser().getSchoolId()).isEqualTo(schoolAId);

        // Step 2: Establish authenticated context for Admin A
        PlatformUserDetails principalA = new PlatformUserDetails(adminA);
        UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(principalA, null, principalA.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authToken);

        // Step 3: Admin A requests access to School A -> ALLOWED
        when(userRepository.findById(adminAId)).thenReturn(Optional.of(adminA));
        when(schoolRepository.findById(schoolAId)).thenReturn(Optional.of(schoolA));

        boolean canAccessSchoolA = tenantAuthorizationService.canAccessSchool(principalA.getUserId(), schoolAId);
        assertThat(canAccessSchoolA).isTrue();

        // Step 4: Admin A requests access to School B -> DENIED (403)
        when(schoolRepository.findById(schoolBId)).thenReturn(Optional.of(schoolB));

        boolean canAccessSchoolB = tenantAuthorizationService.canAccessSchool(principalA.getUserId(), schoolBId);
        assertThat(canAccessSchoolB).isFalse();

        // Step 5: Admin A attempts to access Tenant A via TenantResolutionFilter -> ALLOWED
        when(request.getRequestURI()).thenReturn("/api/v1/students");
        when(request.getHeader("X-Tenant-ID")).thenReturn("TNT-SPRINGFIELD");

        TenantRegistration regA = new TenantRegistration(
                UUID.randomUUID(), "TNT-SPRINGFIELD", schoolAId, "ACTIVE",
                "pg-node-1.vidya.internal", 5432, "tenant_springfield", "usr_springfield", "pwd_springfield"
        );
        when(tenantRegistryService.findByTenantCode("TNT-SPRINGFIELD")).thenReturn(Optional.of(regA));
        when(tenantRepository.findByTenantCode("TNT-SPRINGFIELD")).thenReturn(Optional.of(tenantA));

        // Intercept filterChain execution to verify TenantContext is set to Tenant A during request
        doAnswer(invocation -> {
            assertThat(TenantContext.getTenantCode()).isEqualTo("TNT-SPRINGFIELD");
            return null;
        }).when(filterChain).doFilter(request, response);

        tenantResolutionFilter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        // Verify TenantContext was cleaned up after request completion
        assertThat(TenantContext.getTenantCode()).isNull();

        // Step 6: Admin A attempts to access Tenant B via TenantResolutionFilter -> DENIED (TenantAccessDeniedException)
        when(request.getHeader("X-Tenant-ID")).thenReturn("TNT-SHELBYVILLE");

        TenantRegistration regB = new TenantRegistration(
                UUID.randomUUID(), "TNT-SHELBYVILLE", schoolBId, "ACTIVE",
                "pg-node-2.vidya.internal", 5432, "tenant_shelbyville", "usr_shelbyville", "pwd_shelbyville"
        );
        when(tenantRegistryService.findByTenantCode("TNT-SHELBYVILLE")).thenReturn(Optional.of(regB));
        when(tenantRepository.findByTenantCode("TNT-SHELBYVILLE")).thenReturn(Optional.of(tenantB));

        assertThatThrownBy(() -> tenantResolutionFilter.doFilter(request, response, filterChain))
                .isInstanceOf(TenantAccessDeniedException.class);

        // Verify TenantContext remains clean after exception
        assertThat(TenantContext.getTenantCode()).isNull();
    }

    @Test
    @DisplayName("User whose token is for Tenant A sending X-Tenant-ID: B must be rejected (403)")
    void userWithTenantAToken_sendingXTenantIdB_isRejectedWith403() {
        PlatformUserDetails principalA = new PlatformUserDetails(adminA);
        UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(principalA, null, principalA.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authToken);

        when(request.getRequestURI()).thenReturn("/api/v1/timetables");
        when(request.getHeader("X-Tenant-ID")).thenReturn("TNT-SHELBYVILLE");

        TenantRegistration regB = new TenantRegistration(
                UUID.randomUUID(), "TNT-SHELBYVILLE", schoolBId, "ACTIVE",
                "pg-node-2.vidya.internal", 5432, "tenant_shelbyville", "usr_shelbyville", "pwd_shelbyville"
        );
        when(tenantRegistryService.findByTenantCode("TNT-SHELBYVILLE")).thenReturn(Optional.of(regB));
        when(userRepository.findById(adminAId)).thenReturn(Optional.of(adminA));
        when(tenantRepository.findByTenantCode("TNT-SHELBYVILLE")).thenReturn(Optional.of(tenantB));

        assertThatThrownBy(() -> tenantResolutionFilter.doFilter(request, response, filterChain))
                .isInstanceOf(TenantAccessDeniedException.class);

        assertThat(TenantContext.getTenantCode()).isNull();
    }

    @Test
    @DisplayName("Token with no tenant/school claim accessing tenant-scoped endpoint must be rejected")
    void tokenWithNoTenantClaim_accessingTenantScopedEndpoint_isRejected() {
        PlatformUser userNoSchool = new PlatformUser(
                "USR-NO-SCH", "noschool@example.com", "hash", "No", "School", null, PlatformRole.SCHOOL_ADMIN, null
        );
        UUID noSchoolUserId = UUID.randomUUID();
        userNoSchool.setId(noSchoolUserId);
        PlatformUserDetails principal = new PlatformUserDetails(userNoSchool);
        UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authToken);

        when(request.getRequestURI()).thenReturn("/api/v1/timetables");
        when(request.getHeader("X-Tenant-ID")).thenReturn("TNT-SPRINGFIELD");

        TenantRegistration regA = new TenantRegistration(
                UUID.randomUUID(), "TNT-SPRINGFIELD", schoolAId, "ACTIVE",
                "pg-node-1.vidya.internal", 5432, "tenant_springfield", "usr_springfield", "pwd_springfield"
        );
        when(tenantRegistryService.findByTenantCode("TNT-SPRINGFIELD")).thenReturn(Optional.of(regA));
        when(userRepository.findById(noSchoolUserId)).thenReturn(Optional.of(userNoSchool));

        assertThatThrownBy(() -> tenantResolutionFilter.doFilter(request, response, filterChain))
                .isInstanceOf(TenantAccessDeniedException.class);

        assertThat(TenantContext.getTenantCode()).isNull();
    }
}
