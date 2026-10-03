package com.vidya.tenantdata.subject.entity;

import com.vidya.common.exception.InvalidStateTransitionException;
import com.vidya.tenantdata.subject.enums.SubjectStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SubjectStatus — State Machine Tests")
class SubjectStatusTest {

    @Test
    @DisplayName("ACTIVE can transition to INACTIVE, DELETED")
    void active_ValidTransitions() {
        assertThat(SubjectStatus.ACTIVE.canTransitionTo(SubjectStatus.INACTIVE)).isTrue();
        assertThat(SubjectStatus.ACTIVE.canTransitionTo(SubjectStatus.DELETED)).isTrue();
    }

    @Test
    @DisplayName("INACTIVE can transition to ACTIVE, DELETED")
    void inactive_ValidTransitions() {
        assertThat(SubjectStatus.INACTIVE.canTransitionTo(SubjectStatus.ACTIVE)).isTrue();
        assertThat(SubjectStatus.INACTIVE.canTransitionTo(SubjectStatus.DELETED)).isTrue();
    }

    @Test
    @DisplayName("DELETED is terminal — cannot transition to ACTIVE or INACTIVE")
    void deleted_IsTerminal() {
        assertThat(SubjectStatus.DELETED.canTransitionTo(SubjectStatus.ACTIVE)).isFalse();
        assertThat(SubjectStatus.DELETED.canTransitionTo(SubjectStatus.INACTIVE)).isFalse();
    }

    @Test
    @DisplayName("Same status transition is always valid (idempotent)")
    void sameStatus_IsIdempotent() {
        for (SubjectStatus status : SubjectStatus.values()) {
            assertThat(status.canTransitionTo(status)).isTrue();
        }
    }

    @Test
    @DisplayName("validateTransitionTo throws InvalidStateTransitionException for invalid transition")
    void validateTransitionTo_ThrowsOnInvalid() {
        assertThatThrownBy(() -> SubjectStatus.DELETED.validateTransitionTo(SubjectStatus.ACTIVE))
                .isInstanceOf(InvalidStateTransitionException.class);

        assertThatThrownBy(() -> SubjectStatus.DELETED.validateTransitionTo(SubjectStatus.INACTIVE))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    @DisplayName("canTransitionTo handles null target by returning true")
    void canTransitionTo_NullTarget() {
        assertThat(SubjectStatus.ACTIVE.canTransitionTo(null)).isTrue();
        assertThat(SubjectStatus.DELETED.canTransitionTo(null)).isTrue();
    }
}
