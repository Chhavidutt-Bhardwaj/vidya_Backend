package com.vidya.tenantdata.staff.enums;

import com.vidya.common.exception.InvalidStateTransitionException;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Employment lifecycle status for a Staff member in the tenant database.
 *
 * <p>Valid transitions are enforced at the domain level.
 * Terminal states ({@link #TERMINATED}, {@link #RETIRED}, {@link #DELETED}) do not allow
 * reactivation, preserving the integrity of historical employment records.
 */
public enum EmploymentStatus {

    ACTIVE,
    INACTIVE,
    SUSPENDED,
    RESIGNED,
    TERMINATED,
    RETIRED,
    DELETED;

    private static final Map<EmploymentStatus, Set<EmploymentStatus>> VALID_TRANSITIONS = Map.of(
            ACTIVE,     EnumSet.of(INACTIVE, SUSPENDED, RESIGNED, TERMINATED, RETIRED, DELETED),
            INACTIVE,   EnumSet.of(ACTIVE, DELETED),
            SUSPENDED,  EnumSet.of(ACTIVE, DELETED),
            RESIGNED,   EnumSet.of(DELETED),
            TERMINATED, EnumSet.noneOf(EmploymentStatus.class),
            RETIRED,    EnumSet.noneOf(EmploymentStatus.class),
            DELETED,    EnumSet.noneOf(EmploymentStatus.class)
    );

    public boolean canTransitionTo(EmploymentStatus target) {
        if (target == null || this == target) {
            return true;
        }
        return VALID_TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }

    public void validateTransitionTo(EmploymentStatus target) {
        if (!canTransitionTo(target)) {
            throw new InvalidStateTransitionException(
                    "Staff",
                    this.name(),
                    target != null ? target.name() : "null"
            );
        }
    }
}
