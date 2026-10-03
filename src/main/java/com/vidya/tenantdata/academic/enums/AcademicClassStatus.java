package com.vidya.tenantdata.academic.enums;

import com.vidya.common.exception.InvalidStateTransitionException;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Lifecycle status for an AcademicClass in the tenant database.
 */
public enum AcademicClassStatus {

    ACTIVE,
    INACTIVE,
    DELETED;

    private static final Map<AcademicClassStatus, Set<AcademicClassStatus>> VALID_TRANSITIONS = Map.of(
            ACTIVE, EnumSet.of(INACTIVE, DELETED),
            INACTIVE, EnumSet.of(ACTIVE, DELETED),
            DELETED, EnumSet.noneOf(AcademicClassStatus.class)
    );

    public boolean canTransitionTo(AcademicClassStatus target) {
        if (target == null || this == target) {
            return true;
        }
        return VALID_TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }

    public void validateTransitionTo(AcademicClassStatus target) {
        if (!canTransitionTo(target)) {
            throw new InvalidStateTransitionException(
                    "AcademicClass",
                    this.name(),
                    target != null ? target.name() : "null"
            );
        }
    }
}
