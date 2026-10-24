package com.vidya.security.service;

import com.vidya.master.community.entity.Community;
import com.vidya.master.community.entity.CommunityMember;
import com.vidya.master.community.entity.CommunityMemberRole;
import com.vidya.master.community.entity.CommunityStatus;
import com.vidya.master.community.entity.CommunityType;
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
import com.vidya.security.entity.UserStatus;
import com.vidya.security.repository.PlatformUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantAuthorizationServiceTest {

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

    private TenantAuthorizationServiceImpl authorizationService;

    private UUID superAdminId;
    private PlatformUser superAdmin;

    private UUID schoolAdminAId;
    private PlatformUser schoolAdminA;
    private UUID schoolAId;
    private School schoolA;
    private Tenant tenantA;

    private UUID schoolBId;
    private School schoolB;
    private Tenant tenantB;

    private UUID fieldAgentId;
    private PlatformUser fieldAgent;

    @BeforeEach
    void setUp() {
        authorizationService = new TenantAuthorizationServiceImpl(
                userRepository,
                tenantRepository,
                schoolRepository,
                onboardingRepository,
                communityMemberRepository,
                securityAuditService
        );

        superAdminId = UUID.randomUUID();
        superAdmin = new PlatformUser("USR-SUPER", "super@vidya.io", "hash", "Super", "Admin", null, PlatformRole.PLATFORM_SUPER_ADMIN);
        superAdmin.setId(superAdminId);

        schoolAId = UUID.randomUUID();
        schoolA = new School("SCH-001", "School A", "School A Legal", "REG-01", SchoolBoard.CBSE, SchoolType.ALL, "contact@a.com", "1234567890", "https://a.com", SchoolStatus.ACTIVE);
        schoolA.setId(schoolAId);

        tenantA = Tenant.create(schoolAId, "TNT-001", "host", 5432, "db_a", "user_ref", "pass_ref");
        tenantA.setStatus(TenantStatus.ACTIVE);

        schoolAdminAId = UUID.randomUUID();
        schoolAdminA = new PlatformUser("USR-ADMIN-A", "admin@schoola.com", "hash", "Admin", "A", null, PlatformRole.SCHOOL_ADMIN, schoolAId);
        schoolAdminA.setId(schoolAdminAId);

        schoolBId = UUID.randomUUID();
        schoolB = new School("SCH-002", "School B", "School B Legal", "REG-02", SchoolBoard.CBSE, SchoolType.ALL, "contact@b.com", "1234567890", "https://b.com", SchoolStatus.ACTIVE);
        schoolB.setId(schoolBId);

        tenantB = Tenant.create(schoolBId, "TNT-002", "host", 5432, "db_b", "user_ref", "pass_ref");
        tenantB.setStatus(TenantStatus.ACTIVE);

        fieldAgentId = UUID.randomUUID();
        fieldAgent = new PlatformUser("USR-AGENT", "agent@vidya.io", "hash", "Field", "Agent", null, PlatformRole.FIELD_AGENT);
        fieldAgent.setId(fieldAgentId);
    }

    @Test
    @DisplayName("Super Admin should have full access to any tenant and school")
    void superAdmin_HasFullAccess() {
        when(userRepository.findById(superAdminId)).thenReturn(Optional.of(superAdmin));
        when(tenantRepository.findByTenantCode("TNT-001")).thenReturn(Optional.of(tenantA));
        when(schoolRepository.findById(schoolAId)).thenReturn(Optional.of(schoolA));

        assertThat(authorizationService.canAccessTenant(superAdminId, "TNT-001")).isTrue();
        assertThat(authorizationService.canAccessSchool(superAdminId, schoolAId)).isTrue();
        assertThat(authorizationService.hasPermission(superAdminId, PlatformPermission.TENANT_SUSPEND)).isTrue();
    }

    @Test
    @DisplayName("School Admin A can access School A and Tenant A")
    void schoolAdminA_CanAccessSchoolA_AndTenantA() {
        when(userRepository.findById(schoolAdminAId)).thenReturn(Optional.of(schoolAdminA));
        when(tenantRepository.findByTenantCode("TNT-001")).thenReturn(Optional.of(tenantA));
        when(schoolRepository.findById(schoolAId)).thenReturn(Optional.of(schoolA));

        assertThat(authorizationService.canAccessSchool(schoolAdminAId, schoolAId)).isTrue();
        assertThat(authorizationService.canAccessTenant(schoolAdminAId, "TNT-001")).isTrue();
    }

    @Test
    @DisplayName("School Admin A CANNOT access School B (cross-school denial)")
    void schoolAdminA_CannotAccessSchoolB() {
        when(userRepository.findById(schoolAdminAId)).thenReturn(Optional.of(schoolAdminA));
        when(schoolRepository.findById(schoolBId)).thenReturn(Optional.of(schoolB));

        boolean allowed = authorizationService.canAccessSchool(schoolAdminAId, schoolBId);
        assertThat(allowed).isFalse();
    }

    @Test
    @DisplayName("School Admin A CANNOT access Tenant B (cross-tenant denial)")
    void schoolAdminA_CannotAccessTenantB() {
        when(userRepository.findById(schoolAdminAId)).thenReturn(Optional.of(schoolAdminA));
        when(tenantRepository.findByTenantCode("TNT-002")).thenReturn(Optional.of(tenantB));

        boolean allowed = authorizationService.canAccessTenant(schoolAdminAId, "TNT-002");
        assertThat(allowed).isFalse();
    }

    @Test
    @DisplayName("School user access to SUSPENDED tenant is blocked")
    void schoolUser_AccessToSuspendedTenant_Blocked() {
        tenantA.setStatus(TenantStatus.SUSPENDED);
        when(userRepository.findById(schoolAdminAId)).thenReturn(Optional.of(schoolAdminA));
        when(tenantRepository.findByTenantCode("TNT-001")).thenReturn(Optional.of(tenantA));

        boolean allowed = authorizationService.canAccessTenant(schoolAdminAId, "TNT-001");
        assertThat(allowed).isFalse();
    }

    @Test
    @DisplayName("School user access to DEACTIVATED school is blocked")
    void schoolUser_AccessToDeactivatedSchool_Blocked() {
        schoolA.setStatus(SchoolStatus.DEACTIVATED);
        when(userRepository.findById(schoolAdminAId)).thenReturn(Optional.of(schoolAdminA));
        when(schoolRepository.findById(schoolAId)).thenReturn(Optional.of(schoolA));

        boolean allowed = authorizationService.canAccessSchool(schoolAdminAId, schoolAId);
        assertThat(allowed).isFalse();
    }

    @Test
    @DisplayName("Locked or suspended user is blocked from all access")
    void lockedUser_AccessBlocked() {
        schoolAdminA.setStatus(UserStatus.SUSPENDED);
        when(userRepository.findById(schoolAdminAId)).thenReturn(Optional.of(schoolAdminA));

        assertThat(authorizationService.canAccessSchool(schoolAdminAId, schoolAId)).isFalse();
        assertThat(authorizationService.canAccessTenant(schoolAdminAId, "TNT-001")).isFalse();
    }

    @Test
    @DisplayName("Field Agent can access onboarding and school assigned to their community")
    void fieldAgent_AssignedCommunity_AccessAllowed() {
        UUID communityId = UUID.randomUUID();
        Community community = new Community(
                "COM-01", "Community 1", "desc", CommunityType.NGO, CommunityStatus.ACTIVE
        );
        community.setId(communityId);

        CommunityMember member = new CommunityMember(community, fieldAgentId, "MEM-01", CommunityMemberRole.FIELD_AGENT);
        member.setId(UUID.randomUUID());

        SchoolOnboarding onboarding = new SchoolOnboarding("ONB-01", schoolA, OnboardingSource.COMMUNITY, communityId, member.getId());
        UUID onboardingId = UUID.randomUUID();
        onboarding.setId(onboardingId);

        when(userRepository.findById(fieldAgentId)).thenReturn(Optional.of(fieldAgent));
        when(schoolRepository.findById(schoolAId)).thenReturn(Optional.of(schoolA));
        when(onboardingRepository.existsByPlatformUserIdAndSchoolId(fieldAgentId, schoolAId)).thenReturn(true);
        when(communityMemberRepository.findByPlatformUserId(fieldAgentId)).thenReturn(List.of(member));
        when(onboardingRepository.findById(onboardingId)).thenReturn(Optional.of(onboarding));

        assertThat(authorizationService.canAccessSchool(fieldAgentId, schoolAId)).isTrue();
        assertThat(authorizationService.canAccessOnboarding(fieldAgentId, onboardingId)).isTrue();
    }

    @Test
    @DisplayName("Field Agent CANNOT access unassigned school")
    void fieldAgent_UnassignedSchool_AccessDenied() {
        when(userRepository.findById(fieldAgentId)).thenReturn(Optional.of(fieldAgent));
        when(schoolRepository.findById(schoolBId)).thenReturn(Optional.of(schoolB));
        when(onboardingRepository.existsByPlatformUserIdAndSchoolId(fieldAgentId, schoolBId)).thenReturn(false);

        assertThat(authorizationService.canAccessSchool(fieldAgentId, schoolBId)).isFalse();
    }

    @Test
    @DisplayName("Role permission checks correctly reflect PlatformRole permission set")
    void permissionChecks() {
        when(userRepository.findById(schoolAdminAId)).thenReturn(Optional.of(schoolAdminA));

        assertThat(authorizationService.hasPermission(schoolAdminAId, PlatformPermission.SCHOOL_READ)).isTrue();
        assertThat(authorizationService.hasPermission(schoolAdminAId, PlatformPermission.SUBSCRIPTION_READ)).isTrue();
        // School Admin should NOT have permission to suspend tenants
        assertThat(authorizationService.hasPermission(schoolAdminAId, PlatformPermission.TENANT_SUSPEND)).isFalse();
        // School Admin should NOT have permission to create communities
        assertThat(authorizationService.hasPermission(schoolAdminAId, PlatformPermission.COMMUNITY_CREATE)).isFalse();
    }

    @Test
    @DisplayName("Tenant with null schoolId is safely denied without throwing NullPointerException")
    void tenantWithoutSchool_safelyDenied() {
        Tenant tenantNullSchool = Tenant.create(null, "TNT-NOSCHOOL", "host", 5432, "db_null", "u", "p");
        tenantNullSchool.setStatus(TenantStatus.ACTIVE);
        when(userRepository.findById(schoolAdminAId)).thenReturn(Optional.of(schoolAdminA));
        when(tenantRepository.findByTenantCode("TNT-NOSCHOOL")).thenReturn(Optional.of(tenantNullSchool));

        assertThat(authorizationService.canAccessTenant(schoolAdminAId, "TNT-NOSCHOOL")).isFalse();
    }

    @Test
    @DisplayName("PLATFORM_ADMIN can access ACTIVE and SUSPENDED tenants, but not DEACTIVATED or ARCHIVED")
    void platformAdmin_tenantAccessRules() {
        UUID adminId = UUID.randomUUID();
        PlatformUser platformAdmin = new PlatformUser("USR-PA", "pa@vidya.io", "h", "PA", "User", null, PlatformRole.PLATFORM_ADMIN);
        platformAdmin.setId(adminId);

        when(userRepository.findById(adminId)).thenReturn(Optional.of(platformAdmin));
        when(tenantRepository.findByTenantCode("TNT-001")).thenReturn(Optional.of(tenantA));

        tenantA.setStatus(TenantStatus.ACTIVE);
        assertThat(authorizationService.canAccessTenant(adminId, "TNT-001")).isTrue();

        tenantA.setStatus(TenantStatus.SUSPENDED);
        assertThat(authorizationService.canAccessTenant(adminId, "TNT-001")).isTrue();

        tenantA.setStatus(TenantStatus.DEACTIVATED);
        assertThat(authorizationService.canAccessTenant(adminId, "TNT-001")).isFalse();

        tenantA.setStatus(TenantStatus.ARCHIVED);
        assertThat(authorizationService.canAccessTenant(adminId, "TNT-001")).isFalse();
    }

    @Test
    @DisplayName("SCHOOL_OWNER can access their school tenant and is denied for mismatched tenant")
    void schoolOwner_tenantAccess() {
        UUID ownerId = UUID.randomUUID();
        PlatformUser owner = new PlatformUser("USR-OWNER", "owner@a.com", "h", "Owner", "A", null, PlatformRole.SCHOOL_OWNER, schoolAId);
        owner.setId(ownerId);

        when(userRepository.findById(ownerId)).thenReturn(Optional.of(owner));
        when(tenantRepository.findByTenantCode("TNT-001")).thenReturn(Optional.of(tenantA));
        when(tenantRepository.findByTenantCode("TNT-002")).thenReturn(Optional.of(tenantB));

        assertThat(authorizationService.canAccessTenant(ownerId, "TNT-001")).isTrue();
        assertThat(authorizationService.canAccessTenant(ownerId, "TNT-002")).isFalse();
    }

    @Test
    @DisplayName("TEACHER can access their school tenant and is denied for mismatched tenant")
    void teacher_tenantAccess() {
        UUID teacherId = UUID.randomUUID();
        PlatformUser teacher = new PlatformUser("USR-TEACH", "teacher@a.com", "h", "Teach", "A", null, PlatformRole.TEACHER, schoolAId);
        teacher.setId(teacherId);

        when(userRepository.findById(teacherId)).thenReturn(Optional.of(teacher));
        when(tenantRepository.findByTenantCode("TNT-001")).thenReturn(Optional.of(tenantA));
        when(tenantRepository.findByTenantCode("TNT-002")).thenReturn(Optional.of(tenantB));

        assertThat(authorizationService.canAccessTenant(teacherId, "TNT-001")).isTrue();
        assertThat(authorizationService.canAccessTenant(teacherId, "TNT-002")).isFalse();
    }

    @Test
    @DisplayName("COMMUNITY_ADMIN and COMMUNITY_MANAGER can access tenant of onboarded school via single exists query")
    void communityRoles_tenantAccess() {
        UUID commAdminId = UUID.randomUUID();
        PlatformUser commAdmin = new PlatformUser("USR-CA", "ca@vidya.io", "h", "CA", "User", null, PlatformRole.COMMUNITY_ADMIN);
        commAdmin.setId(commAdminId);

        when(userRepository.findById(commAdminId)).thenReturn(Optional.of(commAdmin));
        when(tenantRepository.findByTenantCode("TNT-001")).thenReturn(Optional.of(tenantA));
        when(onboardingRepository.existsByPlatformUserIdAndSchoolId(commAdminId, schoolAId)).thenReturn(true);

        assertThat(authorizationService.canAccessTenant(commAdminId, "TNT-001")).isTrue();
    }

    @Test
    @DisplayName("COMMUNITY_ADMIN is denied access to tenant when no onboarding link exists")
    void communityRoles_tenantAccess_unlinked_denied() {
        UUID commAdminId = UUID.randomUUID();
        PlatformUser commAdmin = new PlatformUser("USR-CA2", "ca2@vidya.io", "h", "CA2", "User", null, PlatformRole.COMMUNITY_ADMIN);
        commAdmin.setId(commAdminId);

        when(userRepository.findById(commAdminId)).thenReturn(Optional.of(commAdmin));
        when(tenantRepository.findByTenantCode("TNT-001")).thenReturn(Optional.of(tenantA));
        when(onboardingRepository.existsByPlatformUserIdAndSchoolId(commAdminId, schoolAId)).thenReturn(false);

        assertThat(authorizationService.canAccessTenant(commAdminId, "TNT-001")).isFalse();
    }

    @Test
    @DisplayName("User with no assigned schoolId is denied access to tenant")
    void userWithoutSchoolId_deniedTenantAccess() {
        UUID noSchoolAdminId = UUID.randomUUID();
        PlatformUser noSchoolAdmin = new PlatformUser("USR-NOSCH", "ns@vidya.io", "h", "NS", "User", null, PlatformRole.SCHOOL_ADMIN, null);
        noSchoolAdmin.setId(noSchoolAdminId);

        when(userRepository.findById(noSchoolAdminId)).thenReturn(Optional.of(noSchoolAdmin));
        when(tenantRepository.findByTenantCode("TNT-001")).thenReturn(Optional.of(tenantA));

        assertThat(authorizationService.canAccessTenant(noSchoolAdminId, "TNT-001")).isFalse();
    }
}
