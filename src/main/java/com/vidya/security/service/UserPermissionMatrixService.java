package com.vidya.security.service;

import com.vidya.security.entity.PlatformUser;

import java.util.Map;
import java.util.Set;

/**
 * Service to resolve the effective module/section code -> action codes matrix
 * for any authenticated platform user (SuperUser, School Admin, Teacher, Student,
 * Parent, Accounts, Community Member, etc.), dynamically intersected with active
 * tenant/school subscription entitlements.
 */
public interface UserPermissionMatrixService {

    /**
     * Resolves the complete permission matrix for the user.
     *
     * @param user The authenticated platform user
     * @return Map of sectionCode -> Set of granted actionCodes
     */
    Map<Integer, Set<Integer>> resolvePermissionsMatrix(PlatformUser user);
}
