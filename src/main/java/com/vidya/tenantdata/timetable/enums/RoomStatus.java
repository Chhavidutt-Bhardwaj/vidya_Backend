package com.vidya.tenantdata.timetable.enums;

import com.vidya.common.exception.InvalidStateTransitionException;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Operational status of a school facility room.
 */
public enum RoomStatus {
    ACTIVE,
    INACTIVE,
    MAINTENANCE,
    DELETED;

    private static final Map<RoomStatus, Set<RoomStatus>> VALID_TRANSITIONS = Map.of(
            ACTIVE, EnumSet.of(INACTIVE, MAINTENANCE, DELETED),
            INACTIVE, EnumSet.of(ACTIVE, MAINTENANCE, DELETED),
            MAINTENANCE, EnumSet.of(ACTIVE, INACTIVE, DELETED),
            DELETED, EnumSet.noneOf(RoomStatus.class)
    );

    public boolean canTransitionTo(RoomStatus target) {
        if (target == null || this == target) {
            return true;
        }
        return VALID_TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }

    public void validateTransitionTo(RoomStatus target) {
        if (!canTransitionTo(target)) {
            throw new InvalidStateTransitionException(
                    "Room",
                    this.name(),
                    target != null ? target.name() : "null"
            );
        }
    }
}
