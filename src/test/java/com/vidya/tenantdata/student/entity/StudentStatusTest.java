package com.vidya.tenantdata.student.entity;

import com.vidya.common.exception.InvalidStateTransitionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("StudentStatus — State Machine Unit Tests")
class StudentStatusTest {

    @Test
    @DisplayName("ACTIVE status can transition to all operational and exit states")
    void testActiveTransitions() {
        StudentStatus active = StudentStatus.ACTIVE;

        assertThat(active.canTransitionTo(StudentStatus.INACTIVE)).isTrue();
        assertThat(active.canTransitionTo(StudentStatus.SUSPENDED)).isTrue();
        assertThat(active.canTransitionTo(StudentStatus.TRANSFERRED)).isTrue();
        assertThat(active.canTransitionTo(StudentStatus.GRADUATED)).isTrue();
        assertThat(active.canTransitionTo(StudentStatus.WITHDRAWN)).isTrue();
        assertThat(active.canTransitionTo(StudentStatus.DELETED)).isTrue();

        assertThatCode(() -> active.validateTransitionTo(StudentStatus.INACTIVE)).doesNotThrowAnyException();
        assertThatCode(() -> active.validateTransitionTo(StudentStatus.SUSPENDED)).doesNotThrowAnyException();
        assertThatCode(() -> active.validateTransitionTo(StudentStatus.TRANSFERRED)).doesNotThrowAnyException();
        assertThatCode(() -> active.validateTransitionTo(StudentStatus.GRADUATED)).doesNotThrowAnyException();
        assertThatCode(() -> active.validateTransitionTo(StudentStatus.WITHDRAWN)).doesNotThrowAnyException();
        assertThatCode(() -> active.validateTransitionTo(StudentStatus.DELETED)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("INACTIVE status can transition to ACTIVE, WITHDRAWN, or DELETED")
    void testInactiveTransitions() {
        StudentStatus inactive = StudentStatus.INACTIVE;

        assertThat(inactive.canTransitionTo(StudentStatus.ACTIVE)).isTrue();
        assertThat(inactive.canTransitionTo(StudentStatus.WITHDRAWN)).isTrue();
        assertThat(inactive.canTransitionTo(StudentStatus.DELETED)).isTrue();

        assertThat(inactive.canTransitionTo(StudentStatus.GRADUATED)).isFalse();
        assertThat(inactive.canTransitionTo(StudentStatus.TRANSFERRED)).isFalse();

        assertThatThrownBy(() -> inactive.validateTransitionTo(StudentStatus.GRADUATED))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    @DisplayName("SUSPENDED status can transition to ACTIVE, WITHDRAWN, or DELETED")
    void testSuspendedTransitions() {
        StudentStatus suspended = StudentStatus.SUSPENDED;

        assertThat(suspended.canTransitionTo(StudentStatus.ACTIVE)).isTrue();
        assertThat(suspended.canTransitionTo(StudentStatus.WITHDRAWN)).isTrue();
        assertThat(suspended.canTransitionTo(StudentStatus.DELETED)).isTrue();

        assertThat(suspended.canTransitionTo(StudentStatus.GRADUATED)).isFalse();

        assertThatThrownBy(() -> suspended.validateTransitionTo(StudentStatus.GRADUATED))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    @DisplayName("TRANSFERRED and GRADUATED can only transition to DELETED")
    void testTransferredAndGraduatedTransitions() {
        StudentStatus transferred = StudentStatus.TRANSFERRED;
        StudentStatus graduated = StudentStatus.GRADUATED;

        assertThat(transferred.canTransitionTo(StudentStatus.DELETED)).isTrue();
        assertThat(transferred.canTransitionTo(StudentStatus.ACTIVE)).isFalse();

        assertThat(graduated.canTransitionTo(StudentStatus.DELETED)).isTrue();
        assertThat(graduated.canTransitionTo(StudentStatus.ACTIVE)).isFalse();

        assertThatThrownBy(() -> transferred.validateTransitionTo(StudentStatus.ACTIVE))
                .isInstanceOf(InvalidStateTransitionException.class);
        assertThatThrownBy(() -> graduated.validateTransitionTo(StudentStatus.ACTIVE))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    @DisplayName("WITHDRAWN status can transition to ACTIVE (re-enrolled) or DELETED")
    void testWithdrawnTransitions() {
        StudentStatus withdrawn = StudentStatus.WITHDRAWN;

        assertThat(withdrawn.canTransitionTo(StudentStatus.ACTIVE)).isTrue();
        assertThat(withdrawn.canTransitionTo(StudentStatus.DELETED)).isTrue();

        assertThat(withdrawn.canTransitionTo(StudentStatus.SUSPENDED)).isFalse();
        assertThatThrownBy(() -> withdrawn.validateTransitionTo(StudentStatus.SUSPENDED))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @ParameterizedTest
    @EnumSource(value = StudentStatus.class, names = {"ACTIVE", "INACTIVE", "SUSPENDED", "TRANSFERRED", "GRADUATED", "WITHDRAWN"})
    @DisplayName("DELETED is a terminal state — no transitions allowed")
    void testDeletedIsTerminal(StudentStatus nonDeletedStatus) {
        StudentStatus deleted = StudentStatus.DELETED;

        assertThat(deleted.canTransitionTo(nonDeletedStatus)).isFalse();
        assertThatThrownBy(() -> deleted.validateTransitionTo(nonDeletedStatus))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @ParameterizedTest
    @EnumSource(StudentStatus.class)
    @DisplayName("Self-transition is always permitted as a no-op")
    void testSelfTransitionAllowed(StudentStatus status) {
        assertThat(status.canTransitionTo(status)).isTrue();
        assertThatCode(() -> status.validateTransitionTo(status)).doesNotThrowAnyException();
    }
}
