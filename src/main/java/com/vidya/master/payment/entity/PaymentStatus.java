package com.vidya.master.payment.entity;

/**
 * State machine status for a payment transaction.
 */
public enum PaymentStatus {
    INITIATED,
    PENDING,
    SUCCESS,
    FAILED,
    REFUNDED,
    CANCELLED
}
