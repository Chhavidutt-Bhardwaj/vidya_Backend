package com.vidya.master.school.entity;

/**
 * Strategy or origin by which a School is assigned to a Relationship Manager (Community).
 */
public enum RmAssignmentType {
    /** Assigned automatically to the community that onboarded the school. */
    ONBOARDED_BY_COMMUNITY,

    /** Assigned dynamically based on nearest geographic proximity (state/city/hub). */
    NEAREST_GEO_ALLOCATION,

    /** Assigned or overridden manually by a Platform Admin. */
    MANUAL_PLATFORM_OVERRIDE
}
