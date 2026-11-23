package com.vidya.security.access;

/**
 * Which "world" an account or role belongs to. Drives tenant/school data scoping
 * (who may touch which school's data); permissions decide what they may do there.
 */
public enum AccessScope {
    /** Platform staff (super admin, admin, platform staff). May act across schools. */
    PLATFORM,
    /** School operational users (owner, admin, teachers, staff). Confined to their own school. */
    SCHOOL,
    /** Parents and students. Confined to their own school; no tenant-wide data access yet. */
    SCHOOL_PORTAL,
    /** Community / field-agency users. Access only to schools linked through onboarding. */
    COMMUNITY;

    public boolean isSchoolBound() {
        return this == SCHOOL || this == SCHOOL_PORTAL;
    }
}
