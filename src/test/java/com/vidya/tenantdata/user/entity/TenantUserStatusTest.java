package com.vidya.tenantdata.user.entity;

import com.vidya.common.exception.InvalidStateTransitionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TenantUserStatus — State Transition Unit Tests")
class TenantUserStatusTest {

    @Test
    @DisplayName("ACTIVE can transition to INACTIVE, SUSPENDED, LOCKED, DELETED")
    void testActiveTransitions() {
        assertThat(TenantUserStatus.ACTIVE.canTransitionTo(TenantUserStatus.INACTIVE)).isTrue();
        assertThat(TenantUserStatus.ACTIVE.canTransitionTo(TenantUserStatus.SUSPENDED)).isTrue();
        assertThat(TenantUserStatus.ACTIVE.canTransitionTo(TenantUserStatus.LOCKED)).isTrue();
        assertThat(TenantUserStatus.ACTIVE.canTransitionTo(TenantUserStatus.DELETED)).isTrue();
        assertThat(TenantUserStatus.ACTIVE.canTransitionTo(TenantUserStatus.ACTIVE)).isTrue();
    }

    @Test
    @DisplayName("INACTIVE can transition to ACTIVE and DELETED")
    void testInactiveTransitions() {
        assertThat(TenantUserStatus.INACTIVE.canTransitionTo(TenantUserStatus.ACTIVE)).isTrue();
        assertThat(TenantUserStatus.INACTIVE.canTransitionTo(TenantUserStatus.DELETED)).isTrue();
        assertThat(TenantUserStatus.INACTIVE.canTransitionTo(TenantUserStatus.SUSPENDED)).isFalse();
    }

    @Test
    @DisplayName("SUSPENDED can transition to ACTIVE and DELETED")
    void testSuspendedTransitions() {
        assertThat(TenantUserStatus.SUSPENDED.canTransitionTo(TenantUserStatus.ACTIVE)).isTrue();
        assertThat(TenantUserStatus.SUSPENDED.canTransitionTo(TenantUserStatus.DELETED)).isTrue();
        assertThat(TenantUserStatus.SUSPENDED.canTransitionTo(TenantUserStatus.INACTIVE)).isFalse();
    }

    @Test
    @DisplayName("DELETED is terminal and cannot transition to any state")
    void testDeletedTransitions() {
        assertThat(TenantUserStatus.DELETED.canTransitionTo(TenantUserStatus.ACTIVE)).isFalse();
        assertThat(TenantUserStatus.DELETED.canTransitionTo(TenantUserStatus.INACTIVE)).isFalse();
        assertThat(TenantUserStatus.DELETED.canTransitionTo(TenantUserStatus.SUSPENDED)).isFalse();

        assertThatThrownBy(() -> TenantUserStatus.DELETED.validateTransitionTo(TenantUserStatus.ACTIVE))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("TenantUser");
    }
}
