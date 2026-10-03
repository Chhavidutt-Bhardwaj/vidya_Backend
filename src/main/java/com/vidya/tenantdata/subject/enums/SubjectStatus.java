package com.vidya.tenantdata.subject.enums;

import com.vidya.common.exception.InvalidStateTransitionException;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Lifecycle status for an academic Subject in the tenant database.
 */
public enum SubjectStatus {

    ACTIVE,
    INACTIVE,
    DELETED;

    private static final Map<SubjectStatus, Set<SubjectStatus>> VALID_TRANSITIONS = Map.of(
            ACTIVE, EnumSet.of(INACTIVE, DELETED),
            INACTIVE, EnumSet.of(ACTIVE, DELETED),
            DELETED, EnumSet.noneOf(SubjectStatus.class)
    );

    public boolean canTransitionTo(SubjectStatus target) {
        if (target == null || this == target) {
            return true;
        }
        return VALID_TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }

    public void validateTransitionTo(SubjectStatus target) {
        if (!canTransitionTo(target)) {
            throw new InvalidStateTransitionException(
                    "Subject",
                    this.name(),
                    target != null ? target.name() : "null"
            );
        }
    }
}
