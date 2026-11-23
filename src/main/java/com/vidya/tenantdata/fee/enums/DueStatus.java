package com.vidya.tenantdata.fee.enums;

/** Derived, never stored: PAID nothing left; OVERDUE something past its due date; PARTIAL something paid; UPCOMING nothing paid, nothing late. */
public enum DueStatus {
    PAID, PARTIAL, OVERDUE, UPCOMING
}
