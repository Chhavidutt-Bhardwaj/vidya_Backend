package com.vidya.tenantdata.operations.enums;

/** How the late fine is worked out: a fixed amount, an amount per day late, or a percent of the overdue amount. */
public enum LateFineType {
    NONE, FIXED, PER_DAY, PERCENT
}
