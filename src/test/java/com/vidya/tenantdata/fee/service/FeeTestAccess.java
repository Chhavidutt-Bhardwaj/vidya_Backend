package com.vidya.tenantdata.fee.service;

import com.vidya.tenantdata.fee.dto.FeeDtos.Allocation;
import com.vidya.tenantdata.fee.entity.FeeLedgerLine;

import java.util.List;
import java.util.Map;

/** Opens package-private fee logic to tests in other packages. */
public final class FeeTestAccess {

    private FeeTestAccess() {
    }

    public static Map<FeeLedgerLine, Long> allocate(List<FeeLedgerLine> lines, long amount, List<Allocation> explicit, boolean allowPartial) {
        return FeePaymentService.allocate(lines, amount, explicit, allowPartial);
    }
}
