package com.vidya.tenantdata.assessment.enums;

/** ADD_FLAT +n marks; ADD_PERCENT_OF_MAX +n% of max; SCALE_FACTOR marks x n; RAISE_TO_PASS_WITHIN lifts to pass marks if short by at most n. */
public enum ModerationMethod {
    ADD_FLAT, ADD_PERCENT_OF_MAX, SCALE_FACTOR, RAISE_TO_PASS_WITHIN
}
