package com.vidya.security.access.service;

import com.vidya.security.access.DataDomain;
import com.vidya.security.access.DataScope;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Snapshot of a user's effective access placed in the access token, so the frontend can drive
 * menus, routes, buttons and data views from one place without hardcoding role names.
 *
 * <p><b>The server never authorizes from these claims.</b> Every request re-reads access from
 * the database (cached ≤ 60s). The snapshot is for the UI only; it can be stale until the next
 * refresh, and the {@code X-Access-Version} response header tells the client when it is.
 *
 * <pre>
 * perms    ["STUDENT_READ", ...]   effective permission codes (omitted when su=true)
 * su       true                     super user: every permission
 * roles    ["TEACHER", "HOSTEL_WARDEN"]
 * scope    "SCHOOL"                 account scope (PLATFORM / SCHOOL / SCHOOL_PORTAL / COMMUNITY)
 * features ["ATTENDANCE", ...]      modules enabled for the school
 * ds       {"STUDENT":"OWN", ...}   data scope per area (ALL = whole school, OWN = own records)
 * av       "3f9c0a1b2c3d4e5f"       access version; compare with the X-Access-Version header
 * </pre>
 */
public final class AccessTokenClaims {

    public static final String PERMISSIONS = "perms";
    public static final String SUPER_USER = "su";
    public static final String ROLES = "roles";
    public static final String SCOPE = "scope";
    public static final String FEATURES = "features";
    public static final String DATA_SCOPES = "ds";
    public static final String ACCESS_VERSION = "av";

    private AccessTokenClaims() {
    }

    public static Map<String, Object> of(EffectiveAccess access) {
        Map<String, Object> claims = new LinkedHashMap<>();
        if (access.superUser()) {
            claims.put(SUPER_USER, true);
        } else {
            claims.put(PERMISSIONS, List.copyOf(new TreeSet<>(access.permissions())));
        }
        claims.put(ROLES, List.copyOf(access.roles()));
        claims.put(SCOPE, access.scope() != null ? access.scope().name() : null);
        claims.put(FEATURES, List.copyOf(new TreeSet<>(access.enabledFeatures())));
        Map<String, String> ds = new LinkedHashMap<>();
        for (Map.Entry<DataDomain, DataScope> e : access.dataScopes().entrySet()) {
            ds.put(e.getKey().name(), e.getValue().name());
        }
        claims.put(DATA_SCOPES, ds);
        claims.put(ACCESS_VERSION, access.version());
        return claims;
    }
}
