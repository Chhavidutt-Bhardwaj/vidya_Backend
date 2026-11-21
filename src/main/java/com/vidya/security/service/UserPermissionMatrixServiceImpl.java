package com.vidya.security.service;

import com.vidya.master.community.entity.CommunityMember;
import com.vidya.master.community.entity.CommunityRolePermission;
import com.vidya.master.community.repository.CommunityMemberRepository;
import com.vidya.security.access.AccessScope;
import com.vidya.security.access.repository.AccessPermissionRepository;
import com.vidya.security.access.service.EffectiveAccess;
import com.vidya.security.access.service.EffectiveAccessService;
import com.vidya.security.entity.PlatformUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Matrix = the {@code access_permission_matrix_cell} rows of every permission the user
 * effectively holds. Roles, extra assignments, GRANT/DENY overrides and per-school feature
 * gating are therefore already applied. Community members also get the cells of their
 * custom community role ({@code community_role_permission}), as before.
 *
 * <p>To show a new section or action, add a cell to a permission (SQL or
 * {@code PUT /api/v1/admin/access/permissions/{id}} with {@code matrixCells}); no code change.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserPermissionMatrixServiceImpl implements UserPermissionMatrixService {

    private final EffectiveAccessService effectiveAccessService;
    private final AccessPermissionRepository permissionRepository;
    private final CommunityMemberRepository communityMemberRepository;

    // Section codes used by the seeded matrix cells (V30). New sections are just new numbers in the DB.
    public static final int SECTION_STUDENTS = 4;
    public static final int SECTION_STAFF = 11;
    public static final int SECTION_ATTENDANCE = 12;
    public static final int SECTION_FEES = 13;
    public static final int SECTION_MARKS = 14;
    public static final int SECTION_TIMETABLE = 15;
    public static final int SECTION_CHAT = 16;
    public static final int SECTION_LIBRARY = 17;
    public static final int SECTION_COMMUNITY = 20;

    // Action codes (fixed vocabulary, enforced by a DB check constraint)
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
        return resolvePermissionsMatrix(user, effectiveAccessService.getAccess(user));
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "masterTransactionManager")
    public Map<Integer, Set<Integer>> resolvePermissionsMatrix(PlatformUser user, EffectiveAccess access) {
        if (user == null || access == null) {
            return Map.of();
        }
        Map<Integer, Set<Integer>> matrix = new TreeMap<>();

        if (!access.permissions().isEmpty()) {
            for (Object[] row : permissionRepository.findMatrixCellsByCodes(access.permissions())) {
                add(matrix, (Integer) row[0], (Integer) row[1]);
            }
        }

        if (access.scope() == AccessScope.COMMUNITY) {
            addCommunityRoleCells(user, matrix);
        }
        return matrix;
    }

    /** Custom community roles still store their grants as section/action codes. */
    private void addCommunityRoleCells(PlatformUser user, Map<Integer, Set<Integer>> matrix) {
        for (CommunityMember member : communityMemberRepository.findByPlatformUserId(user.getId())) {
            if (member.getCustomRole() == null || member.getCustomRole().getPermissions() == null) {
                continue;
            }
            for (CommunityRolePermission perm : member.getCustomRole().getPermissions()) {
                if (perm.isGranted()) {
                    add(matrix, perm.getSectionCode(), perm.getActionCode());
                }
            }
        }
    }

    private static void add(Map<Integer, Set<Integer>> matrix, Integer section, Integer action) {
        if (section != null && action != null) {
            matrix.computeIfAbsent(section, k -> new TreeSet<>()).add(action);
        }
    }
}
