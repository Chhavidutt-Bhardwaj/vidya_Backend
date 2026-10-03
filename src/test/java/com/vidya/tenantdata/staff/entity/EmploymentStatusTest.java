package com.vidya.tenantdata.staff.entity;

import com.vidya.common.exception.InvalidStateTransitionException;
import com.vidya.tenantdata.staff.enums.EmploymentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("EmploymentStatus — State Machine Tests")
class EmploymentStatusTest {

    @Test
    @DisplayName("ACTIVE can transition to INACTIVE, SUSPENDED, RESIGNED, TERMINATED, RETIRED, DELETED")
    void active_ValidTransitions() {
        assertThat(EmploymentStatus.ACTIVE.canTransitionTo(EmploymentStatus.INACTIVE)).isTrue();
        assertThat(EmploymentStatus.ACTIVE.canTransitionTo(EmploymentStatus.SUSPENDED)).isTrue();
        assertThat(EmploymentStatus.ACTIVE.canTransitionTo(EmploymentStatus.RESIGNED)).isTrue();
        assertThat(EmploymentStatus.ACTIVE.canTransitionTo(EmploymentStatus.TERMINATED)).isTrue();
        assertThat(EmploymentStatus.ACTIVE.canTransitionTo(EmploymentStatus.RETIRED)).isTrue();
        assertThat(EmploymentStatus.ACTIVE.canTransitionTo(EmploymentStatus.DELETED)).isTrue();
    }

    @Test
    @DisplayName("INACTIVE can transition to ACTIVE, DELETED")
    void inactive_ValidTransitions() {
        assertThat(EmploymentStatus.INACTIVE.canTransitionTo(EmploymentStatus.ACTIVE)).isTrue();
        assertThat(EmploymentStatus.INACTIVE.canTransitionTo(EmploymentStatus.DELETED)).isTrue();
    }

    @Test
    @DisplayName("INACTIVE cannot transition to SUSPENDED, RESIGNED, TERMINATED, RETIRED")
    void inactive_InvalidTransitions() {
        assertThat(EmploymentStatus.INACTIVE.canTransitionTo(EmploymentStatus.SUSPENDED)).isFalse();
        assertThat(EmploymentStatus.INACTIVE.canTransitionTo(EmploymentStatus.RESIGNED)).isFalse();
        assertThat(EmploymentStatus.INACTIVE.canTransitionTo(EmploymentStatus.TERMINATED)).isFalse();
        assertThat(EmploymentStatus.INACTIVE.canTransitionTo(EmploymentStatus.RETIRED)).isFalse();
    }

    @Test
    @DisplayName("SUSPENDED can transition to ACTIVE, DELETED")
    void suspended_ValidTransitions() {
        assertThat(EmploymentStatus.SUSPENDED.canTransitionTo(EmploymentStatus.ACTIVE)).isTrue();
        assertThat(EmploymentStatus.SUSPENDED.canTransitionTo(EmploymentStatus.DELETED)).isTrue();
    }

    @Test
    @DisplayName("SUSPENDED cannot transition to INACTIVE, RESIGNED, TERMINATED, RETIRED")
    void suspended_InvalidTransitions() {
        assertThat(EmploymentStatus.SUSPENDED.canTransitionTo(EmploymentStatus.INACTIVE)).isFalse();
        assertThat(EmploymentStatus.SUSPENDED.canTransitionTo(EmploymentStatus.RESIGNED)).isFalse();
        assertThat(EmploymentStatus.SUSPENDED.canTransitionTo(EmploymentStatus.TERMINATED)).isFalse();
        assertThat(EmploymentStatus.SUSPENDED.canTransitionTo(EmploymentStatus.RETIRED)).isFalse();
    }

    @Test
    @DisplayName("RESIGNED can only transition to DELETED")
    void resigned_ValidTransitions() {
        assertThat(EmploymentStatus.RESIGNED.canTransitionTo(EmploymentStatus.DELETED)).isTrue();
    }

    @Test
    @DisplayName("RESIGNED cannot be reactivated")
    void resigned_CannotBeReactivated() {
        assertThat(EmploymentStatus.RESIGNED.canTransitionTo(EmploymentStatus.ACTIVE)).isFalse();
        assertThat(EmploymentStatus.RESIGNED.canTransitionTo(EmploymentStatus.INACTIVE)).isFalse();
        assertThat(EmploymentStatus.RESIGNED.canTransitionTo(EmploymentStatus.SUSPENDED)).isFalse();
    }

    @Test
    @DisplayName("TERMINATED is terminal — no valid transitions")
    void terminated_IsTerminal() {
        for (EmploymentStatus target : EmploymentStatus.values()) {
            if (target == EmploymentStatus.TERMINATED) continue; // same state is always allowed
            assertThat(EmploymentStatus.TERMINATED.canTransitionTo(target))
                    .as("TERMINATED -> %s should be blocked", target)
                    .isFalse();
        }
    }

    @Test
    @DisplayName("RETIRED is terminal — no valid transitions")
    void retired_IsTerminal() {
        for (EmploymentStatus target : EmploymentStatus.values()) {
            if (target == EmploymentStatus.RETIRED) continue;
            assertThat(EmploymentStatus.RETIRED.canTransitionTo(target))
                    .as("RETIRED -> %s should be blocked", target)
                    .isFalse();
        }
    }

    @Test
    @DisplayName("DELETED is terminal — no valid transitions")
    void deleted_IsTerminal() {
        for (EmploymentStatus target : EmploymentStatus.values()) {
            if (target == EmploymentStatus.DELETED) continue;
            assertThat(EmploymentStatus.DELETED.canTransitionTo(target))
                    .as("DELETED -> %s should be blocked", target)
                    .isFalse();
        }
    }

    @Test
    @DisplayName("Same status transition is always valid (idempotent)")
    void sameStatus_IsIdempotent() {
        for (EmploymentStatus status : EmploymentStatus.values()) {
            assertThat(status.canTransitionTo(status))
                    .as("Same status %s -> %s should be idempotent", status, status)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("validateTransitionTo throws InvalidStateTransitionException for invalid transition")
    void validateTransitionTo_ThrowsOnInvalid() {
        assertThatThrownBy(() -> EmploymentStatus.TERMINATED.validateTransitionTo(EmploymentStatus.ACTIVE))
                .isInstanceOf(InvalidStateTransitionException.class);

        assertThatThrownBy(() -> EmploymentStatus.RETIRED.validateTransitionTo(EmploymentStatus.ACTIVE))
                .isInstanceOf(InvalidStateTransitionException.class);

        assertThatThrownBy(() -> EmploymentStatus.DELETED.validateTransitionTo(EmploymentStatus.INACTIVE))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    @DisplayName("canTransitionTo handles null target by returning true (treated as no-op)")
    void canTransitionTo_NullTarget() {
        // Consistent with TenantUserStatus and StudentStatus: null target is treated as "no change"
        assertThat(EmploymentStatus.ACTIVE.canTransitionTo(null)).isTrue();
        assertThat(EmploymentStatus.TERMINATED.canTransitionTo(null)).isTrue();
    }
}
