package com.vidya.tenantdata.academic.enums;

import com.vidya.common.exception.InvalidStateTransitionException;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Lifecycle status for an AcademicYear in the tenant database.
 */
public enum AcademicYearStatus {

    DRAFT,
    ACTIVE,
    CLOSED,
    CANCELLED,
    DELETED;

    private static final Map<AcademicYearStatus, Set<AcademicYearStatus>> VALID_TRANSITIONS = Map.of(
            DRAFT, EnumSet.of(ACTIVE, CANCELLED, DELETED),
            ACTIVE, EnumSet.of(CLOSED, DELETED),
            CLOSED, EnumSet.of(DELETED),
            CANCELLED, EnumSet.of(DELETED),
            DELETED, EnumSet.noneOf(AcademicYearStatus.class)
    );

    public boolean canTransitionTo(AcademicYearStatus target) {
        if (target == null || this == target) {
            return true;
        }
        return VALID_TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }

    public void validateTransitionTo(AcademicYearStatus target) {
        if (!canTransitionTo(target)) {
            throw new InvalidStateTransitionException(
                    "AcademicYear",
                    this.name(),
                    target != null ? target.name() : "null"
            );
        }
    }
}
