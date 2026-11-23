package com.vidya.tenantdata.fee.enums;

/** PENDING waits for a checker; APPROVED is applied to the ledger; REVOKED was approved and taken back. */
public enum ConcessionRequestStatus {
    PENDING, APPROVED, REJECTED, REVOKED
}
