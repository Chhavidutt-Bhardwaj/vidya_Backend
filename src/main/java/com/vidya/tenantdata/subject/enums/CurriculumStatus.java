package com.vidya.tenantdata.subject.enums;

import com.vidya.common.exception.InvalidStateTransitionException;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Lifecycle status for an academic Curriculum in the tenant database.
 */
public enum CurriculumStatus {

    DRAFT,
    ACTIVE,
    ARCHIVED,
    DELETED;

    private static final Map<CurriculumStatus, Set<CurriculumStatus>> VALID_TRANSITIONS = Map.of(
            DRAFT, EnumSet.of(ACTIVE, DELETED),
            ACTIVE, EnumSet.of(ARCHIVED, DELETED),
            ARCHIVED, EnumSet.of(DELETED),
            DELETED, EnumSet.noneOf(CurriculumStatus.class)
    );

    public boolean canTransitionTo(CurriculumStatus target) {
        if (target == null || this == target) {
            return true;
        }
        return VALID_TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }

    public void validateTransitionTo(CurriculumStatus target) {
        if (!canTransitionTo(target)) {
            throw new InvalidStateTransitionException(
                    "Curriculum",
                    this.name(),
                    target != null ? target.name() : "null"
            );
        }
    }
}
