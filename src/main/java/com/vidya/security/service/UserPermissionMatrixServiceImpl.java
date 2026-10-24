package com.vidya.security.service;

import com.vidya.master.community.entity.CommunityMember;
import com.vidya.master.community.entity.CommunityRolePermission;
import com.vidya.master.community.repository.CommunityMemberRepository;
import com.vidya.master.subscription.entity.SchoolSubscription;
import com.vidya.master.subscription.entity.SubscriptionPlanFeature;
import com.vidya.master.subscription.repository.SchoolSubscriptionRepository;
import com.vidya.master.subscription.repository.SubscriptionPlanFeatureRepository;
import com.vidya.security.entity.PlatformRole;
import com.vidya.security.entity.PlatformUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Resolves effective permission matrix for any user logging into the platform,
 * intersecting role permissions with tenant/school active subscription entitlements.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserPermissionMatrixServiceImpl implements UserPermissionMatrixService {

    private final SchoolSubscriptionRepository schoolSubscriptionRepository;
    private final SubscriptionPlanFeatureRepository subscriptionPlanFeatureRepository;
    private final CommunityMemberRepository communityMemberRepository;

    // Standard Section / Module Codes
    public static final int SECTION_STUDENTS = 4;
    public static final int SECTION_STAFF = 11;
    public static final int SECTION_ATTENDANCE = 12;
    public static final int SECTION_FEES = 13;
    public static final int SECTION_MARKS = 14;
    public static final int SECTION_TIMETABLE = 15;
    public static final int SECTION_CHAT = 16;
    public static final int SECTION_LIBRARY = 17;
    public static final int SECTION_COMMUNITY = 20;

    // Standard Action Codes
    public static final int ACTION_VIEW = -9;
    public static final int ACTION_EDIT = -8;
    public static final int ACTION_DELETE = -7;
    public static final int ACTION_APPROVE = -6;
    public static final int ACTION_SCOPED = -5;
    public static final int ACTION_FULL = -4;

    @Override
    @Transactional(readOnly = true, transactionManager = "masterTransactionManager")
    public Map<Integer, Set<Integer>> resolvePermissionsMatrix(PlatformUser user) {
        if (user == null || user.getPlatformRole() == null) {
            return Map.of();
        }

        Map<Integer, Set<Integer>> matrix = new HashMap<>();
        PlatformRole role = user.getPlatformRole();

        // 1. Resolve base role permission matrix
        switch (role) {
            case PLATFORM_SUPER_ADMIN:
            case PLATFORM_ADMIN:
                grantFullPlatformAccess(matrix);
                return matrix;

            case SCHOOL_OWNER:
            case SCHOOL_ADMIN:
                grantSchoolAdminAccess(matrix);
                break;

            case PRINCIPAL:
                grantPrincipalAccess(matrix);
                break;

            case COORDINATOR:
                grantCoordinatorAccess(matrix);
                break;

            case TEACHER:
                grantClassTeacherAccess(matrix);
                break;

            case SUBJECT_TEACHER:
                grantSubjectTeacherAccess(matrix);
                break;

            case ACCOUNTS:
                grantAccountsAccess(matrix);
                break;

            case PARENT:
                grantParentAccess(matrix);
                break;

            case STUDENT:
                grantStudentAccess(matrix);
                break;

            case COMMUNITY_ADMIN:
            case COMMUNITY_ROLE_MANAGER:
            case COMMUNITY_MANAGER:
            case FIELD_AGENT:
                grantCommunityAccess(user.getId(), matrix);
                break;

            default:
                grantBasicAccess(matrix);
                break;
        }

        // 2. Filter by School / Tenant Subscription Entitlement if user is scoped to a school
        if (user.getSchoolId() != null) {
            filterBySchoolSubscription(user.getSchoolId(), matrix);
        }

        return matrix;
    }

    private void grantFullPlatformAccess(Map<Integer, Set<Integer>> matrix) {
        Set<Integer> fullActions = Set.of(
                ACTION_VIEW, ACTION_EDIT, ACTION_DELETE, ACTION_APPROVE, ACTION_SCOPED, ACTION_FULL
        );
        for (int section = 1; section <= 25; section++) {
            matrix.put(section, new HashSet<>(fullActions));
        }
    }

    private void grantSchoolAdminAccess(Map<Integer, Set<Integer>> matrix) {
        matrix.put(SECTION_STUDENTS, new HashSet<>(Set.of(ACTION_VIEW, ACTION_EDIT, ACTION_DELETE, ACTION_APPROVE, ACTION_FULL)));
        matrix.put(SECTION_STAFF, new HashSet<>(Set.of(ACTION_VIEW, ACTION_EDIT, ACTION_DELETE, ACTION_APPROVE, ACTION_FULL)));
        matrix.put(SECTION_ATTENDANCE, new HashSet<>(Set.of(ACTION_VIEW, ACTION_EDIT, ACTION_DELETE, ACTION_APPROVE, ACTION_FULL)));
        matrix.put(SECTION_FEES, new HashSet<>(Set.of(ACTION_VIEW)));
        matrix.put(SECTION_MARKS, new HashSet<>(Set.of(ACTION_VIEW)));
        matrix.put(SECTION_TIMETABLE, new HashSet<>(Set.of(ACTION_VIEW, ACTION_EDIT, ACTION_DELETE, ACTION_APPROVE, ACTION_FULL)));
        matrix.put(SECTION_CHAT, new HashSet<>(Set.of(ACTION_VIEW)));
        matrix.put(SECTION_LIBRARY, new HashSet<>(Set.of(ACTION_VIEW)));
    }

    private void grantPrincipalAccess(Map<Integer, Set<Integer>> matrix) {
        matrix.put(SECTION_STUDENTS, new HashSet<>(Set.of(ACTION_VIEW, ACTION_EDIT, ACTION_DELETE, ACTION_APPROVE, ACTION_FULL)));
        matrix.put(SECTION_STAFF, new HashSet<>(Set.of(ACTION_VIEW, ACTION_EDIT, ACTION_DELETE, ACTION_APPROVE, ACTION_FULL)));
        matrix.put(SECTION_ATTENDANCE, new HashSet<>(Set.of(ACTION_VIEW)));
        matrix.put(SECTION_FEES, new HashSet<>(Set.of(ACTION_VIEW, ACTION_APPROVE)));
        matrix.put(SECTION_MARKS, new HashSet<>(Set.of(ACTION_VIEW, ACTION_APPROVE)));
        matrix.put(SECTION_TIMETABLE, new HashSet<>(Set.of(ACTION_VIEW, ACTION_EDIT, ACTION_DELETE, ACTION_APPROVE, ACTION_FULL)));
        matrix.put(SECTION_CHAT, new HashSet<>(Set.of(ACTION_VIEW)));
        matrix.put(SECTION_LIBRARY, new HashSet<>(Set.of(ACTION_VIEW)));
    }

    private void grantCoordinatorAccess(Map<Integer, Set<Integer>> matrix) {
        matrix.put(SECTION_STUDENTS, new HashSet<>(Set.of(ACTION_VIEW, ACTION_EDIT)));
        matrix.put(SECTION_STAFF, new HashSet<>(Set.of(ACTION_VIEW)));
        matrix.put(SECTION_ATTENDANCE, new HashSet<>(Set.of(ACTION_VIEW, ACTION_APPROVE)));
        matrix.put(SECTION_MARKS, new HashSet<>(Set.of(ACTION_VIEW, ACTION_APPROVE)));
        matrix.put(SECTION_TIMETABLE, new HashSet<>(Set.of(ACTION_VIEW, ACTION_EDIT)));
        matrix.put(SECTION_CHAT, new HashSet<>(Set.of(ACTION_VIEW)));
    }

    private void grantClassTeacherAccess(Map<Integer, Set<Integer>> matrix) {
        matrix.put(SECTION_STUDENTS, new HashSet<>(Set.of(ACTION_VIEW, ACTION_SCOPED)));
        matrix.put(SECTION_STAFF, new HashSet<>(Set.of(ACTION_VIEW)));
        matrix.put(SECTION_ATTENDANCE, new HashSet<>(Set.of(ACTION_VIEW, ACTION_EDIT, ACTION_SCOPED)));
        matrix.put(SECTION_MARKS, new HashSet<>(Set.of(ACTION_VIEW, ACTION_EDIT, ACTION_SCOPED)));
        matrix.put(SECTION_TIMETABLE, new HashSet<>(Set.of(ACTION_VIEW, ACTION_SCOPED)));
        matrix.put(SECTION_CHAT, new HashSet<>(Set.of(ACTION_VIEW, ACTION_EDIT, ACTION_SCOPED)));
    }

    private void grantSubjectTeacherAccess(Map<Integer, Set<Integer>> matrix) {
        matrix.put(SECTION_STUDENTS, new HashSet<>(Set.of(ACTION_VIEW, ACTION_SCOPED)));
        matrix.put(SECTION_ATTENDANCE, new HashSet<>(Set.of(ACTION_VIEW, ACTION_EDIT, ACTION_SCOPED)));
        matrix.put(SECTION_MARKS, new HashSet<>(Set.of(ACTION_VIEW, ACTION_EDIT, ACTION_SCOPED)));
        matrix.put(SECTION_TIMETABLE, new HashSet<>(Set.of(ACTION_VIEW, ACTION_SCOPED)));
    }

    private void grantAccountsAccess(Map<Integer, Set<Integer>> matrix) {
        matrix.put(SECTION_STUDENTS, new HashSet<>(Set.of(ACTION_VIEW)));
        matrix.put(SECTION_FEES, new HashSet<>(Set.of(ACTION_VIEW, ACTION_EDIT, ACTION_FULL)));
        matrix.put(SECTION_LIBRARY, new HashSet<>(Set.of(ACTION_VIEW, ACTION_EDIT)));
    }

    private void grantParentAccess(Map<Integer, Set<Integer>> matrix) {
        matrix.put(SECTION_STUDENTS, new HashSet<>(Set.of(ACTION_VIEW, ACTION_SCOPED)));
        matrix.put(SECTION_ATTENDANCE, new HashSet<>(Set.of(ACTION_VIEW, ACTION_SCOPED)));
        matrix.put(SECTION_FEES, new HashSet<>(Set.of(ACTION_VIEW, ACTION_EDIT)));
        matrix.put(SECTION_MARKS, new HashSet<>(Set.of(ACTION_VIEW)));
        matrix.put(SECTION_TIMETABLE, new HashSet<>(Set.of(ACTION_VIEW)));
        matrix.put(SECTION_CHAT, new HashSet<>(Set.of(ACTION_VIEW, ACTION_EDIT, ACTION_SCOPED)));
    }

    private void grantStudentAccess(Map<Integer, Set<Integer>> matrix) {
        matrix.put(SECTION_STUDENTS, new HashSet<>(Set.of(ACTION_VIEW, ACTION_SCOPED)));
        matrix.put(SECTION_ATTENDANCE, new HashSet<>(Set.of(ACTION_VIEW, ACTION_SCOPED)));
        matrix.put(SECTION_MARKS, new HashSet<>(Set.of(ACTION_VIEW)));
        matrix.put(SECTION_TIMETABLE, new HashSet<>(Set.of(ACTION_VIEW)));
        matrix.put(SECTION_LIBRARY, new HashSet<>(Set.of(ACTION_VIEW)));
    }

    private void grantCommunityAccess(UUID platformUserId, Map<Integer, Set<Integer>> matrix) {
        // Default community permissions
        matrix.put(SECTION_COMMUNITY, new HashSet<>(Set.of(ACTION_VIEW, ACTION_EDIT, ACTION_DELETE)));
        matrix.put(SECTION_STUDENTS, new HashSet<>(Set.of(ACTION_VIEW)));

        // If member has a custom role assignment, incorporate its permissions
        List<CommunityMember> members = communityMemberRepository.findByPlatformUserId(platformUserId);
        for (CommunityMember member : members) {
            if (member.getCustomRole() != null && member.getCustomRole().getPermissions() != null) {
                for (CommunityRolePermission perm : member.getCustomRole().getPermissions()) {
                    if (perm.isGranted()) {
                        matrix.computeIfAbsent(perm.getSectionCode(), k -> new HashSet<>())
                              .add(perm.getActionCode());
                    }
                }
            }
        }
    }

    private void grantBasicAccess(Map<Integer, Set<Integer>> matrix) {
        matrix.put(SECTION_STUDENTS, new HashSet<>(Set.of(ACTION_VIEW)));
    }

    private void filterBySchoolSubscription(UUID schoolId, Map<Integer, Set<Integer>> matrix) {
        List<SchoolSubscription> activeSubscriptions =
                schoolSubscriptionRepository.findActiveSubscriptionsBySchoolId(schoolId);

        if (activeSubscriptions == null || activeSubscriptions.isEmpty()) {
            // No active subscription: retain matrix as-is (e.g. trial/onboarding mode)
            return;
        }

        SchoolSubscription activeSub = activeSubscriptions.get(0);
        if (activeSub.getPlan() == null) {
            return;
        }

        List<SubscriptionPlanFeature> planFeatures =
                subscriptionPlanFeatureRepository.findByPlanId(activeSub.getPlan().getId());

        Set<Integer> subscribedSectionCodes = new HashSet<>();
        for (SubscriptionPlanFeature spf : planFeatures) {
            if (spf.isIncluded() && spf.getFeature() != null) {
                Integer sectionCode = mapFeatureCodeToSectionCode(spf.getFeature().getFeatureCode());
                if (sectionCode != null) {
                    subscribedSectionCodes.add(sectionCode);
                }
            }
        }

        // Retain only section codes subscribed by the school
        if (!subscribedSectionCodes.isEmpty()) {
            matrix.keySet().retainAll(subscribedSectionCodes);
        }
    }

    private Integer mapFeatureCodeToSectionCode(String featureCode) {
        if (featureCode == null) return null;
        return switch (featureCode) {
            case "STUDENT_MANAGEMENT" -> SECTION_STUDENTS;
            case "STAFF_MANAGEMENT" -> SECTION_STAFF;
            case "ATTENDANCE" -> SECTION_ATTENDANCE;
            case "FEES" -> SECTION_FEES;
            case "EXAMINATION", "RESULTS" -> SECTION_MARKS;
            case "TIMETABLE" -> SECTION_TIMETABLE;
            case "LIBRARY" -> SECTION_LIBRARY;
            case "PARENT_PORTAL", "STUDENT_PORTAL", "TEACHER_PORTAL" -> SECTION_CHAT;
            default -> null;
        };
    }
}
