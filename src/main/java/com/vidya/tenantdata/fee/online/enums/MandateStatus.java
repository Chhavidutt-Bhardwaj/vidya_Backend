package com.vidya.tenantdata.fee.online.enums;

/** Mandate life: PENDING (sent to the payer) → ACTIVE (bank approved) → CANCELLED / EXPIRED; FAILED if the payer or bank rejected it. */
public enum MandateStatus {
    PENDING, ACTIVE, PAUSED, CANCELLED, FAILED, EXPIRED;

    /** Pending or active: covers its children (no second mandate for them). */
    public boolean live() {
        return this == PENDING || this == ACTIVE || this == PAUSED;
    }
}
