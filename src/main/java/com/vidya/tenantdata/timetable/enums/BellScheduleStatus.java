package com.vidya.tenantdata.timetable.enums;

import com.vidya.common.exception.InvalidStateTransitionException;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Status of a bell schedule template.
 */
public enum BellScheduleStatus {
    ACTIVE,
    INACTIVE,
    DELETED;

    private static final Map<BellScheduleStatus, Set<BellScheduleStatus>> VALID_TRANSITIONS = Map.of(
            ACTIVE, EnumSet.of(INACTIVE, DELETED),
            INACTIVE, EnumSet.of(ACTIVE, DELETED),
            DELETED, EnumSet.noneOf(BellScheduleStatus.class)
    );

    public boolean canTransitionTo(BellScheduleStatus target) {
        if (target == null || this == target) {
            return true;
        }
        return VALID_TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }

    public void validateTransitionTo(BellScheduleStatus target) {
        if (!canTransitionTo(target)) {
            throw new InvalidStateTransitionException(
                    "BellSchedule",
                    this.name(),
                    target != null ? target.name() : "null"
            );
        }
    }
}
