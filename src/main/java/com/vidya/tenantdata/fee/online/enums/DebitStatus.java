package com.vidya.tenantdata.fee.online.enums;

/** One auto-debit: SCHEDULED → SUBMITTING → SUBMITTED (with the bank) → SUCCESS / FAILED (bounced); SKIPPED nothing due; ERROR the gateway refused it. */
public enum DebitStatus {
    SCHEDULED, SUBMITTING, SUBMITTED, SUCCESS, FAILED, SKIPPED, CANCELLED, ERROR;

    public boolean open() {
        return this == SCHEDULED || this == SUBMITTING || this == SUBMITTED;
    }
}
