package com.vidya.master.subscription.entity;

/**
 * Lifecycle status of a school's subscription instance.
 */
public enum SubscriptionStatus {
    TRIAL,
    ACTIVE,
    PAYMENT_PENDING,
    PAST_DUE,
    SUSPENDED,
    EXPIRED,
    CANCELLED
}
