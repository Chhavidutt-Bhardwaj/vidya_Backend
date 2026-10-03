package com.vidya.tenantdata.attendance.entity;

import com.vidya.common.exception.InvalidStateTransitionException;
import com.vidya.tenantdata.attendance.enums.AttendanceStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AttendanceStatus — State Machine Tests")
class AttendanceStatusTest {

    @Test
    @DisplayName("PRESENT → ABSENT — allowed")
    void present_to_absent_allowed() {
        assertThat(AttendanceStatus.PRESENT.canTransitionTo(AttendanceStatus.ABSENT)).isTrue();
    }

    @Test
    @DisplayName("PRESENT → LATE — allowed")
    void present_to_late_allowed() {
        assertThat(AttendanceStatus.PRESENT.canTransitionTo(AttendanceStatus.LATE)).isTrue();
    }

    @Test
    @DisplayName("PRESENT → HALF_DAY — allowed")
    void present_to_halfDay_allowed() {
        assertThat(AttendanceStatus.PRESENT.canTransitionTo(AttendanceStatus.HALF_DAY)).isTrue();
    }

    @Test
    @DisplayName("PRESENT → EXCUSED — allowed")
    void present_to_excused_allowed() {
        assertThat(AttendanceStatus.PRESENT.canTransitionTo(AttendanceStatus.EXCUSED)).isTrue();
    }

    @Test
    @DisplayName("PRESENT → DELETED — allowed (soft delete)")
    void present_to_deleted_allowed() {
        assertThat(AttendanceStatus.PRESENT.canTransitionTo(AttendanceStatus.DELETED)).isTrue();
    }

    @Test
    @DisplayName("ABSENT → PRESENT — allowed (correction)")
    void absent_to_present_allowed() {
        assertThat(AttendanceStatus.ABSENT.canTransitionTo(AttendanceStatus.PRESENT)).isTrue();
    }

    @Test
    @DisplayName("LATE → PRESENT — allowed (correction)")
    void late_to_present_allowed() {
        assertThat(AttendanceStatus.LATE.canTransitionTo(AttendanceStatus.PRESENT)).isTrue();
    }

    @Test
    @DisplayName("HALF_DAY → EXCUSED — allowed (correction)")
    void halfDay_to_excused_allowed() {
        assertThat(AttendanceStatus.HALF_DAY.canTransitionTo(AttendanceStatus.EXCUSED)).isTrue();
    }

    @Test
    @DisplayName("EXCUSED → ABSENT — allowed (correction)")
    void excused_to_absent_allowed() {
        assertThat(AttendanceStatus.EXCUSED.canTransitionTo(AttendanceStatus.ABSENT)).isTrue();
    }

    @Test
    @DisplayName("DELETED → any — not allowed (terminal state)")
    void deleted_to_any_notAllowed() {
        assertThat(AttendanceStatus.DELETED.canTransitionTo(AttendanceStatus.PRESENT)).isFalse();
        assertThat(AttendanceStatus.DELETED.canTransitionTo(AttendanceStatus.ABSENT)).isFalse();
        assertThat(AttendanceStatus.DELETED.canTransitionTo(AttendanceStatus.LATE)).isFalse();
    }

    @Test
    @DisplayName("DELETED → PRESENT — throws InvalidStateTransitionException")
    void deleted_to_present_throws() {
        assertThatThrownBy(() -> AttendanceStatus.DELETED.validateTransitionTo(AttendanceStatus.PRESENT))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    @DisplayName("Same status transition — allowed (no-op)")
    void same_status_allowed() {
        assertThat(AttendanceStatus.PRESENT.canTransitionTo(AttendanceStatus.PRESENT)).isTrue();
        assertThat(AttendanceStatus.DELETED.canTransitionTo(AttendanceStatus.DELETED)).isTrue();
    }

    @Test
    @DisplayName("isActive — correct for all statuses")
    void isActive_correct() {
        assertThat(AttendanceStatus.PRESENT.isActive()).isTrue();
        assertThat(AttendanceStatus.ABSENT.isActive()).isTrue();
        assertThat(AttendanceStatus.LATE.isActive()).isTrue();
        assertThat(AttendanceStatus.HALF_DAY.isActive()).isTrue();
        assertThat(AttendanceStatus.EXCUSED.isActive()).isTrue();
        assertThat(AttendanceStatus.DELETED.isActive()).isFalse();
    }
}
