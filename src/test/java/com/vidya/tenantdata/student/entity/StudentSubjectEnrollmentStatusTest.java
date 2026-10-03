package com.vidya.tenantdata.student.entity;

import com.vidya.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("StudentSubjectEnrollmentStatus — State Machine Unit Tests")
class StudentSubjectEnrollmentStatusTest {

    @Test
    @DisplayName("ACTIVE can transition to DROPPED, EXEMPTED, COMPLETED, DELETED")
    void testActiveTransitions() {
        StudentSubjectEnrollmentStatus active = StudentSubjectEnrollmentStatus.ACTIVE;

        assertThat(active.canTransitionTo(StudentSubjectEnrollmentStatus.DROPPED)).isTrue();
        assertThat(active.canTransitionTo(StudentSubjectEnrollmentStatus.EXEMPTED)).isTrue();
        assertThat(active.canTransitionTo(StudentSubjectEnrollmentStatus.COMPLETED)).isTrue();
        assertThat(active.canTransitionTo(StudentSubjectEnrollmentStatus.DELETED)).isTrue();

        assertThatCode(() -> active.validateTransitionTo(StudentSubjectEnrollmentStatus.DROPPED)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("DROPPED, EXEMPTED and COMPLETED can transition to ACTIVE (re-enrolled) or DELETED")
    void testDroppedAndExemptedTransitions() {
        StudentSubjectEnrollmentStatus dropped = StudentSubjectEnrollmentStatus.DROPPED;
        assertThat(dropped.canTransitionTo(StudentSubjectEnrollmentStatus.ACTIVE)).isTrue();
        assertThat(dropped.canTransitionTo(StudentSubjectEnrollmentStatus.DELETED)).isTrue();

        StudentSubjectEnrollmentStatus exempted = StudentSubjectEnrollmentStatus.EXEMPTED;
        assertThat(exempted.canTransitionTo(StudentSubjectEnrollmentStatus.ACTIVE)).isTrue();
        assertThat(exempted.canTransitionTo(StudentSubjectEnrollmentStatus.DELETED)).isTrue();

        StudentSubjectEnrollmentStatus completed = StudentSubjectEnrollmentStatus.COMPLETED;
        assertThat(completed.canTransitionTo(StudentSubjectEnrollmentStatus.ACTIVE)).isTrue();
        assertThat(completed.canTransitionTo(StudentSubjectEnrollmentStatus.DELETED)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(StudentSubjectEnrollmentStatus.class)
    @DisplayName("Self-transition is always permitted")
    void testSelfTransitionAllowed(StudentSubjectEnrollmentStatus status) {
        assertThat(status.canTransitionTo(status)).isTrue();
        assertThatCode(() -> status.validateTransitionTo(status)).doesNotThrowAnyException();
    }
}
