package com.vidya.tenantdata.timetable.enums;

import com.vidya.common.exception.InvalidStateTransitionException;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Lifecycle state machine for master Timetables in the tenant database.
 */
public enum TimetableStatus {
    DRAFT,
    PUBLISHED,
    ACTIVE,
    ARCHIVED,
    DELETED;

    private static final Map<TimetableStatus, Set<TimetableStatus>> VALID_TRANSITIONS = Map.of(
            DRAFT, EnumSet.of(PUBLISHED, DELETED),
            PUBLISHED, EnumSet.of(ACTIVE, DRAFT),
            ACTIVE, EnumSet.of(ARCHIVED),
            ARCHIVED, EnumSet.noneOf(TimetableStatus.class),
            DELETED, EnumSet.noneOf(TimetableStatus.class)
    );

    public boolean canTransitionTo(TimetableStatus target) {
        if (target == null) {
            return false;
        }
        if (this == target) {
            return true;
        }
        return VALID_TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }

    public void validateTransitionTo(TimetableStatus target) {
        if (!canTransitionTo(target)) {
            throw new InvalidStateTransitionException(
                    "Timetable",
                    this.name(),
                    target != null ? target.name() : "null"
            );
        }
    }
}
