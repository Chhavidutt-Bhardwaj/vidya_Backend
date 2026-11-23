package com.vidya.tenantdata.engagement.enums;

/** Delivery state of one outbox message. SKIPPED = the channel is off or has no provider. */
public enum OutboxStatus {
    PENDING, SENT, FAILED, SKIPPED
}
