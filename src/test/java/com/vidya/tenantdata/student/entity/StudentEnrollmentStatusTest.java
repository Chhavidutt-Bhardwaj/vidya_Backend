package com.vidya.tenantdata.student.entity;

import com.vidya.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("StudentEnrollmentStatus — State Machine Unit Tests")
class StudentEnrollmentStatusTest {

    @Test
    @DisplayName("ADMITTED can transition to ACTIVE, WITHDRAWN, or DELETED")
    void testAdmittedTransitions() {
        StudentEnrollmentStatus admitted = StudentEnrollmentStatus.ADMITTED;

        assertThat(admitted.canTransitionTo(StudentEnrollmentStatus.ACTIVE)).isTrue();
        assertThat(admitted.canTransitionTo(StudentEnrollmentStatus.WITHDRAWN)).isTrue();
        assertThat(admitted.canTransitionTo(StudentEnrollmentStatus.DELETED)).isTrue();
        assertThat(admitted.canTransitionTo(StudentEnrollmentStatus.PROMOTED)).isFalse();

        assertThatCode(() -> admitted.validateTransitionTo(StudentEnrollmentStatus.ACTIVE)).doesNotThrowAnyException();
        assertThatThrownBy(() -> admitted.validateTransitionTo(StudentEnrollmentStatus.PROMOTED))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("ACTIVE can transition to PROMOTED, TRANSFERRED, WITHDRAWN, COMPLETED, SUSPENDED, DELETED")
    void testActiveTransitions() {
        StudentEnrollmentStatus active = StudentEnrollmentStatus.ACTIVE;

        assertThat(active.canTransitionTo(StudentEnrollmentStatus.PROMOTED)).isTrue();
        assertThat(active.canTransitionTo(StudentEnrollmentStatus.TRANSFERRED)).isTrue();
        assertThat(active.canTransitionTo(StudentEnrollmentStatus.WITHDRAWN)).isTrue();
        assertThat(active.canTransitionTo(StudentEnrollmentStatus.COMPLETED)).isTrue();
        assertThat(active.canTransitionTo(StudentEnrollmentStatus.SUSPENDED)).isTrue();
        assertThat(active.canTransitionTo(StudentEnrollmentStatus.DELETED)).isTrue();

        assertThatCode(() -> active.validateTransitionTo(StudentEnrollmentStatus.PROMOTED)).doesNotThrowAnyException();
        assertThatCode(() -> active.validateTransitionTo(StudentEnrollmentStatus.TRANSFERRED)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("SUSPENDED can transition to ACTIVE, WITHDRAWN, or DELETED")
    void testSuspendedTransitions() {
        StudentEnrollmentStatus suspended = StudentEnrollmentStatus.SUSPENDED;

        assertThat(suspended.canTransitionTo(StudentEnrollmentStatus.ACTIVE)).isTrue();
        assertThat(suspended.canTransitionTo(StudentEnrollmentStatus.WITHDRAWN)).isTrue();
        assertThat(suspended.canTransitionTo(StudentEnrollmentStatus.DELETED)).isTrue();
        assertThat(suspended.canTransitionTo(StudentEnrollmentStatus.PROMOTED)).isFalse();
    }

    @Test
    @DisplayName("Terminal states (PROMOTED, TRANSFERRED, WITHDRAWN, COMPLETED) can only transition to DELETED")
    void testTerminalTransitions() {
        for (StudentEnrollmentStatus terminal : new StudentEnrollmentStatus[]{
                StudentEnrollmentStatus.PROMOTED,
                StudentEnrollmentStatus.TRANSFERRED,
                StudentEnrollmentStatus.WITHDRAWN,
                StudentEnrollmentStatus.COMPLETED
        }) {
            assertThat(terminal.canTransitionTo(StudentEnrollmentStatus.DELETED)).isTrue();
            assertThat(terminal.canTransitionTo(StudentEnrollmentStatus.ACTIVE)).isFalse();

            assertThatThrownBy(() -> terminal.validateTransitionTo(StudentEnrollmentStatus.ACTIVE))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @ParameterizedTest
    @EnumSource(StudentEnrollmentStatus.class)
    @DisplayName("Self-transition is always valid")
    void testSelfTransitionAllowed(StudentEnrollmentStatus status) {
        assertThat(status.canTransitionTo(status)).isTrue();
        assertThatCode(() -> status.validateTransitionTo(status)).doesNotThrowAnyException();
    }
}
