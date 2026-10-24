package com.vidya.master.support.entity;

/**
 * Resolution lifecycle state of a school query.
 */
public enum QueryStatus {
    OPEN,
    IN_PROGRESS,
    ESCALATED_TO_HUB,
    ESCALATED_TO_PLATFORM,
    RESOLVED,
    CLOSED,
    REJECTED
}
