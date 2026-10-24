package com.vidya.security.service;

import com.vidya.master.community.entity.CommunityMember;
import com.vidya.master.community.entity.CommunityRoleDefinition;
import com.vidya.master.community.entity.CommunityRolePermission;
import com.vidya.master.community.repository.CommunityMemberRepository;
import com.vidya.master.subscription.entity.FeatureCategory;
import com.vidya.master.subscription.entity.SchoolSubscription;
import com.vidya.master.subscription.entity.SubscriptionFeature;
import com.vidya.master.subscription.entity.SubscriptionPlan;
import com.vidya.master.subscription.entity.SubscriptionPlanFeature;
import com.vidya.master.subscription.repository.SchoolSubscriptionRepository;
import com.vidya.master.subscription.repository.SubscriptionPlanFeatureRepository;
import com.vidya.security.entity.PlatformRole;
import com.vidya.security.entity.PlatformUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserPermissionMatrixServiceTest {

    @Mock
    private SchoolSubscriptionRepository schoolSubscriptionRepository;

    @Mock
    private SubscriptionPlanFeatureRepository subscriptionPlanFeatureRepository;

    @Mock
    private CommunityMemberRepository communityMemberRepository;

    @InjectMocks
    private UserPermissionMatrixServiceImpl userPermissionMatrixService;

    private UUID userId;
    private UUID schoolId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        schoolId = UUID.randomUUID();
    }

    private PlatformUser createUser(PlatformRole role, UUID schoolId) {
        PlatformUser user = new PlatformUser(
                "USR-001", "test@vidya.io", "hashedPass", "First", "Last", "+919999999999", role
        );
        ReflectionTestUtils.setField(user, "id", userId);
        user.setSchoolId(schoolId);
        return user;
    }

    @Test
    void resolvePermissionsMatrix_SuperAdmin_GetsFullMatrix() {
        PlatformUser superAdmin = createUser(PlatformRole.PLATFORM_SUPER_ADMIN, null);

        Map<Integer, Set<Integer>> matrix = userPermissionMatrixService.resolvePermissionsMatrix(superAdmin);

        assertThat(matrix).containsKey(UserPermissionMatrixServiceImpl.SECTION_STUDENTS);
        assertThat(matrix).containsKey(UserPermissionMatrixServiceImpl.SECTION_STAFF);
        assertThat(matrix).containsKey(UserPermissionMatrixServiceImpl.SECTION_ATTENDANCE);
        assertThat(matrix).containsKey(UserPermissionMatrixServiceImpl.SECTION_COMMUNITY);
        assertThat(matrix.get(UserPermissionMatrixServiceImpl.SECTION_STUDENTS))
                .contains(UserPermissionMatrixServiceImpl.ACTION_VIEW, UserPermissionMatrixServiceImpl.ACTION_EDIT);
    }

    @Test
    void resolvePermissionsMatrix_Teacher_HasScopedStudentAndAttendanceAccess() {
        PlatformUser teacher = createUser(PlatformRole.TEACHER, null);

        Map<Integer, Set<Integer>> matrix = userPermissionMatrixService.resolvePermissionsMatrix(teacher);

        // Teacher has SIS (4) with View (-9) and Scoped (-5)
        assertThat(matrix).containsKey(4);
        assertThat(matrix.get(4)).contains(-9, -5);

        // Attendance (12) with View, Edit, Scoped
        assertThat(matrix).containsKey(12);
        assertThat(matrix.get(12)).contains(-9, -8, -5);

        // Should NOT have Fees (13)
        assertThat(matrix).doesNotContainKey(13);
    }

    @Test
    void resolvePermissionsMatrix_Student_HasReadOnlyAccess() {
        PlatformUser student = createUser(PlatformRole.STUDENT, null);

        Map<Integer, Set<Integer>> matrix = userPermissionMatrixService.resolvePermissionsMatrix(student);

        // Student has Read-Only (view -9, scoped -5) for students
        assertThat(matrix.get(4)).containsExactlyInAnyOrder(-9, -5);

        // Should NOT have Fees
        assertThat(matrix).doesNotContainKey(13);
    }

    @Test
    void resolvePermissionsMatrix_FilteredBySubscription_OnlySubscribedModulesRetained() {
        PlatformUser schoolAdmin = createUser(PlatformRole.SCHOOL_ADMIN, schoolId);

        UUID planId = UUID.randomUUID();
        SubscriptionPlan plan = Mockito.mock(SubscriptionPlan.class);
        when(plan.getId()).thenReturn(planId);

        SchoolSubscription sub = Mockito.mock(SchoolSubscription.class);
        when(sub.getPlan()).thenReturn(plan);

        when(schoolSubscriptionRepository.findActiveSubscriptionsBySchoolId(schoolId))
                .thenReturn(List.of(sub));

        SubscriptionFeature studentFeature = new SubscriptionFeature(
                "STUDENT_MANAGEMENT", "Student Management", "Desc", FeatureCategory.CORE
        );
        SubscriptionPlanFeature spf = Mockito.mock(SubscriptionPlanFeature.class);
        when(spf.isIncluded()).thenReturn(true);
        when(spf.getFeature()).thenReturn(studentFeature);

        when(subscriptionPlanFeatureRepository.findByPlanId(planId))
                .thenReturn(List.of(spf));

        Map<Integer, Set<Integer>> matrix = userPermissionMatrixService.resolvePermissionsMatrix(schoolAdmin);

        // Because school ONLY subscribed to STUDENT_MANAGEMENT (4), only section 4 is returned!
        assertThat(matrix.keySet()).containsExactly(4);
        assertThat(matrix.get(4)).contains(-9, -8, -7, -6, -4);
    }

    @Test
    void resolvePermissionsMatrix_CommunityUserWithCustomRole_LoadsDynamicPermissions() {
        PlatformUser commUser = createUser(PlatformRole.COMMUNITY_ADMIN, null);

        CommunityRoleDefinition customRole = Mockito.mock(CommunityRoleDefinition.class);
        CommunityRolePermission perm1 = new CommunityRolePermission(customRole, 4, -9, true);
        CommunityRolePermission perm2 = new CommunityRolePermission(customRole, 11, -9, true);
        when(customRole.getPermissions()).thenReturn(new HashSet<>(Set.of(perm1, perm2)));

        CommunityMember member = Mockito.mock(CommunityMember.class);
        when(member.getCustomRole()).thenReturn(customRole);

        when(communityMemberRepository.findByPlatformUserId(userId)).thenReturn(List.of(member));

        Map<Integer, Set<Integer>> matrix = userPermissionMatrixService.resolvePermissionsMatrix(commUser);

        assertThat(matrix).containsKey(4);
        assertThat(matrix.get(4)).contains(-9);
        assertThat(matrix).containsKey(11);
        assertThat(matrix.get(11)).contains(-9);
        assertThat(matrix).containsKey(20); // Community management
    }
}
