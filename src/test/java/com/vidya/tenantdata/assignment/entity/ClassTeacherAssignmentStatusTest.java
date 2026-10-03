package com.vidya.tenantdata.assignment.entity;

import com.vidya.common.exception.InvalidStateTransitionException;
import com.vidya.tenantdata.assignment.enums.ClassTeacherAssignmentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ClassTeacherAssignmentStatus — State Machine Unit Tests")
class ClassTeacherAssignmentStatusTest {

    @Test
    @DisplayName("ACTIVE can transition to INACTIVE, COMPLETED, CANCELLED, DELETED")
    void activeTransitions() {
        assertThat(ClassTeacherAssignmentStatus.ACTIVE.canTransitionTo(ClassTeacherAssignmentStatus.INACTIVE)).isTrue();
        assertThat(ClassTeacherAssignmentStatus.ACTIVE.canTransitionTo(ClassTeacherAssignmentStatus.COMPLETED)).isTrue();
        assertThat(ClassTeacherAssignmentStatus.ACTIVE.canTransitionTo(ClassTeacherAssignmentStatus.CANCELLED)).isTrue();
        assertThat(ClassTeacherAssignmentStatus.ACTIVE.canTransitionTo(ClassTeacherAssignmentStatus.DELETED)).isTrue();
        assertThat(ClassTeacherAssignmentStatus.ACTIVE.canTransitionTo(ClassTeacherAssignmentStatus.ACTIVE)).isTrue();

        assertThatCode(() -> ClassTeacherAssignmentStatus.ACTIVE.validateTransitionTo(ClassTeacherAssignmentStatus.COMPLETED))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("INACTIVE can transition to ACTIVE, DELETED only")
    void inactiveTransitions() {
        assertThat(ClassTeacherAssignmentStatus.INACTIVE.canTransitionTo(ClassTeacherAssignmentStatus.ACTIVE)).isTrue();
        assertThat(ClassTeacherAssignmentStatus.INACTIVE.canTransitionTo(ClassTeacherAssignmentStatus.DELETED)).isTrue();
        assertThat(ClassTeacherAssignmentStatus.INACTIVE.canTransitionTo(ClassTeacherAssignmentStatus.COMPLETED)).isFalse();
        assertThat(ClassTeacherAssignmentStatus.INACTIVE.canTransitionTo(ClassTeacherAssignmentStatus.CANCELLED)).isFalse();

        assertThatThrownBy(() -> ClassTeacherAssignmentStatus.INACTIVE.validateTransitionTo(ClassTeacherAssignmentStatus.COMPLETED))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("cannot move from [INACTIVE] to [COMPLETED]");
    }

    @Test
    @DisplayName("COMPLETED can only transition to DELETED")
    void completedTransitions() {
        assertThat(ClassTeacherAssignmentStatus.COMPLETED.canTransitionTo(ClassTeacherAssignmentStatus.DELETED)).isTrue();
        assertThat(ClassTeacherAssignmentStatus.COMPLETED.canTransitionTo(ClassTeacherAssignmentStatus.ACTIVE)).isFalse();
        assertThat(ClassTeacherAssignmentStatus.COMPLETED.canTransitionTo(ClassTeacherAssignmentStatus.INACTIVE)).isFalse();
        assertThat(ClassTeacherAssignmentStatus.COMPLETED.canTransitionTo(ClassTeacherAssignmentStatus.CANCELLED)).isFalse();

        assertThatThrownBy(() -> ClassTeacherAssignmentStatus.COMPLETED.validateTransitionTo(ClassTeacherAssignmentStatus.ACTIVE))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("cannot move from [COMPLETED] to [ACTIVE]");
    }

    @Test
    @DisplayName("CANCELLED can only transition to DELETED")
    void cancelledTransitions() {
        assertThat(ClassTeacherAssignmentStatus.CANCELLED.canTransitionTo(ClassTeacherAssignmentStatus.DELETED)).isTrue();
        assertThat(ClassTeacherAssignmentStatus.CANCELLED.canTransitionTo(ClassTeacherAssignmentStatus.ACTIVE)).isFalse();
        assertThat(ClassTeacherAssignmentStatus.CANCELLED.canTransitionTo(ClassTeacherAssignmentStatus.INACTIVE)).isFalse();
        assertThat(ClassTeacherAssignmentStatus.CANCELLED.canTransitionTo(ClassTeacherAssignmentStatus.COMPLETED)).isFalse();

        assertThatThrownBy(() -> ClassTeacherAssignmentStatus.CANCELLED.validateTransitionTo(ClassTeacherAssignmentStatus.ACTIVE))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("cannot move from [CANCELLED] to [ACTIVE]");
    }

    @Test
    @DisplayName("DELETED is terminal and cannot transition to any other status")
    void deletedTerminal() {
        for (ClassTeacherAssignmentStatus target : ClassTeacherAssignmentStatus.values()) {
            if (target != ClassTeacherAssignmentStatus.DELETED) {
                assertThat(ClassTeacherAssignmentStatus.DELETED.canTransitionTo(target)).isFalse();
                assertThatThrownBy(() -> ClassTeacherAssignmentStatus.DELETED.validateTransitionTo(target))
                        .isInstanceOf(InvalidStateTransitionException.class);
            }
        }
    }
}
