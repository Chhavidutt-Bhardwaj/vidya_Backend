package com.vidya.tenantdata.student.entity;

import com.vidya.common.exception.InvalidStateTransitionException;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Lifecycle status for a Student in the tenant database.
 */
public enum StudentStatus {

    ACTIVE,
    INACTIVE,
    SUSPENDED,
    TRANSFERRED,
    GRADUATED,
    WITHDRAWN,
    DELETED;

    private static final Map<StudentStatus, Set<StudentStatus>> VALID_TRANSITIONS = Map.of(
            ACTIVE, EnumSet.of(INACTIVE, SUSPENDED, TRANSFERRED, GRADUATED, WITHDRAWN, DELETED),
            INACTIVE, EnumSet.of(ACTIVE, WITHDRAWN, DELETED),
            SUSPENDED, EnumSet.of(ACTIVE, WITHDRAWN, DELETED),
            TRANSFERRED, EnumSet.of(DELETED),
            GRADUATED, EnumSet.of(DELETED),
            WITHDRAWN, EnumSet.of(ACTIVE, DELETED),
            DELETED, EnumSet.noneOf(StudentStatus.class)
    );

    public boolean canTransitionTo(StudentStatus target) {
        if (target == null || this == target) {
            return true;
        }
        return VALID_TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }

    public void validateTransitionTo(StudentStatus target) {
        if (!canTransitionTo(target)) {
            throw new InvalidStateTransitionException(
                    "Student",
                    this.name(),
                    target != null ? target.name() : "null"
            );
        }
    }
}
