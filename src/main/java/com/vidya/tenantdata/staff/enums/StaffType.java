package com.vidya.tenantdata.staff.enums;

/**
 * Operational category for a staff member in the tenant school.
 *
 * <p>Use {@link #TEACHER} for instructional staff who will later be linked
 * to subject/class assignments, timetables, and attendance.
 * Other categories represent non-teaching roles.
 */
public enum StaffType {
    TEACHER,
    NON_TEACHING,
    ADMINISTRATIVE,
    SUPPORT,
    OTHER
}
