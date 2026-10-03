package com.vidya.tenantdata.assignment.entity;

import com.vidya.common.exception.InvalidStateTransitionException;
import com.vidya.tenantdata.assignment.enums.TeacherAssignmentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TeacherAssignmentStatus — State Machine Unit Tests")
class TeacherAssignmentStatusTest {

    @Test
    @DisplayName("DRAFT can transition to ACTIVE, CANCELLED, DELETED")
    void draftTransitions() {
        assertThat(TeacherAssignmentStatus.DRAFT.canTransitionTo(TeacherAssignmentStatus.ACTIVE)).isTrue();
        assertThat(TeacherAssignmentStatus.DRAFT.canTransitionTo(TeacherAssignmentStatus.CANCELLED)).isTrue();
        assertThat(TeacherAssignmentStatus.DRAFT.canTransitionTo(TeacherAssignmentStatus.DELETED)).isTrue();
        assertThat(TeacherAssignmentStatus.DRAFT.canTransitionTo(TeacherAssignmentStatus.COMPLETED)).isFalse();
        assertThat(TeacherAssignmentStatus.DRAFT.canTransitionTo(TeacherAssignmentStatus.INACTIVE)).isFalse();

        assertThatCode(() -> TeacherAssignmentStatus.DRAFT.validateTransitionTo(TeacherAssignmentStatus.ACTIVE))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("ACTIVE can transition to INACTIVE, COMPLETED, CANCELLED, DELETED")
    void activeTransitions() {
        assertThat(TeacherAssignmentStatus.ACTIVE.canTransitionTo(TeacherAssignmentStatus.INACTIVE)).isTrue();
        assertThat(TeacherAssignmentStatus.ACTIVE.canTransitionTo(TeacherAssignmentStatus.COMPLETED)).isTrue();
        assertThat(TeacherAssignmentStatus.ACTIVE.canTransitionTo(TeacherAssignmentStatus.CANCELLED)).isTrue();
        assertThat(TeacherAssignmentStatus.ACTIVE.canTransitionTo(TeacherAssignmentStatus.DELETED)).isTrue();
        assertThat(TeacherAssignmentStatus.ACTIVE.canTransitionTo(TeacherAssignmentStatus.DRAFT)).isFalse();

        assertThatCode(() -> TeacherAssignmentStatus.ACTIVE.validateTransitionTo(TeacherAssignmentStatus.COMPLETED))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("INACTIVE can transition to ACTIVE, COMPLETED, CANCELLED, DELETED")
    void inactiveTransitions() {
        assertThat(TeacherAssignmentStatus.INACTIVE.canTransitionTo(TeacherAssignmentStatus.ACTIVE)).isTrue();
        assertThat(TeacherAssignmentStatus.INACTIVE.canTransitionTo(TeacherAssignmentStatus.COMPLETED)).isTrue();
        assertThat(TeacherAssignmentStatus.INACTIVE.canTransitionTo(TeacherAssignmentStatus.CANCELLED)).isTrue();
        assertThat(TeacherAssignmentStatus.INACTIVE.canTransitionTo(TeacherAssignmentStatus.DELETED)).isTrue();
        assertThat(TeacherAssignmentStatus.INACTIVE.canTransitionTo(TeacherAssignmentStatus.DRAFT)).isFalse();
    }

    @Test
    @DisplayName("DELETED is terminal; COMPLETED and CANCELLED can only transition to DELETED")
    void terminalTransitions() {
        assertThat(TeacherAssignmentStatus.COMPLETED.canTransitionTo(TeacherAssignmentStatus.DELETED)).isTrue();
        assertThat(TeacherAssignmentStatus.COMPLETED.canTransitionTo(TeacherAssignmentStatus.ACTIVE)).isFalse();
        assertThat(TeacherAssignmentStatus.CANCELLED.canTransitionTo(TeacherAssignmentStatus.DELETED)).isTrue();
        assertThat(TeacherAssignmentStatus.CANCELLED.canTransitionTo(TeacherAssignmentStatus.ACTIVE)).isFalse();

        for (TeacherAssignmentStatus target : TeacherAssignmentStatus.values()) {
            if (target != TeacherAssignmentStatus.DELETED) {
                assertThat(TeacherAssignmentStatus.DELETED.canTransitionTo(target)).isFalse();
            }
        }

        assertThatThrownBy(() -> TeacherAssignmentStatus.COMPLETED.validateTransitionTo(TeacherAssignmentStatus.ACTIVE))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("cannot move from [COMPLETED] to [ACTIVE]");
    }
}
