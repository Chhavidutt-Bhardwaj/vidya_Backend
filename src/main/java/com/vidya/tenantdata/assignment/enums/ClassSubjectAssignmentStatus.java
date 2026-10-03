package com.vidya.tenantdata.assignment.enums;

import com.vidya.common.exception.InvalidStateTransitionException;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Lifecycle status for a ClassSubjectAssignment in the tenant database.
 */
public enum ClassSubjectAssignmentStatus {

    ACTIVE,
    INACTIVE,
    DELETED;

    private static final Map<ClassSubjectAssignmentStatus, Set<ClassSubjectAssignmentStatus>> VALID_TRANSITIONS = Map.of(
            ACTIVE, EnumSet.of(INACTIVE, DELETED),
            INACTIVE, EnumSet.of(ACTIVE, DELETED),
            DELETED, EnumSet.noneOf(ClassSubjectAssignmentStatus.class)
    );

    public boolean canTransitionTo(ClassSubjectAssignmentStatus target) {
        if (target == null || this == target) {
            return true;
        }
        return VALID_TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }

    public void validateTransitionTo(ClassSubjectAssignmentStatus target) {
        if (!canTransitionTo(target)) {
            throw new InvalidStateTransitionException(
                    "ClassSubjectAssignment",
                    this.name(),
                    target != null ? target.name() : "null"
            );
        }
    }
}
