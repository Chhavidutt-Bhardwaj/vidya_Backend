package com.vidya.security;

import com.vidya.master.community.entity.Community;
import com.vidya.master.community.entity.CommunityMember;
import com.vidya.master.community.entity.CommunityMemberRole;
import com.vidya.master.community.repository.CommunityMemberRepository;
import com.vidya.master.onboarding.entity.OnboardingSource;
import com.vidya.master.onboarding.entity.SchoolOnboarding;
import com.vidya.master.onboarding.repository.SchoolOnboardingRepository;
import com.vidya.master.school.entity.School;
import com.vidya.master.school.entity.SchoolBoard;
import com.vidya.master.school.entity.SchoolStatus;
import com.vidya.master.school.entity.SchoolType;
import com.vidya.master.school.repository.SchoolRepository;
import com.vidya.master.tenant.entity.Tenant;
import com.vidya.master.tenant.entity.TenantStatus;
import com.vidya.master.tenant.repository.TenantRepository;
import com.vidya.security.entity.PlatformPermission;
import com.vidya.security.entity.PlatformRole;
import com.vidya.security.entity.PlatformUser;
import com.vidya.security.jwt.JwtTokenProvider;
import com.vidya.security.repository.PlatformUserRepository;
import com.vidya.security.service.SecurityAuditService;
import com.vidya.security.service.TenantAuthorizationServiceImpl;
import com.vidya.security.user.PlatformUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecurityMatrixTest {

    @Mock
    private PlatformUserRepository userRepository;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private SchoolRepository schoolRepository;

    @Mock
    private SchoolOnboardingRepository onboardingRepository;

    @Mock
    private CommunityMemberRepository communityMemberRepository;

    @Mock
    private SecurityAuditService securityAuditService;

    private JwtTokenProvider jwtTokenProvider;
    private TenantAuthorizationServiceImpl tenantAuthorizationService;

    private PlatformUser platformSuperAdmin;
    private PlatformUser platformAdmin;
    private PlatformUser communityAdmin;
    private PlatformUser communityManager;
    private PlatformUser fieldAgent;
    private PlatformUser schoolOwner;
    private PlatformUser schoolAdmin;

    private School schoolA;
    private School schoolB;
    private Tenant tenantA;
    private Tenant tenantB;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(
                "vidya-dev-secret-key-change-in-production-min-256-bits-length",
                86400,
                604800
        );

        tenantAuthorizationService = new TenantAuthorizationServiceImpl(
                userRepository,
                tenantRepository,
                schoolRepository,
                onboardingRepository,
                communityMemberRepository,
                securityAuditService
        );

        UUID schoolAId = UUID.randomUUID();
        schoolA = new School("SCH-001", "School A", "School A Legal", "REG-01", SchoolBoard.CBSE, SchoolType.ALL, "a@sch.com", "9999999991", "https://a.sch.com", SchoolStatus.ACTIVE);
        schoolA.setId(schoolAId);

        tenantA = Tenant.create(schoolAId, "TNT-001", "localhost", 5432, "tenant_a_db", "user_ref", "pass_ref");
        tenantA.setStatus(TenantStatus.ACTIVE);

        UUID schoolBId = UUID.randomUUID();
        schoolB = new School("SCH-002", "School B", "School B Legal", "REG-02", SchoolBoard.CBSE, SchoolType.ALL, "b@sch.com", "9999999992", "https://b.sch.com", SchoolStatus.ACTIVE);
        schoolB.setId(schoolBId);

        tenantB = Tenant.create(schoolBId, "TNT-002", "localhost", 5432, "tenant_b_db", "user_ref", "pass_ref");
        tenantB.setStatus(TenantStatus.ACTIVE);

        platformSuperAdmin = new PlatformUser("USR-SA", "sa@vidya.io", "hash", "Super", "Admin", null, PlatformRole.PLATFORM_SUPER_ADMIN);
        platformSuperAdmin.setId(UUID.randomUUID());

        platformAdmin = new PlatformUser("USR-PA", "pa@vidya.io", "hash", "Platform", "Admin", null, PlatformRole.PLATFORM_ADMIN);
        platformAdmin.setId(UUID.randomUUID());

        communityAdmin = new PlatformUser("USR-CA", "ca@vidya.io", "hash", "Community", "Admin", null, PlatformRole.COMMUNITY_ADMIN);
        communityAdmin.setId(UUID.randomUUID());

        communityManager = new PlatformUser("USR-CM", "cm@vidya.io", "hash", "Community", "Manager", null, PlatformRole.COMMUNITY_MANAGER);
        communityManager.setId(UUID.randomUUID());

        fieldAgent = new PlatformUser("USR-FA", "fa@vidya.io", "hash", "Field", "Agent", null, PlatformRole.FIELD_AGENT);
        fieldAgent.setId(UUID.randomUUID());

        schoolOwner = new PlatformUser("USR-SO", "so@schoola.com", "hash", "School", "Owner", null, PlatformRole.SCHOOL_OWNER, schoolAId);
        schoolOwner.setId(UUID.randomUUID());

        schoolAdmin = new PlatformUser("USR-SADM", "adm@schoola.com", "hash", "School", "Admin", null, PlatformRole.SCHOOL_ADMIN, schoolAId);
        schoolAdmin.setId(UUID.randomUUID());
    }

    // ── Token tests ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("JWT: Valid token generation and parsing")
    void token_ValidToken_CanBeParsed() {
        String token = jwtTokenProvider.generateAccessToken(
                schoolOwner.getId(), schoolOwner.getEmail(), schoolOwner.getUserCode(),
                schoolOwner.getPlatformRole().name(), schoolOwner.getSchoolId());

        assertThat(jwtTokenProvider.validateToken(token)).isTrue();
        assertThat(jwtTokenProvider.getUserIdFromToken(token)).isEqualTo(schoolOwner.getId());
        assertThat(jwtTokenProvider.getEmailFromToken(token)).isEqualTo(schoolOwner.getEmail());
        assertThat(jwtTokenProvider.getRoleFromToken(token)).isEqualTo("SCHOOL_OWNER");
        assertThat(jwtTokenProvider.getSchoolIdFromToken(token)).isEqualTo(schoolOwner.getSchoolId());
    }

    @Test
    @DisplayName("JWT: Invalid token string returns false")
    void token_InvalidToken_Fails() {
        assertThat(jwtTokenProvider.validateToken("invalid.jwt.token")).isFalse();
    }

    @Test
    @DisplayName("JWT: Expired token returns false")
    void token_ExpiredToken_Fails() {
        JwtTokenProvider shortExpiryProvider = new JwtTokenProvider(
                "vidya-dev-secret-key-change-in-production-min-256-bits-length",
                -10, // already expired
                -10
        );

        String expiredToken = shortExpiryProvider.generateAccessToken(
                schoolOwner.getId(), schoolOwner.getEmail(), schoolOwner.getUserCode(),
                schoolOwner.getPlatformRole().name(), schoolOwner.getSchoolId());

        assertThat(jwtTokenProvider.validateToken(expiredToken)).isFalse();
    }

    // ── Role and Authorization Matrix ────────────────────────────────────────

    @Test
    @DisplayName("Role: PLATFORM_SUPER_ADMIN has all permissions")
    void role_PlatformSuperAdmin_HasAllPermissions() {
        when(userRepository.findById(platformSuperAdmin.getId())).thenReturn(Optional.of(platformSuperAdmin));

        for (PlatformPermission perm : PlatformPermission.values()) {
            assertThat(tenantAuthorizationService.hasPermission(platformSuperAdmin.getId(), perm))
                    .as("Platform Super Admin should have permission: %s", perm)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("Role: PLATFORM_ADMIN has administrative permissions")
    void role_PlatformAdmin_HasAdminPermissions() {
        when(userRepository.findById(platformAdmin.getId())).thenReturn(Optional.of(platformAdmin));

        assertThat(tenantAuthorizationService.hasPermission(platformAdmin.getId(), PlatformPermission.TENANT_PROVISION)).isTrue();
        assertThat(tenantAuthorizationService.hasPermission(platformAdmin.getId(), PlatformPermission.ONBOARDING_APPROVE)).isTrue();
        assertThat(tenantAuthorizationService.hasPermission(platformAdmin.getId(), PlatformPermission.PAYMENT_REFUND)).isTrue();
    }

    @Test
    @DisplayName("Role: COMMUNITY_ADMIN has community and onboarding permissions")
    void role_CommunityAdmin_HasSpecificPermissions() {
        when(userRepository.findById(communityAdmin.getId())).thenReturn(Optional.of(communityAdmin));

        assertThat(tenantAuthorizationService.hasPermission(communityAdmin.getId(), PlatformPermission.COMMUNITY_READ)).isTrue();
        assertThat(tenantAuthorizationService.hasPermission(communityAdmin.getId(), PlatformPermission.ONBOARDING_READ)).isTrue();
        // Should NOT have tenant provision permission
        assertThat(tenantAuthorizationService.hasPermission(communityAdmin.getId(), PlatformPermission.TENANT_PROVISION)).isFalse();
    }

    @Test
    @DisplayName("Role: FIELD_AGENT has field agent permissions only")
    void role_FieldAgent_HasFieldPermissions() {
        when(userRepository.findById(fieldAgent.getId())).thenReturn(Optional.of(fieldAgent));

        assertThat(tenantAuthorizationService.hasPermission(fieldAgent.getId(), PlatformPermission.ONBOARDING_CREATE)).isTrue();
        assertThat(tenantAuthorizationService.hasPermission(fieldAgent.getId(), PlatformPermission.ONBOARDING_UPDATE)).isTrue();
        // Should NOT have tenant suspend or payment refund permissions
        assertThat(tenantAuthorizationService.hasPermission(fieldAgent.getId(), PlatformPermission.TENANT_SUSPEND)).isFalse();
        assertThat(tenantAuthorizationService.hasPermission(fieldAgent.getId(), PlatformPermission.PAYMENT_REFUND)).isFalse();
    }

    @Test
    @DisplayName("Role: SCHOOL_OWNER has school owner permissions")
    void role_SchoolOwner_HasOwnerPermissions() {
        when(userRepository.findById(schoolOwner.getId())).thenReturn(Optional.of(schoolOwner));

        assertThat(tenantAuthorizationService.hasPermission(schoolOwner.getId(), PlatformPermission.SCHOOL_UPDATE)).isTrue();
        assertThat(tenantAuthorizationService.hasPermission(schoolOwner.getId(), PlatformPermission.USER_CREATE)).isTrue();
        assertThat(tenantAuthorizationService.hasPermission(schoolOwner.getId(), PlatformPermission.PAYMENT_CREATE)).isTrue();
    }

    @Test
    @DisplayName("Role: SCHOOL_ADMIN has school admin read permissions")
    void role_SchoolAdmin_HasSchoolAdminPermissions() {
        when(userRepository.findById(schoolAdmin.getId())).thenReturn(Optional.of(schoolAdmin));

        assertThat(tenantAuthorizationService.hasPermission(schoolAdmin.getId(), PlatformPermission.SCHOOL_READ)).isTrue();
        assertThat(tenantAuthorizationService.hasPermission(schoolAdmin.getId(), PlatformPermission.SUBSCRIPTION_READ)).isTrue();
        // Should NOT have platform community creation
        assertThat(tenantAuthorizationService.hasPermission(schoolAdmin.getId(), PlatformPermission.COMMUNITY_CREATE)).isFalse();
    }

    // ── Tenant Isolation and Boundary Enforcement ────────────────────────────

    @Test
    @DisplayName("Isolation: School Owner cannot access Tenant B (cross-tenant)")
    void isolation_SchoolOwner_CannotAccessTenantB() {
        when(userRepository.findById(schoolOwner.getId())).thenReturn(Optional.of(schoolOwner));
        when(tenantRepository.findByTenantCode("TNT-002")).thenReturn(Optional.of(tenantB));

        boolean canAccess = tenantAuthorizationService.canAccessTenant(schoolOwner.getId(), "TNT-002");
        assertThat(canAccess).isFalse();
        verify(securityAuditService).logAccessDenied(eq(schoolOwner.getId()), eq("so@schoola.com"), eq("TENANT:TNT-002"), any(), any(), any());
    }

    @Test
    @DisplayName("Isolation: School Owner cannot access School B (cross-school)")
    void isolation_SchoolOwner_CannotAccessSchoolB() {
        when(userRepository.findById(schoolOwner.getId())).thenReturn(Optional.of(schoolOwner));
        when(schoolRepository.findById(schoolB.getId())).thenReturn(Optional.of(schoolB));

        boolean canAccess = tenantAuthorizationService.canAccessSchool(schoolOwner.getId(), schoolB.getId());
        assertThat(canAccess).isFalse();
        verify(securityAuditService).logAccessDenied(eq(schoolOwner.getId()), eq("so@schoola.com"), eq("SCHOOL:" + schoolB.getId()), any(), any(), any());
    }

    @Test
    @DisplayName("SpEL helper: canAccessSchool SpEL method evaluates correctly")
    void spEL_canAccessSchool() {
        PlatformUserDetails userDetails = new PlatformUserDetails(schoolOwner);
        Authentication auth = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

        when(userRepository.findById(schoolOwner.getId())).thenReturn(Optional.of(schoolOwner));
        when(schoolRepository.findById(schoolA.getId())).thenReturn(Optional.of(schoolA));
        when(schoolRepository.findById(schoolB.getId())).thenReturn(Optional.of(schoolB));

        assertThat(tenantAuthorizationService.canAccessSchool(auth, schoolA.getId())).isTrue();
        assertThat(tenantAuthorizationService.canAccessSchool(auth, schoolB.getId())).isFalse();
    }
}
