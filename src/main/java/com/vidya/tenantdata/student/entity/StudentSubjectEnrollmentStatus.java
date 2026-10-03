package com.vidya.tenantdata.student.entity;

import com.vidya.common.exception.BusinessException;

import java.util.EnumSet;
import java.util.Set;

/**
 * Status of a student's enrollment in an individual subject.
 */
public enum StudentSubjectEnrollmentStatus {

    ACTIVE,
    DROPPED,
    EXEMPTED,
    COMPLETED,
    DELETED;

    public void validateTransitionTo(StudentSubjectEnrollmentStatus target) {
        if (this == target) {
            return;
        }
        Set<StudentSubjectEnrollmentStatus> allowed = switch (this) {
            case ACTIVE -> EnumSet.of(DROPPED, EXEMPTED, COMPLETED, DELETED);
            case DROPPED, EXEMPTED, COMPLETED -> EnumSet.of(ACTIVE, DELETED);
            case DELETED -> EnumSet.noneOf(StudentSubjectEnrollmentStatus.class);
        };
        if (!allowed.contains(target)) {
            throw new BusinessException("Invalid subject enrollment status transition from " + this + " to " + target);
        }
    }

    public boolean canTransitionTo(StudentSubjectEnrollmentStatus target) {
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
}
