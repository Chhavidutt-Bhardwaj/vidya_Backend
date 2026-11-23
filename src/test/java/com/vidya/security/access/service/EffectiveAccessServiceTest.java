package com.vidya.security.access.service;

import com.vidya.master.subscription.service.SubscriptionEntitlementService;
import com.vidya.security.access.AccessScope;
import com.vidya.security.access.entity.AccessPermission;
import com.vidya.security.access.entity.AccessPermission.PermissionScope;
import com.vidya.security.access.entity.AccessRole;
import com.vidya.security.access.entity.UserPermissionOverride;
import com.vidya.security.access.entity.UserRoleAssignment;
import com.vidya.security.access.repository.AccessPermissionRepository;
import com.vidya.security.access.repository.AccessRoleRepository;
import com.vidya.security.access.repository.UserPermissionOverrideRepository;
import com.vidya.security.access.repository.UserRoleAssignmentRepository;
import com.vidya.security.entity.PlatformRole;
import com.vidya.security.entity.PlatformUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("EffectiveAccessService — DB-driven roles, overrides, feature gating and caching")
class EffectiveAccessServiceTest {

    @Mock private AccessRoleRepository roleRepository;
    @Mock private AccessPermissionRepository permissionRepository;
    @Mock private UserRoleAssignmentRepository assignmentRepository;
    @Mock private UserPermissionOverrideRepository overrideRepository;
    @Mock private SubscriptionEntitlementService entitlementService;

    private EffectiveAccessService service;
    private UUID schoolId;
    private PlatformUser teacher;
    private AccessRole teacherRole;

    private final AccessPermission studentRead = perm("STUDENT_READ", PermissionScope.SCHOOL, "STUDENT_MANAGEMENT");
    private final AccessPermission attendanceMark = perm("ATTENDANCE_MARK", PermissionScope.SCHOOL, "ATTENDANCE");
    private final AccessPermission hostelManage = perm("HOSTEL_MANAGE", PermissionScope.SCHOOL, "HOSTEL");
    private final AccessPermission tenantProvision = perm("TENANT_PROVISION", PermissionScope.PLATFORM, null);
    private final AccessPermission calendarRead = perm("CALENDAR_READ", PermissionScope.SCHOOL, null);

    @BeforeEach
    void setUp() {
        service = new EffectiveAccessService(roleRepository, permissionRepository, assignmentRepository,
                overrideRepository, entitlementService, 60, true);
        schoolId = UUID.randomUUID();
        teacher = new PlatformUser("USR-T", "t@school.edu", "h", "Tea", "Cher", null, PlatformRole.TEACHER, schoolId);
        teacher.setId(UUID.randomUUID());
        teacherRole = role("TEACHER", AccessScope.SCHOOL, null);

        lenient().when(roleRepository.findGlobalByCode("TEACHER")).thenReturn(Optional.of(teacherRole));
        lenient().when(assignmentRepository.findByUserIdWithRole(teacher.getId())).thenReturn(List.of());
        lenient().when(overrideRepository.findByUserIdWithPermission(teacher.getId())).thenReturn(List.of());
        lenient().when(entitlementService.getEnabledFeatureCodes(schoolId))
                .thenReturn(Set.of("STUDENT_MANAGEMENT", "ATTENDANCE"));
    }

    @Test
    @DisplayName("account-type role grants its DB permissions; features not in the plan are removed")
    void systemRole_withFeatureGating() {
        when(permissionRepository.findActiveGrantedByRoles(anyCollection()))
                .thenReturn(List.of(studentRead, attendanceMark, hostelManage, calendarRead));

        EffectiveAccess access = service.getAccess(teacher);

        assertThat(access.permissions()).containsExactlyInAnyOrder("STUDENT_READ", "ATTENDANCE_MARK", "CALENDAR_READ");
        assertThat(access.hasPermission("HOSTEL_MANAGE")).as("hostel not in the school's plan").isFalse();
        assertThat(access.roles()).containsExactly("TEACHER");
        assertThat(access.enabledFeatures()).contains("ATTENDANCE");
    }

    @Test
    @DisplayName("enabling a feature for the school (e.g. HOSTEL) unlocks its permissions without a deploy")
    void enablingFeature_unlocksPermissions() {
        when(entitlementService.getEnabledFeatureCodes(schoolId)).thenReturn(Set.of("HOSTEL"));
        when(permissionRepository.findActiveGrantedByRoles(anyCollection())).thenReturn(List.of(hostelManage));

        assertThat(service.getAccess(teacher).hasPermission("HOSTEL_MANAGE")).isTrue();
    }

    @Test
    @DisplayName("school accounts never get PLATFORM-scope permissions, even via a GRANT override")
    void schoolAccount_neverGetsPlatformPermissions() {
        when(permissionRepository.findActiveGrantedByRoles(anyCollection())).thenReturn(List.of(calendarRead));
        when(overrideRepository.findByUserIdWithPermission(teacher.getId()))
                .thenReturn(List.of(new UserPermissionOverride(teacher.getId(), tenantProvision, UserPermissionOverride.Effect.GRANT, null, null, null)));

        assertThat(service.getAccess(teacher).hasPermission("TENANT_PROVISION")).isFalse();
    }

    @Test
    @DisplayName("extra assigned role (school custom) adds permissions; DENY override wins over any grant")
    void assignmentsAndOverrides() {
        AccessRole warden = role("HOSTEL_WARDEN", AccessScope.SCHOOL, schoolId);
        when(assignmentRepository.findByUserIdWithRole(teacher.getId()))
                .thenReturn(List.of(new UserRoleAssignment(teacher.getId(), warden, schoolId, null, null, null, null)));
        when(entitlementService.getEnabledFeatureCodes(schoolId)).thenReturn(Set.of("HOSTEL", "STUDENT_MANAGEMENT"));
        when(permissionRepository.findActiveGrantedByRoles(anyCollection())).thenReturn(List.of(studentRead, hostelManage));
        when(overrideRepository.findByUserIdWithPermission(teacher.getId()))
                .thenReturn(List.of(new UserPermissionOverride(teacher.getId(), studentRead, UserPermissionOverride.Effect.DENY, null, null, null)));

        EffectiveAccess access = service.getAccess(teacher);

        assertThat(access.roles()).containsExactlyInAnyOrder("TEACHER", "HOSTEL_WARDEN");
        assertThat(access.globalRoles()).containsExactly("TEACHER");
        assertThat(access.permissions()).containsExactly("HOSTEL_MANAGE");
    }

    @Test
    @DisplayName("expired or other-school role assignments are ignored")
    void ineffectiveAssignmentsIgnored() {
        AccessRole expired = role("EXAM_CELL", AccessScope.SCHOOL, schoolId);
        AccessRole foreign = role("OTHER_SCHOOL_ROLE", AccessScope.SCHOOL, UUID.randomUUID());
        when(assignmentRepository.findByUserIdWithRole(teacher.getId())).thenReturn(List.of(
                new UserRoleAssignment(teacher.getId(), expired, schoolId, null, Instant.now().minusSeconds(5), null, null),
                new UserRoleAssignment(teacher.getId(), foreign, schoolId, null, null, null, null)));
        when(permissionRepository.findActiveGrantedByRoles(anyCollection())).thenReturn(List.of(calendarRead));

        assertThat(service.getAccess(teacher).roles()).containsExactly("TEACHER");
    }

    @Test
    @DisplayName("a school custom role never becomes a ROLE_* authority (no hasRole spoofing)")
    void customRole_doesNotProduceRoleAuthority() {
        AccessRole spoof = role("SCHOOL_ADMIN_X", AccessScope.SCHOOL, schoolId);
        when(assignmentRepository.findByUserIdWithRole(teacher.getId()))
                .thenReturn(List.of(new UserRoleAssignment(teacher.getId(), spoof, schoolId, null, null, null, null)));
        when(permissionRepository.findActiveGrantedByRoles(anyCollection())).thenReturn(List.of());

        Collection<? extends GrantedAuthority> authorities = service.getAccess(teacher).toAuthorities();

        assertThat(authorities).extracting(GrantedAuthority::getAuthority)
                .contains("ROLE_TEACHER")
                .doesNotContain("ROLE_SCHOOL_ADMIN_X");
    }

    @Test
    @DisplayName("grants_all role gets every active permission")
    void superUser_getsAllActive() {
        PlatformUser root = new PlatformUser("USR-R", "r@vidya.io", "h", "R", "R", null, PlatformRole.PLATFORM_SUPER_ADMIN);
        root.setId(UUID.randomUUID());
        AccessRole superRole = role("PLATFORM_SUPER_ADMIN", AccessScope.PLATFORM, null);
        superRole.setGrantsAllPermissions(true);
        when(roleRepository.findGlobalByCode("PLATFORM_SUPER_ADMIN")).thenReturn(Optional.of(superRole));
        when(assignmentRepository.findByUserIdWithRole(root.getId())).thenReturn(List.of());
        when(overrideRepository.findByUserIdWithPermission(root.getId())).thenReturn(List.of());
        when(permissionRepository.findByActiveTrue()).thenReturn(List.of(tenantProvision, hostelManage, calendarRead));

        EffectiveAccess access = service.getAccess(root);

        assertThat(access.superUser()).isTrue();
        assertThat(access.permissions()).containsExactlyInAnyOrder("TENANT_PROVISION", "HOSTEL_MANAGE", "CALENDAR_READ");
    }

    @Test
    @DisplayName("results are cached until an AccessChangedEvent evicts them")
    void caching_andEviction() {
        when(permissionRepository.findActiveGrantedByRoles(anyCollection())).thenReturn(List.of(calendarRead));

        service.getAccess(teacher);
        service.getAccess(teacher);
        verify(permissionRepository, times(1)).findActiveGrantedByRoles(anyCollection());

        service.onAccessChanged(AccessChangedEvent.user(teacher.getId(), "test"));
        service.getAccess(teacher);
        verify(permissionRepository, times(2)).findActiveGrantedByRoles(anyCollection());

        service.onAccessChanged(AccessChangedEvent.all("test"));
        service.getAccess(teacher);
        verify(permissionRepository, times(3)).findActiveGrantedByRoles(anyCollection());
    }

    @Test
    @DisplayName("changing a user's account type invalidates their cached access immediately")
    void accountTypeChange_bypassesCache() {
        when(permissionRepository.findActiveGrantedByRoles(anyCollection())).thenReturn(List.of(calendarRead));
        service.getAccess(teacher);

        AccessRole principalRole = role("PRINCIPAL", AccessScope.SCHOOL, null);
        when(roleRepository.findGlobalByCode("PRINCIPAL")).thenReturn(Optional.of(principalRole));
        teacher.setPlatformRole(PlatformRole.PRINCIPAL);

        assertThat(service.getAccess(teacher).roles()).containsExactly("PRINCIPAL");
    }

    // ---------------------------------------------------------------------------------------

    private static AccessPermission perm(String code, PermissionScope scope, String feature) {
        AccessPermission p = new AccessPermission(code, code, null, "TEST", scope, feature);
        p.setId(UUID.randomUUID());
        return p;
    }

    private static AccessRole role(String code, AccessScope scope, UUID schoolId) {
        AccessRole r = new AccessRole(code, code, null, scope, schoolId, null);
        r.setId(UUID.randomUUID());
        return r;
    }
}
