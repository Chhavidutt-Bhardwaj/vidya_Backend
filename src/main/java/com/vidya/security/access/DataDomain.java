package com.vidya.security.access;

/**
 * Areas with data-level (row) access control. For each one a user works on either ALL records
 * of their school or only their OWN (see {@link DataScope}). Which one is decided by the
 * {@code <PREFIX>_SCOPE_ALL} permission, so it is configured per role in the database.
 */
public enum DataDomain {
    STUDENT("STUDENT"),
    ATTENDANCE("ATTENDANCE"),
    MARKS("MARKS"),
    TIMETABLE("TIMETABLE"),
    STAFF("STAFF"),
    FEE("FEE"),
    CHAT("CHAT"),
    /** School user accounts (tenant_user). */
    USER("USER");

    private final String permissionPrefix;

    DataDomain(String permissionPrefix) {
        this.permissionPrefix = permissionPrefix;
    }

    /** Permission that lifts the user from OWN to ALL records, e.g. {@code STUDENT_SCOPE_ALL}. */
    public String scopeAllPermission() {
        return permissionPrefix + "_SCOPE_ALL";
    }
}
