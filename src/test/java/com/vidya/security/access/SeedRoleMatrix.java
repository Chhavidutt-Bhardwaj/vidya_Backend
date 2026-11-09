package com.vidya.security.access;

import com.vidya.security.access.service.EffectiveAccess;
import com.vidya.security.entity.PlatformUser;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Test helper: reads the role/permission/matrix seed straight from the V29 and V30 migrations, so tests check
 * the grants the database will actually hold (the enum-based matrix no longer exists).
 */
public final class SeedRoleMatrix {

    private static final List<Path> MIGRATIONS = List.of(
            Path.of("src/main/resources/db/migration/master/V29__dynamic_access_control_sessions_and_login_history.sql"),
            Path.of("src/main/resources/db/migration/master/V30__permission_matrix_cells.sql"),
            Path.of("src/main/resources/db/migration/master/V31__data_scope_grants.sql"),
            Path.of("src/main/resources/db/migration/master/V32__data_scope_complete.sql"),
            Path.of("src/main/resources/db/migration/master/V33__student_promotion_permission.sql"));

    private static final Map<String, String> PERMISSION_SCOPE = new LinkedHashMap<>();
    private static final Map<String, String> PERMISSION_FEATURE = new HashMap<>();
    private static final Map<String, Set<String>> ROLE_GRANTS = new HashMap<>();
    private static final Set<String> GRANTS_ALL_ROLES = new HashSet<>();
    private static final Map<String, Set<int[]>> MATRIX_CELLS = new HashMap<>();

    static {
        for (Path migration : MIGRATIONS) {
            String sql;
            try {
                sql = Files.readString(migration);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            parse(sql);
        }
        if (PERMISSION_SCOPE.isEmpty() || ROLE_GRANTS.isEmpty() || MATRIX_CELLS.isEmpty()) {
            throw new IllegalStateException("Could not parse V29/V30 seed");
        }
    }

    private static void parse(String sql) {
        String permSection = section(sql, "INSERT INTO access_permission (", "ON CONFLICT");
        if (permSection != null) {
            Matcher pm = Pattern.compile("\\('([A-Z0-9_]+)',\\s*'[^']*',\\s*(?:NULL|'(?:[^']|'')*'),\\s*'([A-Z_]+)',\\s*'([A-Z]+)',\\s*(NULL|'[A-Z_]+')\\)")
                    .matcher(permSection);
            while (pm.find()) {
                PERMISSION_SCOPE.put(pm.group(1), pm.group(3));
                if (!"NULL".equals(pm.group(4))) {
                    PERMISSION_FEATURE.put(pm.group(1), pm.group(4).replace("'", ""));
                }
            }
        }
        String roleSection = section(sql, "INSERT INTO access_role (", "ON CONFLICT");
        if (roleSection != null) {
            Matcher rm = Pattern.compile("\\('([A-Z_]+)', '[^']*', (?:NULL|'(?:[^']|'')*'), '[A-Z_]+', NULL, TRUE, (TRUE|FALSE)\\)")
                    .matcher(roleSection);
            while (rm.find()) {
                ROLE_GRANTS.putIfAbsent(rm.group(1), new LinkedHashSet<>());
                if ("TRUE".equals(rm.group(2))) {
                    GRANTS_ALL_ROLES.add(rm.group(1));
                }
            }
        }
        String grantSection = section(sql, "INSERT INTO access_role_permission", "ON CONFLICT");
        if (grantSection != null) {
            Matcher gm = Pattern.compile("\\('([A-Z_]+)',\\s*'([A-Z0-9_]+)'\\)").matcher(grantSection);
            while (gm.find()) {
                ROLE_GRANTS.computeIfAbsent(gm.group(1), k -> new LinkedHashSet<>()).add(gm.group(2));
            }
        }
        String cellSection = section(sql, "INSERT INTO access_permission_matrix_cell", "ON CONFLICT");
        if (cellSection != null) {
            Matcher cm = Pattern.compile("\\('([A-Z0-9_]+)', (\\d+), (-\\d+)\\)").matcher(cellSection);
            while (cm.find()) {
                MATRIX_CELLS.computeIfAbsent(cm.group(1), k -> new java.util.LinkedHashSet<>())
                        .add(new int[]{Integer.parseInt(cm.group(2)), Integer.parseInt(cm.group(3))});
            }
        }
    }

    private SeedRoleMatrix() {
    }

    public static Set<String> allPermissionCodes() {
        return Set.copyOf(PERMISSION_SCOPE.keySet());
    }

    public static Set<String> roleCodes() {
        return Set.copyOf(ROLE_GRANTS.keySet());
    }

    /** Permissions the seeded role holds (all of them for a grants-all role). */
    public static Set<String> permissionsOf(String roleCode) {
        if (GRANTS_ALL_ROLES.contains(roleCode)) {
            return allPermissionCodes();
        }
        Set<String> grants = ROLE_GRANTS.get(roleCode);
        if (grants == null) {
            throw new IllegalArgumentException("Role not seeded: " + roleCode);
        }
        return Set.copyOf(grants);
    }

    public static boolean grantsAll(String roleCode) {
        return GRANTS_ALL_ROLES.contains(roleCode);
    }

    public static String scopeOf(String permissionCode) {
        return PERMISSION_SCOPE.get(permissionCode);
    }

    public static String featureOf(String permissionCode) {
        return PERMISSION_FEATURE.get(permissionCode);
    }

    /** Effective access a user would get from the seed alone (no assignments/overrides, all features enabled). */
    public static EffectiveAccess accessFor(PlatformUser user) {
        String role = user.getPlatformRole().name();
        Set<String> perms = new LinkedHashSet<>(permissionsOf(role));
        AccessScope scope = user.getPlatformRole().getScope();
        if (scope.isSchoolBound()) {
            perms.removeIf(p -> "PLATFORM".equals(PERMISSION_SCOPE.get(p)));
        }
        return new EffectiveAccess(user.getId(), user.getPlatformRole(), scope, user.getSchoolId(),
                Set.of(role), Set.of(role), perms, Set.of(), grantsAll(role), Instant.now());
    }

    /** Matrix cells (as [section, action] rows) of the given permission codes, like the repository query. */
    public static List<Object[]> matrixRows(java.util.Collection<String> permissionCodes) {
        List<Object[]> rows = new java.util.ArrayList<>();
        for (String code : permissionCodes) {
            for (int[] c : MATRIX_CELLS.getOrDefault(code, Set.of())) {
                rows.add(new Object[]{c[0], c[1]});
            }
        }
        return rows;
    }

    private static String section(String sql, String start, String end) {
        int s = sql.indexOf(start);
        if (s < 0) {
            return null;
        }
        int e = sql.indexOf(end, s);
        return e < 0 ? null : sql.substring(s, e);
    }
}
