package com.vidya.tenantdata.fee.service;

import com.vidya.tenantdata.fee.dto.FeeDtos.LineView;
import com.vidya.tenantdata.fee.dto.FeeDtos.TransactionView;
import com.vidya.tenantdata.fee.entity.FeeLedgerLine;
import com.vidya.tenantdata.fee.entity.FeePayment;
import com.vidya.tenantdata.fee.enums.LineStatus;
import com.vidya.tenantdata.operations.entity.FeeHead;
import com.vidya.tenantdata.operations.entity.FeeSettings;

import java.time.LocalDate;

/** Entity → response mapping shared by the fee services. */
final class FeeViews {

    private FeeViews() {
    }

    /** @param settings the year's fee settings, for the informational late fine (null = none) */
    static LineView line(FeeLedgerLine l, FeeHead h, LocalDate today, FeeSettings settings) {
        long due = l.duePaise();
        long fine = l.getStatus() == LineStatus.ACTIVE ? FeeMath.lateFine(due, l.getDueDate(), today, settings) : 0;
        return new LineView(l.getId(), l.getFeeHeadId(), h == null ? null : h.getName(), h == null || h.getCategory() == null ? null : h.getCategory().name(),
                l.getSource(), l.getInstallmentNo(), l.getInstallmentName(), l.getLabel(), l.getAmountPaise(), l.getConcessionPaise(), l.getPaidPaise(),
                l.getPendingPaise(), due, l.getDueDate(), l.getStatus() == LineStatus.ACTIVE ? l.dueStatus(today) : null, l.getStatus(), fine,
                l.getReason(), l.getCancelReason());
    }

    static TransactionView transaction(FeePayment p) {
        return new TransactionView(p.getId(), p.getReceiptNumber(), p.getPaidOn(), p.getRealizedOn(), p.getAmountPaise(), p.getMode(), p.getStatus(),
                p.getReferenceNo(), p.getCollectedByName(), p.getStatusReason());
    }
}
