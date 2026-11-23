package com.vidya.tenantdata.operations.enums;

/** How often a fee head is charged in a year; also how often the school collects fees. */
public enum FeeFrequency {
    ONE_TIME(1), ANNUAL(1), HALF_YEARLY(2), QUARTERLY(4), MONTHLY(12);

    private final int timesPerYear;

    FeeFrequency(int timesPerYear) {
        this.timesPerYear = timesPerYear;
    }

    public int timesPerYear() {
        return timesPerYear;
    }
}
