package com.vidya.tenantdata.attendance.enums;

import com.vidya.common.exception.InvalidStateTransitionException;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Attendance status for a student on a given attendance date.
 *
 * <p>Status transitions are controlled to maintain audit integrity.
 * Any active status may be corrected to another active status.
 * DELETED is the soft-delete terminal state.
 */
public enum AttendanceStatus {

    PRESENT,
    ABSENT,
    LATE,
    HALF_DAY,
    EXCUSED,
    DELETED;

    /**
     * Any non-deleted status may transition to any other non-deleted status (attendance correction),
     * or to DELETED (soft delete). DELETED is terminal.
     */
    private static final Map<AttendanceStatus, Set<AttendanceStatus>> VALID_TRANSITIONS = Map.of(
            PRESENT,  EnumSet.of(ABSENT, LATE, HALF_DAY, EXCUSED, DELETED),
            ABSENT,   EnumSet.of(PRESENT, LATE, HALF_DAY, EXCUSED, DELETED),
            LATE,     EnumSet.of(PRESENT, ABSENT, HALF_DAY, EXCUSED, DELETED),
            HALF_DAY, EnumSet.of(PRESENT, ABSENT, LATE, EXCUSED, DELETED),
            EXCUSED,  EnumSet.of(PRESENT, ABSENT, LATE, HALF_DAY, DELETED),
            DELETED,  EnumSet.noneOf(AttendanceStatus.class)
    );

    public boolean canTransitionTo(AttendanceStatus target) {
        if (target == null || this == target) {
            return true;
        }
        return VALID_TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }

    public void validateTransitionTo(AttendanceStatus target) {
        if (!canTransitionTo(target)) {
            throw new InvalidStateTransitionException(
                    "Attendance",
                    this.name(),
                    target != null ? target.name() : "null"
            );
        }
    }

    /** Returns true if this status represents an active (non-deleted) attendance record. */
    public boolean isActive() {
        return this != DELETED;
    }
}
