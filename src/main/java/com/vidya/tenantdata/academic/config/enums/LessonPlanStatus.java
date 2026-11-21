package com.vidya.tenantdata.academic.config.enums;

/**
 * DRAFT → SUBMITTED → APPROVED → DELIVERED, with RETURNED (back to the teacher) from SUBMITTED.
 * Schools that do not review plans go DRAFT → DELIVERED.
 */
public enum LessonPlanStatus {
    DRAFT, SUBMITTED, APPROVED, RETURNED, DELIVERED;

    public boolean editable() {
        return this == DRAFT || this == SUBMITTED || this == RETURNED;
    }
}
