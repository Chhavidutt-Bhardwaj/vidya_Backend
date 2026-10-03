package com.vidya.tenantdata.assignment.entity;

import com.vidya.common.exception.InvalidStateTransitionException;
import com.vidya.tenantdata.assignment.enums.ClassSubjectAssignmentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ClassSubjectAssignmentStatus — State Machine Unit Tests")
class ClassSubjectAssignmentStatusTest {

    @Test
    @DisplayName("ACTIVE can transition to INACTIVE, DELETED")
    void activeTransitions() {
        assertThat(ClassSubjectAssignmentStatus.ACTIVE.canTransitionTo(ClassSubjectAssignmentStatus.INACTIVE)).isTrue();
        assertThat(ClassSubjectAssignmentStatus.ACTIVE.canTransitionTo(ClassSubjectAssignmentStatus.DELETED)).isTrue();

        assertThatCode(() -> ClassSubjectAssignmentStatus.ACTIVE.validateTransitionTo(ClassSubjectAssignmentStatus.INACTIVE))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("INACTIVE can transition to ACTIVE, DELETED")
    void inactiveTransitions() {
        assertThat(ClassSubjectAssignmentStatus.INACTIVE.canTransitionTo(ClassSubjectAssignmentStatus.ACTIVE)).isTrue();
        assertThat(ClassSubjectAssignmentStatus.INACTIVE.canTransitionTo(ClassSubjectAssignmentStatus.DELETED)).isTrue();

        assertThatCode(() -> ClassSubjectAssignmentStatus.INACTIVE.validateTransitionTo(ClassSubjectAssignmentStatus.ACTIVE))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("DELETED is terminal and cannot transition")
    void deletedTransitions() {
        assertThat(ClassSubjectAssignmentStatus.DELETED.canTransitionTo(ClassSubjectAssignmentStatus.ACTIVE)).isFalse();
        assertThat(ClassSubjectAssignmentStatus.DELETED.canTransitionTo(ClassSubjectAssignmentStatus.INACTIVE)).isFalse();

        assertThatThrownBy(() -> ClassSubjectAssignmentStatus.DELETED.validateTransitionTo(ClassSubjectAssignmentStatus.ACTIVE))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("cannot move from [DELETED] to [ACTIVE]");
    }
}
