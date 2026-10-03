package com.vidya.tenantdata.academic.enums;

import com.vidya.common.exception.InvalidStateTransitionException;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Lifecycle status for a Section in the tenant database.
 */
public enum SectionStatus {

    ACTIVE,
    INACTIVE,
    DELETED;

    private static final Map<SectionStatus, Set<SectionStatus>> VALID_TRANSITIONS = Map.of(
            ACTIVE, EnumSet.of(INACTIVE, DELETED),
            INACTIVE, EnumSet.of(ACTIVE, DELETED),
            DELETED, EnumSet.noneOf(SectionStatus.class)
    );

    public boolean canTransitionTo(SectionStatus target) {
        if (target == null || this == target) {
            return true;
        }
        return VALID_TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }

    public void validateTransitionTo(SectionStatus target) {
        if (!canTransitionTo(target)) {
            throw new InvalidStateTransitionException(
                    "Section",
                    this.name(),
                    target != null ? target.name() : "null"
            );
        }
    }
}
