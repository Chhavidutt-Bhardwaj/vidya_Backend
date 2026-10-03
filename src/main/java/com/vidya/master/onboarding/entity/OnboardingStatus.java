package com.vidya.master.onboarding.entity;

/**
 * State machine status for school onboarding workflow.
 */
public enum OnboardingStatus {
    DRAFT,
    SUBMITTED,
    VERIFICATION_PENDING,
    VERIFIED,
    PLAN_SELECTED,
    PAYMENT_PENDING,
    PAYMENT_COMPLETED,
    APPROVAL_PENDING,
    APPROVED,
    PROVISIONING,
    PROVISIONED,
    ACTIVE,
    REJECTED,
    CANCELLED,
    FAILED,
    PROVISIONING_FAILED
}
