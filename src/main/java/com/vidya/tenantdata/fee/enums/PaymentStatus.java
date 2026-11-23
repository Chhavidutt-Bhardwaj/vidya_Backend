package com.vidya.tenantdata.fee.enums;

/** SUCCESS counts as collected; PENDING_CLEARANCE is a cheque not yet cleared (held on the lines); BOUNCED and REVERSED count for nothing. */
public enum PaymentStatus {
    SUCCESS, PENDING_CLEARANCE, BOUNCED, REVERSED
}
