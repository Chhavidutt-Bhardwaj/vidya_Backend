package com.vidya.tenantdata.timetable.enums;

import com.vidya.common.exception.InvalidStateTransitionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TimetableStatus — Lifecycle & State Transition Tests")
class TimetableStatusTest {

    @Test
    @DisplayName("DRAFT can transition to PUBLISHED, DELETED, and self")
    void draftTransitions() {
        assertThat(TimetableStatus.DRAFT.canTransitionTo(TimetableStatus.PUBLISHED)).isTrue();
        assertThat(TimetableStatus.DRAFT.canTransitionTo(TimetableStatus.DELETED)).isTrue();
        assertThat(TimetableStatus.DRAFT.canTransitionTo(TimetableStatus.DRAFT)).isTrue();
        assertThat(TimetableStatus.DRAFT.canTransitionTo(TimetableStatus.ACTIVE)).isFalse();
        assertThat(TimetableStatus.DRAFT.canTransitionTo(TimetableStatus.ARCHIVED)).isFalse();

        assertThatCode(() -> TimetableStatus.DRAFT.validateTransitionTo(TimetableStatus.PUBLISHED)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("PUBLISHED can transition to ACTIVE, DRAFT, and self")
    void publishedTransitions() {
        assertThat(TimetableStatus.PUBLISHED.canTransitionTo(TimetableStatus.ACTIVE)).isTrue();
        assertThat(TimetableStatus.PUBLISHED.canTransitionTo(TimetableStatus.DRAFT)).isTrue();
        assertThat(TimetableStatus.PUBLISHED.canTransitionTo(TimetableStatus.DELETED)).isFalse();
        assertThat(TimetableStatus.PUBLISHED.canTransitionTo(TimetableStatus.PUBLISHED)).isTrue();

        assertThatCode(() -> TimetableStatus.PUBLISHED.validateTransitionTo(TimetableStatus.ACTIVE)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("ACTIVE can transition to ARCHIVED, and self")
    void activeTransitions() {
        assertThat(TimetableStatus.ACTIVE.canTransitionTo(TimetableStatus.ARCHIVED)).isTrue();
        assertThat(TimetableStatus.ACTIVE.canTransitionTo(TimetableStatus.DRAFT)).isFalse();
        assertThat(TimetableStatus.ACTIVE.canTransitionTo(TimetableStatus.DELETED)).isFalse();
        assertThat(TimetableStatus.ACTIVE.canTransitionTo(TimetableStatus.ACTIVE)).isTrue();
        assertThat(TimetableStatus.ACTIVE.canTransitionTo(TimetableStatus.PUBLISHED)).isFalse();

        assertThatCode(() -> TimetableStatus.ACTIVE.validateTransitionTo(TimetableStatus.ARCHIVED)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("ARCHIVED is terminal, only allows self")
    void archivedTransitions() {
        assertThat(TimetableStatus.ARCHIVED.canTransitionTo(TimetableStatus.DELETED)).isFalse();
        assertThat(TimetableStatus.ARCHIVED.canTransitionTo(TimetableStatus.ARCHIVED)).isTrue();
        assertThat(TimetableStatus.ARCHIVED.canTransitionTo(TimetableStatus.DRAFT)).isFalse();
        assertThat(TimetableStatus.ARCHIVED.canTransitionTo(TimetableStatus.PUBLISHED)).isFalse();

        assertThatThrownBy(() -> TimetableStatus.ARCHIVED.validateTransitionTo(TimetableStatus.DELETED))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    @DisplayName("DELETED is terminal")
    void deletedIsTerminal() {
        assertThat(TimetableStatus.DELETED.canTransitionTo(TimetableStatus.DRAFT)).isFalse();
        assertThat(TimetableStatus.DELETED.canTransitionTo(TimetableStatus.PUBLISHED)).isFalse();
        assertThat(TimetableStatus.DELETED.canTransitionTo(TimetableStatus.ACTIVE)).isFalse();

        assertThatThrownBy(() -> TimetableStatus.DELETED.validateTransitionTo(TimetableStatus.ACTIVE))
                .isInstanceOf(InvalidStateTransitionException.class);
    }
}
