package com.vidya.tenantdata.student.entity;

import com.vidya.common.exception.BusinessException;

import java.util.EnumSet;
import java.util.Set;

/**
 * Academic enrollment lifecycle statuses.
 */
public enum StudentEnrollmentStatus {

    ADMITTED,
    ACTIVE,
    PROMOTED,
    TRANSFERRED,
    WITHDRAWN,
    COMPLETED,
    SUSPENDED,
    DELETED;

    public void validateTransitionTo(StudentEnrollmentStatus target) {
        if (this == target) {
            return;
        }
        Set<StudentEnrollmentStatus> allowed = switch (this) {
            case ADMITTED -> EnumSet.of(ACTIVE, WITHDRAWN, CANCELLED_OR_DELETED());
            case ACTIVE -> EnumSet.of(PROMOTED, TRANSFERRED, WITHDRAWN, COMPLETED, SUSPENDED, DELETED);
            case SUSPENDED -> EnumSet.of(ACTIVE, WITHDRAWN, DELETED);
            case PROMOTED, TRANSFERRED, WITHDRAWN, COMPLETED -> EnumSet.of(DELETED);
            case DELETED -> EnumSet.noneOf(StudentEnrollmentStatus.class);
        };
        if (!allowed.contains(target)) {
            throw new BusinessException("Invalid enrollment status transition from " + this + " to " + target);
        }
    }

    private static StudentEnrollmentStatus CANCELLED_OR_DELETED() {
        return DELETED;
    }

    public boolean canTransitionTo(StudentEnrollmentStatus target) {
        if (this == target) {
            return true;
        }
        try {
            validateTransitionTo(target);
            return true;
        } catch (BusinessException e) {
            return false;
        }
    }

    public boolean isActiveOrAdmitted() {
        return this == ACTIVE || this == ADMITTED;
    }
}
