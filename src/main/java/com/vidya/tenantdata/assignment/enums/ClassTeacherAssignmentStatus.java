package com.vidya.tenantdata.assignment.enums;

import com.vidya.common.exception.InvalidStateTransitionException;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Lifecycle status for a ClassTeacherAssignment in the tenant database.
 *
 * <p>A class/section teacher is the staff member responsible for a section in an academic year.
 * The state machine enforces controlled transitions; terminal states cannot be reversed.
 */
public enum ClassTeacherAssignmentStatus {

    ACTIVE,
    INACTIVE,
    COMPLETED,
    CANCELLED,
    DELETED;

    private static final Map<ClassTeacherAssignmentStatus, Set<ClassTeacherAssignmentStatus>> VALID_TRANSITIONS = Map.of(
            ACTIVE,    EnumSet.of(INACTIVE, COMPLETED, CANCELLED, DELETED),
            INACTIVE,  EnumSet.of(ACTIVE, DELETED),
            COMPLETED, EnumSet.of(DELETED),
            CANCELLED, EnumSet.of(DELETED),
            DELETED,   EnumSet.noneOf(ClassTeacherAssignmentStatus.class)
    );

    public boolean canTransitionTo(ClassTeacherAssignmentStatus target) {
        if (target == null || this == target) {
            return true;
        }
        return VALID_TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }

    public void validateTransitionTo(ClassTeacherAssignmentStatus target) {
        if (!canTransitionTo(target)) {
            throw new InvalidStateTransitionException(
                    "ClassTeacherAssignment",
                    this.name(),
                    target != null ? target.name() : "null"
            );
        }
    }
}
