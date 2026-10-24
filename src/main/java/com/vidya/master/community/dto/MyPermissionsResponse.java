package com.vidya.master.community.dto;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The effective permission map for the currently authenticated community member.
 *
 * <p>Frontend calls {@code GET /api/v1/communities/{id}/my-permissions} at login
 * or page load and uses this map to gate UI elements without hardcoded roles.
 *
 * <p>Example usage (pseudo-JS):
 * <pre>
 * if (permissions[4]?.includes(-9)) showStudentTab();
 * if (permissions[4]?.includes(-8)) enableStudentEdit();
 * </pre>
 *
 * @param memberId           The resolved member ID for this user in this community.
 * @param roleName           Effective role name (for display only).
 * @param permissionsMatrix  Map of sectionCode -> Set of granted actionCodes.
 */
public record MyPermissionsResponse(
        UUID memberId,
        String roleName,
        Map<Integer, Set<Integer>> permissionsMatrix
) {}
