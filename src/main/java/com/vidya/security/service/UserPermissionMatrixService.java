package com.vidya.security.service;

import com.vidya.security.access.service.EffectiveAccess;
import com.vidya.security.entity.PlatformUser;

import java.util.Map;
import java.util.Set;

/**
 * Builds the frontend {@code permissionsMatrix} (section code → action codes) from the user's
 * DB-resolved effective permissions. Each permission maps to matrix cells through
 * {@code access_permission_matrix_cell}, so the matrix always agrees with what the API allows.
 *
 * @deprecated for new UI code: check the {@code permissions} strings instead. Kept so existing
 *             screens that read the numeric matrix keep working.
 */
public interface UserPermissionMatrixService {

    /** Resolves the user's effective access, then builds the matrix. */
    Map<Integer, Set<Integer>> resolvePermissionsMatrix(PlatformUser user);

    /** Builds the matrix from access that was already resolved (avoids resolving it twice). */
    Map<Integer, Set<Integer>> resolvePermissionsMatrix(PlatformUser user, EffectiveAccess access);
}
