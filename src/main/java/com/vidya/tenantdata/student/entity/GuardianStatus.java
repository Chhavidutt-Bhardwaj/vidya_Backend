package com.vidya.tenantdata.student.entity;

import com.vidya.common.exception.BusinessException;

import java.util.EnumSet;
import java.util.Set;

/**
 * Lifecycle status of a student guardian record.
 */
public enum GuardianStatus {
    ACTIVE,
    INACTIVE,
    DELETED;

    public void validateTransitionTo(GuardianStatus target) {
        if (this == target) {
            return;
        }
        Set<GuardianStatus> allowed = switch (this) {
            case ACTIVE -> EnumSet.of(INACTIVE, DELETED);
            case INACTIVE -> EnumSet.of(ACTIVE, DELETED);
            case DELETED -> EnumSet.noneOf(GuardianStatus.class);
        };
        if (!allowed.contains(target)) {
            throw new BusinessException("Invalid guardian status transition from " + this + " to " + target);
        }
    }
}
