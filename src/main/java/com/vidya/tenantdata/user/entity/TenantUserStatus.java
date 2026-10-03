package com.vidya.tenantdata.user.entity;

import com.vidya.common.exception.InvalidStateTransitionException;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Lifecycle status for a Tenant operational user.
 */
public enum TenantUserStatus {

    ACTIVE,
    INACTIVE,
    SUSPENDED,
    LOCKED,
    DELETED;

    private static final Map<TenantUserStatus, Set<TenantUserStatus>> VALID_TRANSITIONS = Map.of(
            ACTIVE, EnumSet.of(INACTIVE, SUSPENDED, LOCKED, DELETED),
            INACTIVE, EnumSet.of(ACTIVE, DELETED),
            SUSPENDED, EnumSet.of(ACTIVE, DELETED),
            LOCKED, EnumSet.of(ACTIVE, DELETED),
            DELETED, EnumSet.noneOf(TenantUserStatus.class)
    );

    public boolean canTransitionTo(TenantUserStatus target) {
        if (target == null || this == target) {
            return true;
        }
        return VALID_TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }

    public void validateTransitionTo(TenantUserStatus target) {
        if (!canTransitionTo(target)) {
            throw new InvalidStateTransitionException(
                    "TenantUser",
                    this.name(),
                    target != null ? target.name() : "null"
            );
        }
    }
}
