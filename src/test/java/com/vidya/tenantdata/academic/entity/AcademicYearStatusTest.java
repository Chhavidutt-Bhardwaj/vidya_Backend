package com.vidya.tenantdata.academic.entity;

import com.vidya.common.exception.InvalidStateTransitionException;
import com.vidya.tenantdata.academic.enums.AcademicYearStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AcademicYearStatus — State Machine Unit Tests")
class AcademicYearStatusTest {

    @Test
    @DisplayName("DRAFT status can transition to ACTIVE, CANCELLED, or DELETED")
    void testDraftTransitions() {
        AcademicYearStatus draft = AcademicYearStatus.DRAFT;

        assertThat(draft.canTransitionTo(AcademicYearStatus.ACTIVE)).isTrue();
        assertThat(draft.canTransitionTo(AcademicYearStatus.CANCELLED)).isTrue();
        assertThat(draft.canTransitionTo(AcademicYearStatus.DELETED)).isTrue();
        assertThat(draft.canTransitionTo(AcademicYearStatus.CLOSED)).isFalse();

        assertThatCode(() -> draft.validateTransitionTo(AcademicYearStatus.ACTIVE)).doesNotThrowAnyException();
        assertThatThrownBy(() -> draft.validateTransitionTo(AcademicYearStatus.CLOSED))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    @DisplayName("ACTIVE status can transition to CLOSED or DELETED")
    void testActiveTransitions() {
        AcademicYearStatus active = AcademicYearStatus.ACTIVE;

        assertThat(active.canTransitionTo(AcademicYearStatus.CLOSED)).isTrue();
        assertThat(active.canTransitionTo(AcademicYearStatus.DELETED)).isTrue();
        assertThat(active.canTransitionTo(AcademicYearStatus.DRAFT)).isFalse();
        assertThat(active.canTransitionTo(AcademicYearStatus.CANCELLED)).isFalse();

        assertThatCode(() -> active.validateTransitionTo(AcademicYearStatus.CLOSED)).doesNotThrowAnyException();
        assertThatThrownBy(() -> active.validateTransitionTo(AcademicYearStatus.DRAFT))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    @DisplayName("CLOSED and CANCELLED statuses can only transition to DELETED")
    void testClosedAndCancelledTransitions() {
        AcademicYearStatus closed = AcademicYearStatus.CLOSED;
        AcademicYearStatus cancelled = AcademicYearStatus.CANCELLED;

        assertThat(closed.canTransitionTo(AcademicYearStatus.DELETED)).isTrue();
        assertThat(closed.canTransitionTo(AcademicYearStatus.ACTIVE)).isFalse();

        assertThat(cancelled.canTransitionTo(AcademicYearStatus.DELETED)).isTrue();
        assertThat(cancelled.canTransitionTo(AcademicYearStatus.ACTIVE)).isFalse();

        assertThatThrownBy(() -> closed.validateTransitionTo(AcademicYearStatus.ACTIVE))
                .isInstanceOf(InvalidStateTransitionException.class);
        assertThatThrownBy(() -> cancelled.validateTransitionTo(AcademicYearStatus.ACTIVE))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @ParameterizedTest
    @EnumSource(value = AcademicYearStatus.class, names = {"DRAFT", "ACTIVE", "CLOSED", "CANCELLED"})
    @DisplayName("DELETED is a terminal state")
    void testDeletedIsTerminal(AcademicYearStatus status) {
        AcademicYearStatus deleted = AcademicYearStatus.DELETED;
        assertThat(deleted.canTransitionTo(status)).isFalse();
        assertThatThrownBy(() -> deleted.validateTransitionTo(status))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @ParameterizedTest
    @EnumSource(AcademicYearStatus.class)
    @DisplayName("Self-transition is always permitted")
    void testSelfTransitionAllowed(AcademicYearStatus status) {
        assertThat(status.canTransitionTo(status)).isTrue();
    }
}
