package com.vidya.tenantdata.operations.enums;

import java.math.BigDecimal;

/** How a LATE mark counts in the attendance percentage. */
public enum LateCountsAs {
    PRESENT(BigDecimal.ONE), HALF_DAY(new BigDecimal("0.5")), ABSENT(BigDecimal.ZERO);

    private final BigDecimal weight;

    LateCountsAs(BigDecimal weight) {
        this.weight = weight;
    }

    /** Share of a day a late student is counted present. */
    public BigDecimal weight() {
        return weight;
    }
}
