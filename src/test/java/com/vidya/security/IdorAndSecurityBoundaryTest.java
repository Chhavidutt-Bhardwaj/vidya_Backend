package com.vidya.security;

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
import com.vidya.security.entity.PlatformRole;
import com.vidya.security.entity.PlatformUser;
import com.vidya.security.repository.PlatformUserRepository;
import com.vidya.security.service.SecurityAuditService;
import com.vidya.security.service.TenantAuthorizationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdorAndSecurityBoundaryTest {

    @Mock private PlatformUserRepository userRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private SchoolRepository schoolRepository;
    @Mock private SchoolOnboardingRepository onboardingRepository;
    @Mock private CommunityMemberRepository communityMemberRepository;
    @Mock private SecurityAuditService securityAuditService;

    private TenantAuthorizationServiceImpl authorizationService;

    private UUID userAId;
    private PlatformUser userA;
    private UUID schoolAId;
    private School schoolA;
    private Tenant tenantA;

    private UUID schoolBId;
    private School schoolB;
    private Tenant tenantB;

    private UUID onboardingAId;
    private SchoolOnboarding onboardingA;

    private UUID onboardingBId;
    private SchoolOnboarding onboardingB;

    @BeforeEach
    void setUp() {
        authorizationService = new TenantAuthorizationServiceImpl(
                userRepository, tenantRepository, schoolRepository,
                onboardingRepository, communityMemberRepository, securityAuditService
        );

        schoolAId = UUID.randomUUID();
        schoolA = new School("SCH-001", "School A", "School A Legal", "REG-01", SchoolBoard.CBSE, SchoolType.ALL, "admin@schoola.com", "9999999991", "https://a.edu", SchoolStatus.ACTIVE);
        schoolA.setId(schoolAId);

        tenantA = Tenant.create(schoolAId, "TNT-A", "localhost", 5432, "tenant_a_db", "user_ref", "pass_ref");
        tenantA.setStatus(TenantStatus.ACTIVE);

        userAId = UUID.randomUUID();
        userA = new PlatformUser("USR-ADMIN-A", "admin@schoola.com", "hash", "Admin", "A", null, PlatformRole.SCHOOL_ADMIN, schoolAId);
        userA.setId(userAId);

        onboardingAId = UUID.randomUUID();
        onboardingA = new SchoolOnboarding("ONB-A", schoolA, OnboardingSource.DIRECT, null, null);
        onboardingA.setId(onboardingAId);

        schoolBId = UUID.randomUUID();
        schoolB = new School("SCH-002", "School B", "School B Legal", "REG-02", SchoolBoard.CBSE, SchoolType.ALL, "admin@schoolb.com", "9999999992", "https://b.edu", SchoolStatus.ACTIVE);
        schoolB.setId(schoolBId);

        tenantB = Tenant.create(schoolBId, "TNT-B", "localhost", 5432, "tenant_b_db", "user_ref", "pass_ref");
        tenantB.setStatus(TenantStatus.ACTIVE);

        onboardingBId = UUID.randomUUID();
        onboardingB = new SchoolOnboarding("ONB-B", schoolB, OnboardingSource.DIRECT, null, null);
        onboardingB.setId(onboardingBId);
    }

    @Test
    @DisplayName("IDOR Defense: User A is denied access when supplying School B's UUID in path or query")
    void idor_SchoolIdSpoofing_Denied() {
        when(userRepository.findById(userAId)).thenReturn(Optional.of(userA));
        when(schoolRepository.findById(schoolBId)).thenReturn(Optional.of(schoolB));

        boolean canAccess = authorizationService.canAccessSchool(userAId, schoolBId);

        assertThat(canAccess).isFalse();
        verify(securityAuditService).logAccessDenied(eq(userAId), eq("admin@schoola.com"), eq("SCHOOL:" + schoolBId), any(), any(), any());
    }

    @Test
    @DisplayName("IDOR Defense: User A is denied access when requesting Onboarding record belonging to School B")
    void idor_OnboardingIdSpoofing_Denied() {
        when(userRepository.findById(userAId)).thenReturn(Optional.of(userA));
        when(onboardingRepository.findById(onboardingBId)).thenReturn(Optional.of(onboardingB));

        boolean canAccess = authorizationService.canAccessOnboarding(userAId, onboardingBId);

        assertThat(canAccess).isFalse();
    }

    @Test
    @DisplayName("Header Spoofing Defense: User A cannot access Tenant B by setting X-Tenant-ID: TNT-B")
    void headerSpoofing_TenantHeaderSpoofing_Denied() {
        when(userRepository.findById(userAId)).thenReturn(Optional.of(userA));
        when(tenantRepository.findByTenantCode("TNT-B")).thenReturn(Optional.of(tenantB));

        boolean canAccess = authorizationService.canAccessTenant(userAId, "TNT-B");

        assertThat(canAccess).isFalse();
        verify(securityAuditService).logAccessDenied(eq(userAId), eq("admin@schoola.com"), eq("TENANT:TNT-B"), any(), any(), any());
    }
}
