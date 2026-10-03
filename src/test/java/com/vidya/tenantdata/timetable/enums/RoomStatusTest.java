package com.vidya.tenantdata.timetable.enums;

import com.vidya.common.exception.InvalidStateTransitionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RoomStatus — Lifecycle & State Transition Tests")
class RoomStatusTest {

    @Test
    @DisplayName("ACTIVE can transition to INACTIVE, MAINTENANCE, DELETED and self")
    void activeTransitions() {
        assertThat(RoomStatus.ACTIVE.canTransitionTo(RoomStatus.INACTIVE)).isTrue();
        assertThat(RoomStatus.ACTIVE.canTransitionTo(RoomStatus.MAINTENANCE)).isTrue();
        assertThat(RoomStatus.ACTIVE.canTransitionTo(RoomStatus.DELETED)).isTrue();
        assertThat(RoomStatus.ACTIVE.canTransitionTo(RoomStatus.ACTIVE)).isTrue();

        assertThatCode(() -> RoomStatus.ACTIVE.validateTransitionTo(RoomStatus.INACTIVE)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("MAINTENANCE can transition back to ACTIVE, INACTIVE, DELETED")
    void maintenanceTransitions() {
        assertThat(RoomStatus.MAINTENANCE.canTransitionTo(RoomStatus.ACTIVE)).isTrue();
        assertThat(RoomStatus.MAINTENANCE.canTransitionTo(RoomStatus.INACTIVE)).isTrue();
        assertThat(RoomStatus.MAINTENANCE.canTransitionTo(RoomStatus.DELETED)).isTrue();

        assertThatCode(() -> RoomStatus.MAINTENANCE.validateTransitionTo(RoomStatus.ACTIVE)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("DELETED is terminal and cannot transition to other states")
    void deletedIsTerminal() {
        assertThat(RoomStatus.DELETED.canTransitionTo(RoomStatus.ACTIVE)).isFalse();
        assertThat(RoomStatus.DELETED.canTransitionTo(RoomStatus.INACTIVE)).isFalse();
        assertThat(RoomStatus.DELETED.canTransitionTo(RoomStatus.MAINTENANCE)).isFalse();

        assertThatThrownBy(() -> RoomStatus.DELETED.validateTransitionTo(RoomStatus.ACTIVE))
                .isInstanceOf(InvalidStateTransitionException.class);
    }
}
