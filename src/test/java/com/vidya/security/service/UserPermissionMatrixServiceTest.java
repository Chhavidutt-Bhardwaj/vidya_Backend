package com.vidya.security.service;

import com.vidya.master.community.entity.CommunityMember;
import com.vidya.master.community.entity.CommunityRoleDefinition;
import com.vidya.master.community.entity.CommunityRolePermission;
import com.vidya.master.community.repository.CommunityMemberRepository;
import com.vidya.security.access.SeedRoleMatrix;
import com.vidya.security.access.repository.AccessPermissionRepository;
import com.vidya.security.access.service.EffectiveAccess;
import com.vidya.security.access.service.EffectiveAccessService;
import com.vidya.security.entity.PlatformRole;
import com.vidya.security.entity.PlatformUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.vidya.security.service.UserPermissionMatrixServiceImpl.ACTION_APPROVE;
import static com.vidya.security.service.UserPermissionMatrixServiceImpl.ACTION_DELETE;
import static com.vidya.security.service.UserPermissionMatrixServiceImpl.ACTION_EDIT;
import static com.vidya.security.service.UserPermissionMatrixServiceImpl.ACTION_FULL;
import static com.vidya.security.service.UserPermissionMatrixServiceImpl.ACTION_SCOPED;
import static com.vidya.security.service.UserPermissionMatrixServiceImpl.ACTION_VIEW;
import static com.vidya.security.service.UserPermissionMatrixServiceImpl.SECTION_ATTENDANCE;
import static com.vidya.security.service.UserPermissionMatrixServiceImpl.SECTION_CHAT;
import static com.vidya.security.service.UserPermissionMatrixServiceImpl.SECTION_FEES;
import static com.vidya.security.service.UserPermissionMatrixServiceImpl.SECTION_LIBRARY;
import static com.vidya.security.service.UserPermissionMatrixServiceImpl.SECTION_MARKS;
import static com.vidya.security.service.UserPermissionMatrixServiceImpl.SECTION_STAFF;
import static com.vidya.security.service.UserPermissionMatrixServiceImpl.SECTION_STUDENTS;
import static com.vidya.security.service.UserPermissionMatrixServiceImpl.SECTION_TIMETABLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The matrix is derived from the seeded DB data (V29 + V30): role grants → permissions → matrix cells.
 * These tests pin the matrix each account type sees out of the box.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserPermissionMatrixService — matrix derived from DB permissions")
class UserPermissionMatrixServiceTest {

    @Mock private EffectiveAccessService effectiveAccessService;
    @Mock private AccessPermissionRepository permissionRepository;
    @Mock private CommunityMemberRepository communityMemberRepository;

    private UserPermissionMatrixServiceImpl service;
    private final UUID schoolId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new UserPermissionMatrixServiceImpl(effectiveAccessService, permissionRepository, communityMemberRepository);
        lenient().when(permissionRepository.findMatrixCellsByCodes(anyCollection()))
                .thenAnswer(inv -> SeedRoleMatrix.matrixRows(inv.<Collection<String>>getArgument(0)));
    }

    private Map<Integer, Set<Integer>> matrixOf(PlatformRole role) {
        PlatformUser user = user(role);
        return service.resolvePermissionsMatrix(user, SeedRoleMatrix.accessFor(user));
    }

    @Test
    @DisplayName("super admin (grants all) sees every seeded section with all actions it maps to")
    void superAdmin() {
        Map<Integer, Set<Integer>> m = matrixOf(PlatformRole.PLATFORM_SUPER_ADMIN);
        assertThat(m).containsKeys(SECTION_STUDENTS, SECTION_STAFF, SECTION_ATTENDANCE, SECTION_FEES,
                SECTION_MARKS, SECTION_TIMETABLE, SECTION_CHAT, SECTION_LIBRARY);
        assertThat(m.get(SECTION_STUDENTS)).contains(ACTION_VIEW, ACTION_EDIT, ACTION_DELETE, ACTION_APPROVE, ACTION_FULL);
    }

    @Test
    @DisplayName("school admin: full student/staff/attendance/timetable, read-only fees and library")
    void schoolAdmin() {
        Map<Integer, Set<Integer>> m = matrixOf(PlatformRole.SCHOOL_ADMIN);
        assertThat(m.get(SECTION_STUDENTS)).contains(ACTION_VIEW, ACTION_EDIT, ACTION_DELETE, ACTION_FULL);
        assertThat(m.get(SECTION_STAFF)).contains(ACTION_VIEW, ACTION_EDIT, ACTION_FULL);
        assertThat(m.get(SECTION_TIMETABLE)).contains(ACTION_VIEW, ACTION_EDIT, ACTION_FULL);
        assertThat(m.get(SECTION_FEES)).containsExactly(ACTION_VIEW);
        assertThat(m.get(SECTION_LIBRARY)).containsExactly(ACTION_VIEW);
        assertThat(m.get(SECTION_STUDENTS)).doesNotContain(ACTION_SCOPED);
    }

    @Test
    @DisplayName("class teacher: own-class (SCOPED) students, attendance, marks, timetable and chat; never FULL")
    void teacher() {
        Map<Integer, Set<Integer>> m = matrixOf(PlatformRole.TEACHER);
        assertThat(m.get(SECTION_STUDENTS)).contains(ACTION_VIEW, ACTION_SCOPED).doesNotContain(ACTION_FULL, ACTION_DELETE);
        assertThat(m.get(SECTION_ATTENDANCE)).contains(ACTION_VIEW, ACTION_EDIT, ACTION_SCOPED).doesNotContain(ACTION_FULL);
        assertThat(m.get(SECTION_MARKS)).contains(ACTION_VIEW, ACTION_EDIT, ACTION_SCOPED);
        assertThat(m.get(SECTION_CHAT)).contains(ACTION_VIEW, ACTION_EDIT, ACTION_SCOPED);
        assertThat(m).doesNotContainKey(SECTION_FEES);
    }

    @Test
    @DisplayName("accounts: full fees, library edit, student view")
    void accounts() {
        Map<Integer, Set<Integer>> m = matrixOf(PlatformRole.ACCOUNTS);
        assertThat(m.get(SECTION_FEES)).contains(ACTION_VIEW, ACTION_EDIT, ACTION_FULL);
        assertThat(m.get(SECTION_LIBRARY)).contains(ACTION_VIEW, ACTION_EDIT);
        assertThat(m.get(SECTION_STUDENTS)).contains(ACTION_VIEW);
    }

    @Test
    @DisplayName("parent: own child (SCOPED) students and attendance, can view and pay fees, chat")
    void parent() {
        Map<Integer, Set<Integer>> m = matrixOf(PlatformRole.PARENT);
        assertThat(m.get(SECTION_STUDENTS)).containsExactlyInAnyOrder(ACTION_VIEW, ACTION_SCOPED);
        assertThat(m.get(SECTION_ATTENDANCE)).containsExactlyInAnyOrder(ACTION_VIEW, ACTION_SCOPED);
        assertThat(m.get(SECTION_FEES)).containsExactlyInAnyOrder(ACTION_VIEW, ACTION_EDIT);
        assertThat(m.get(SECTION_CHAT)).contains(ACTION_VIEW, ACTION_EDIT, ACTION_SCOPED);
        assertThat(m).doesNotContainKey(SECTION_STAFF);
    }

    @Test
    @DisplayName("student: read-only, own records")
    void student() {
        Map<Integer, Set<Integer>> m = matrixOf(PlatformRole.STUDENT);
        assertThat(m.get(SECTION_STUDENTS)).containsExactlyInAnyOrder(ACTION_VIEW, ACTION_SCOPED);
        assertThat(m.get(SECTION_MARKS)).containsExactly(ACTION_VIEW);
        assertThat(m.get(SECTION_TIMETABLE)).containsExactly(ACTION_VIEW);
        assertThat(m.get(SECTION_LIBRARY)).containsExactly(ACTION_VIEW);
        m.values().forEach(actions -> assertThat(actions).doesNotContain(ACTION_EDIT, ACTION_DELETE, ACTION_FULL));
    }

    @Test
    @DisplayName("the matrix follows effective access: a DENY or a disabled feature removes the cells")
    void followsEffectiveAccess() {
        PlatformUser parent = user(PlatformRole.PARENT);
        EffectiveAccess seeded = SeedRoleMatrix.accessFor(parent);
        Set<String> withoutFees = new HashSet<>(seeded.permissions());
        withoutFees.removeIf(p -> p.startsWith("FEE_"));          // e.g. school has no FEES module
        withoutFees.remove("CHAT_SEND");                            // e.g. DENY override
        EffectiveAccess access = new EffectiveAccess(parent.getId(), parent.getPlatformRole(), seeded.scope(), schoolId,
                seeded.roles(), seeded.globalRoles(), withoutFees, Set.of(), false, Instant.now());

        Map<Integer, Set<Integer>> m = service.resolvePermissionsMatrix(parent, access);

        assertThat(m).doesNotContainKey(SECTION_FEES);
        assertThat(m.get(SECTION_CHAT)).doesNotContain(ACTION_EDIT);
    }

    @Test
    @DisplayName("no permissions → empty matrix without querying cells")
    void noPermissions() {
        PlatformUser u = user(PlatformRole.SCHOOL_STAFF);
        EffectiveAccess none = new EffectiveAccess(u.getId(), u.getPlatformRole(), u.getPlatformRole().getScope(), schoolId,
                Set.of(), Set.of(), Set.of(), Set.of(), false, Instant.now());

        assertThat(service.resolvePermissionsMatrix(u, none)).isEmpty();
        verify(permissionRepository, never()).findMatrixCellsByCodes(anyCollection());
    }

    @Test
    @DisplayName("community members also get the cells of their custom community role")
    void communityCustomRole() {
        PlatformUser agent = new PlatformUser("USR-F", "f@vidya.io", "h", "F", "A", null, PlatformRole.FIELD_AGENT);
        agent.setId(UUID.randomUUID());
        CommunityRoleDefinition customRole = mock(CommunityRoleDefinition.class);
        when(customRole.getPermissions()).thenReturn(Set.of(
                new CommunityRolePermission(customRole, 21, ACTION_VIEW, true),
                new CommunityRolePermission(customRole, 22, ACTION_EDIT, false)));
        CommunityMember member = mock(CommunityMember.class);
        when(member.getCustomRole()).thenReturn(customRole);
        when(communityMemberRepository.findByPlatformUserId(agent.getId())).thenReturn(List.of(member));

        Map<Integer, Set<Integer>> m = service.resolvePermissionsMatrix(agent, SeedRoleMatrix.accessFor(agent));

        assertThat(m.get(21)).containsExactly(ACTION_VIEW);
        assertThat(m).doesNotContainKey(22);
    }

    @Test
    @DisplayName("single-argument overload resolves effective access first")
    void resolvesAccessWhenNotGiven() {
        PlatformUser t = user(PlatformRole.TEACHER);
        when(effectiveAccessService.getAccess(t)).thenReturn(SeedRoleMatrix.accessFor(t));

        assertThat(service.resolvePermissionsMatrix(t)).containsKey(SECTION_ATTENDANCE);
    }

    private PlatformUser user(PlatformRole role) {
        UUID school = role.getScope().isSchoolBound() ? schoolId : null;
        PlatformUser u = new PlatformUser("USR-" + role, role.name().toLowerCase() + "@x.io", "h", "F", "L", null, role, school);
        u.setId(UUID.randomUUID());
        return u;
    }
}
