package com.vidya.tenantdata.academic.enums;

import com.vidya.common.exception.InvalidStateTransitionException;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Status of a student's academic placement.
 */
public enum StudentAssignmentStatus {

    ACTIVE,
    COMPLETED,
    TRANSFERRED,
    CANCELLED,
    DELETED;

    private static final Map<StudentAssignmentStatus, Set<StudentAssignmentStatus>> VALID_TRANSITIONS = Map.of(
            ACTIVE, EnumSet.of(COMPLETED, TRANSFERRED, CANCELLED, DELETED),
            COMPLETED, EnumSet.of(DELETED),
            TRANSFERRED, EnumSet.of(DELETED),
            CANCELLED, EnumSet.of(DELETED),
            DELETED, EnumSet.noneOf(StudentAssignmentStatus.class)
    );

    public boolean canTransitionTo(StudentAssignmentStatus target) {
        if (target == null || this == target) {
            return true;
        }
        return VALID_TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }

    public void validateTransitionTo(StudentAssignmentStatus target) {
        if (!canTransitionTo(target)) {
            throw new InvalidStateTransitionException(
                    "StudentAcademicAssignment",
                    this.name(),
                    target != null ? target.name() : "null"
            );
        }
    }
}
