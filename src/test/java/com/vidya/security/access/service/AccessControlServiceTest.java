package com.vidya.security.access.service;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.master.subscription.repository.SubscriptionFeatureRepository;
import com.vidya.security.access.AccessScope;
import com.vidya.security.access.dto.AssignRoleRequest;
import com.vidya.security.access.dto.RoleRequest;
import com.vidya.security.access.entity.AccessPermission;
import com.vidya.security.access.entity.AccessPermission.PermissionScope;
import com.vidya.security.access.entity.AccessRole;
import com.vidya.security.access.repository.AccessPermissionRepository;
import com.vidya.security.access.repository.AccessRoleRepository;
import com.vidya.security.access.repository.UserPermissionOverrideRepository;
import com.vidya.security.access.repository.UserRoleAssignmentRepository;
import com.vidya.security.entity.PlatformRole;
import com.vidya.security.entity.PlatformUser;
import com.vidya.security.repository.PlatformUserRepository;
import com.vidya.security.user.PlatformUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccessControlService — school scoping and anti-escalation")
class AccessControlServiceTest {

    @Mock private AccessPermissionRepository permissionRepository;
    @Mock private AccessRoleRepository roleRepository;
    @Mock private UserRoleAssignmentRepository assignmentRepository;
    @Mock private UserPermissionOverrideRepository overrideRepository;
    @Mock private PlatformUserRepository userRepository;
    @Mock private SubscriptionFeatureRepository featureRepository;
    @Mock private EffectiveAccessService effectiveAccessService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private AccessControlService service;
    private UUID schoolId;
    private PlatformUser schoolAdmin;

    private final AccessPermission hostelManage = perm("HOSTEL_MANAGE", PermissionScope.SCHOOL);
    private final AccessPermission feeManage = perm("FEE_MANAGE", PermissionScope.SCHOOL);
    private final AccessPermission tenantProvision = perm("TENANT_PROVISION", PermissionScope.PLATFORM);

    @BeforeEach
    void setUp() {
        service = new AccessControlService(permissionRepository, roleRepository, assignmentRepository, overrideRepository,
                userRepository, featureRepository, effectiveAccessService, eventPublisher);
        schoolId = UUID.randomUUID();
        schoolAdmin = new PlatformUser("USR-A", "a@school.edu", "h", "Ad", "Min", null, PlatformRole.SCHOOL_ADMIN, schoolId);
        schoolAdmin.setId(UUID.randomUUID());
        actAs(schoolAdmin, Set.of("SCHOOL_ROLE_MANAGE", "HOSTEL_MANAGE"), false);
        lenient().when(roleRepository.save(any(AccessRole.class))).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("school admin creates a custom role from permissions they hold")
    void createCustomRole_ok() {
        when(permissionRepository.findByCodeIn(Set.of("HOSTEL_MANAGE"))).thenReturn(List.of(hostelManage));

        var role = service.createRole(new RoleRequest("HOSTEL_WARDEN", "Hostel Warden", null, null, null, Set.of("HOSTEL_MANAGE")), schoolId);

        assertThat(role.code()).isEqualTo("HOSTEL_WARDEN");
        assertThat(role.scope()).isEqualTo(AccessScope.SCHOOL);
        assertThat(role.schoolId()).isEqualTo(schoolId);
    }

    @Test
    @DisplayName("cannot put a permission into a role that the actor does not hold (escalation)")
    void createCustomRole_withUnheldPermission_denied() {
        when(permissionRepository.findByCodeIn(Set.of("FEE_MANAGE"))).thenReturn(List.of(feeManage));

        assertThatThrownBy(() -> service.createRole(
                new RoleRequest("FEE_CLERK", "Fee Clerk", null, null, null, Set.of("FEE_MANAGE")), schoolId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("FEE_MANAGE");
        verify(roleRepository, never()).save(any());
    }

    @Test
    @DisplayName("school roles cannot contain PLATFORM permissions")
    void schoolRole_withPlatformPermission_rejected() {
        when(permissionRepository.findByCodeIn(Set.of("TENANT_PROVISION"))).thenReturn(List.of(tenantProvision));

        assertThatThrownBy(() -> service.createRole(
                new RoleRequest("SNEAKY", "Sneaky", null, null, null, Set.of("TENANT_PROVISION")), schoolId))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("school custom role cannot reuse a platform role code")
    void schoolRole_reservedCode_rejected() {
        assertThatThrownBy(() -> service.createRole(
                new RoleRequest("PLATFORM_ADMIN", "x", null, null, null, Set.of()), schoolId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("reserved");
    }

    @Test
    @DisplayName("a school cannot see or edit another school's role")
    void otherSchoolsRole_notFound() {
        AccessRole foreign = role("X", AccessScope.SCHOOL, UUID.randomUUID());
        when(roleRepository.findWithPermissionsById(foreign.getId())).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.getRole(foreign.getId(), schoolId)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("global system roles are read-only for schools")
    void globalRole_readOnlyForSchool() {
        AccessRole teacher = role("TEACHER", AccessScope.SCHOOL, null);
        when(roleRepository.findWithPermissionsById(teacher.getId())).thenReturn(Optional.of(teacher));

        assertThatThrownBy(() -> service.replaceRolePermissions(teacher.getId(), Set.of(), schoolId))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("cannot assign a role whose permissions the actor lacks")
    void assignRole_escalation_denied() {
        PlatformUser staff = schoolUser();
        AccessRole accountant = role("FEE_CLERK", AccessScope.SCHOOL, schoolId);
        accountant.getPermissions().add(feeManage);
        when(roleRepository.findWithPermissionsById(accountant.getId())).thenReturn(Optional.of(accountant));

        assertThatThrownBy(() -> service.assignRole(staff.getId(), new AssignRoleRequest(accountant.getId(), null, null, null), schoolId))
                .isInstanceOf(AccessDeniedException.class);
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("assigning a held role to a school user saves it and evicts the user's cache")
    void assignRole_ok() {
        PlatformUser staff = schoolUser();
        AccessRole warden = role("HOSTEL_WARDEN", AccessScope.SCHOOL, schoolId);
        warden.getPermissions().add(hostelManage);
        when(roleRepository.findWithPermissionsById(warden.getId())).thenReturn(Optional.of(warden));
        when(assignmentRepository.findByUserIdAndRoleId(staff.getId(), warden.getId())).thenReturn(Optional.empty());
        when(assignmentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = service.assignRole(staff.getId(), new AssignRoleRequest(warden.getId(), null, Instant.now().plusSeconds(86400), "term"), schoolId);

        assertThat(result.roleCode()).isEqualTo("HOSTEL_WARDEN");
        verify(eventPublisher).publishEvent(any(AccessChangedEvent.class));
    }

    @Test
    @DisplayName("a school admin cannot touch users of another school")
    void otherSchoolsUser_notFound() {
        PlatformUser outsider = new PlatformUser("USR-O", "o@x.edu", "h", "O", "O", null, PlatformRole.TEACHER, UUID.randomUUID());
        outsider.setId(UUID.randomUUID());
        when(userRepository.findById(outsider.getId())).thenReturn(Optional.of(outsider));

        assertThatThrownBy(() -> service.listUserRoles(outsider.getId(), schoolId)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("only a super user may edit the grants-all role")
    void superUserRole_onlyBySuperUser() {
        AccessRole root = role("PLATFORM_SUPER_ADMIN", AccessScope.PLATFORM, null);
        root.setGrantsAllPermissions(true);
        when(roleRepository.findWithPermissionsById(root.getId())).thenReturn(Optional.of(root));

        assertThatThrownBy(() -> service.replaceRolePermissions(root.getId(), Set.of(), null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("system roles cannot be deleted")
    void systemRole_notDeletable() {
        PlatformUser platformAdmin = new PlatformUser("USR-P", "p@vidya.io", "h", "P", "A", null, PlatformRole.PLATFORM_SUPER_ADMIN);
        platformAdmin.setId(UUID.randomUUID());
        actAs(platformAdmin, Set.of(), true);
        AccessRole teacher = role("TEACHER", AccessScope.SCHOOL, null);
        teacher.setSystem(true);
        when(roleRepository.findWithPermissionsById(teacher.getId())).thenReturn(Optional.of(teacher));

        assertThatThrownBy(() -> service.deleteRole(teacher.getId(), null)).isInstanceOf(BusinessException.class);
        verify(roleRepository, never()).delete(any());
        verify(assignmentRepository, never()).findUserIdsByRoleId(any());
    }

    @Test
    @DisplayName("unknown permission codes are rejected with the missing codes listed")
    void unknownPermission_rejected() {
        AccessRole warden = role("HOSTEL_WARDEN", AccessScope.SCHOOL, schoolId);
        when(roleRepository.findWithPermissionsById(warden.getId())).thenReturn(Optional.of(warden));
        when(permissionRepository.findByCodeIn(anyCollection())).thenReturn(List.of());

        assertThatThrownBy(() -> service.replaceRolePermissions(warden.getId(), Set.of("NOPE"), schoolId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("NOPE");
    }

    // ---------------------------------------------------------------------------------------

    private PlatformUser schoolUser() {
        PlatformUser staff = new PlatformUser("USR-S", "s@school.edu", "h", "St", "Aff", null, PlatformRole.SCHOOL_STAFF, schoolId);
        staff.setId(UUID.randomUUID());
        when(userRepository.findById(staff.getId())).thenReturn(Optional.of(staff));
        return staff;
    }

    private static void actAs(PlatformUser user, Set<String> permissions, boolean superUser) {
        EffectiveAccess access = new EffectiveAccess(user.getId(), user.getPlatformRole(), user.getPlatformRole().getScope(),
                user.getSchoolId(), Set.of(user.getPlatformRole().name()), Set.of(user.getPlatformRole().name()),
                permissions, Set.of(), superUser, Instant.now());
        PlatformUserDetails principal = new PlatformUserDetails(user, null, access, UUID.randomUUID());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private static AccessPermission perm(String code, PermissionScope scope) {
        AccessPermission p = new AccessPermission(code, code, null, "TEST", scope, null);
        p.setId(UUID.randomUUID());
        return p;
    }

    private static AccessRole role(String code, AccessScope scope, UUID schoolId) {
        AccessRole r = new AccessRole(code, code, null, scope, schoolId, null);
        r.setId(UUID.randomUUID());
        return r;
    }
}
