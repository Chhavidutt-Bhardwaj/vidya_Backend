package com.vidya.tenantdata.assignment.enums;

import com.vidya.common.exception.InvalidStateTransitionException;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Lifecycle status for a TeacherSubjectAssignment in the tenant database.
 */
public enum TeacherAssignmentStatus {

    DRAFT,
    ACTIVE,
    INACTIVE,
    COMPLETED,
    CANCELLED,
    DELETED;

    private static final Map<TeacherAssignmentStatus, Set<TeacherAssignmentStatus>> VALID_TRANSITIONS = Map.of(
            DRAFT, EnumSet.of(ACTIVE, CANCELLED, DELETED),
            ACTIVE, EnumSet.of(INACTIVE, COMPLETED, CANCELLED, DELETED),
            INACTIVE, EnumSet.of(ACTIVE, COMPLETED, CANCELLED, DELETED),
            COMPLETED, EnumSet.of(DELETED),
            CANCELLED, EnumSet.of(DELETED),
            DELETED, EnumSet.noneOf(TeacherAssignmentStatus.class)
    );

    public boolean canTransitionTo(TeacherAssignmentStatus target) {
        if (target == null || this == target) {
            return true;
        }
        return VALID_TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }

    public void validateTransitionTo(TeacherAssignmentStatus target) {
        if (!canTransitionTo(target)) {
            throw new InvalidStateTransitionException(
                    "TeacherSubjectAssignment",
                    this.name(),
                    target != null ? target.name() : "null"
            );
        }
    }
}
