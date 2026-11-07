package com.vidya.master.onboarding.entity;

import com.vidya.common.exception.InvalidStateTransitionException;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Lifecycle status of a school onboarding application.
 *
 * <p>All status changes must go through {@link #validateTransitionTo(OnboardingStatus)}.
 * The transition table is the single source of truth for the onboarding workflow and
 * enforces the mandatory sequence:
 *
 * <pre>
 * DRAFT → SUBMITTED → VERIFIED → PLAN_SELECTED → PAYMENT_PENDING → PAYMENT_COMPLETED ─┐
 *                                      └──────────(authorized waiver)──► PAYMENT_WAIVED ─┤
 *                                                                          APPROVED ◄────┘
 * APPROVED → PROVISIONING → PROVISIONED → ACTIVE
 *                 └──► PROVISIONING_FAILED → PROVISIONING (retry)
 * </pre>
 *
 * <p>Consequences: verification cannot be skipped (plan selection requires VERIFIED),
 * approval requires a settled or explicitly waived payment, activation requires a
 * completed provisioning, and terminal states (ACTIVE, REJECTED, CANCELLED) cannot be
 * left through the normal workflow.
 */
public enum OnboardingStatus {
    DRAFT,
    SUBMITTED,
    VERIFICATION_PENDING,
    VERIFIED,
    PLAN_SELECTED,
    PAYMENT_PENDING,
    PAYMENT_COMPLETED,
    PAYMENT_WAIVED,
    APPROVAL_PENDING,
    APPROVED,
    PROVISIONING,
    PROVISIONED,
    ACTIVE,
    REJECTED,
    CANCELLED,
    FAILED,
    PROVISIONING_FAILED;

    private static final Map<OnboardingStatus, Set<OnboardingStatus>> VALID_TRANSITIONS =
            new EnumMap<>(OnboardingStatus.class);

    static {
        VALID_TRANSITIONS.put(DRAFT, EnumSet.of(SUBMITTED, REJECTED, CANCELLED));
        VALID_TRANSITIONS.put(SUBMITTED, EnumSet.of(VERIFIED, VERIFICATION_PENDING, REJECTED, CANCELLED));
        VALID_TRANSITIONS.put(VERIFICATION_PENDING, EnumSet.of(VERIFIED, REJECTED, CANCELLED));
        VALID_TRANSITIONS.put(VERIFIED, EnumSet.of(PLAN_SELECTED, REJECTED, CANCELLED));
        VALID_TRANSITIONS.put(PLAN_SELECTED, EnumSet.of(PAYMENT_PENDING, PAYMENT_WAIVED, REJECTED, CANCELLED));
        VALID_TRANSITIONS.put(PAYMENT_PENDING, EnumSet.of(PAYMENT_COMPLETED, PAYMENT_WAIVED, PLAN_SELECTED, REJECTED, CANCELLED));
        VALID_TRANSITIONS.put(PAYMENT_COMPLETED, EnumSet.of(APPROVAL_PENDING, APPROVED, REJECTED));
        VALID_TRANSITIONS.put(PAYMENT_WAIVED, EnumSet.of(APPROVAL_PENDING, APPROVED, REJECTED, CANCELLED));
        VALID_TRANSITIONS.put(APPROVAL_PENDING, EnumSet.of(APPROVED, REJECTED));
        VALID_TRANSITIONS.put(APPROVED, EnumSet.of(PROVISIONING, REJECTED, CANCELLED));
        VALID_TRANSITIONS.put(PROVISIONING, EnumSet.of(PROVISIONED, PROVISIONING_FAILED));
        VALID_TRANSITIONS.put(PROVISIONING_FAILED, EnumSet.of(PROVISIONING, CANCELLED));
        VALID_TRANSITIONS.put(FAILED, EnumSet.of(PROVISIONING, CANCELLED));
        VALID_TRANSITIONS.put(PROVISIONED, EnumSet.of(ACTIVE));
        VALID_TRANSITIONS.put(ACTIVE, EnumSet.noneOf(OnboardingStatus.class));
        VALID_TRANSITIONS.put(REJECTED, EnumSet.noneOf(OnboardingStatus.class));
        VALID_TRANSITIONS.put(CANCELLED, EnumSet.noneOf(OnboardingStatus.class));
    }

    /**
     * Returns true if the onboarding may move from this status to {@code target}.
     * A self-transition is allowed (idempotent re-application of the same step).
     */
    public boolean canTransitionTo(OnboardingStatus target) {
        if (target == null) {
            return false;
        }
        if (this == target) {
            return true;
        }
        return VALID_TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }

    /**
     * @throws InvalidStateTransitionException if the transition is not allowed
     */
    public void validateTransitionTo(OnboardingStatus target) {
        if (!canTransitionTo(target)) {
            throw new InvalidStateTransitionException(
                    "SchoolOnboarding",
                    this.name(),
                    target != null ? target.name() : "null");
        }
    }

    /** Payment has been settled through a verified gateway or confirmed offline, or explicitly waived. */
    public boolean isPaymentSettled() {
        return this == PAYMENT_COMPLETED || this == PAYMENT_WAIVED;
    }

    public boolean isTerminal() {
        return this == ACTIVE || this == REJECTED || this == CANCELLED;
    }
}
