package com.vidya.tenantdata.fee.service;

import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.tenantdata.academic.entity.AcademicYear;
import com.vidya.tenantdata.fee.dto.FeeDtos.Allocation;
import com.vidya.tenantdata.fee.dto.FeeDtos.AllocationView;
import com.vidya.tenantdata.fee.dto.FeeDtos.ChequeOutcome;
import com.vidya.tenantdata.fee.dto.FeeDtos.ChequeStatusRequest;
import com.vidya.tenantdata.fee.dto.FeeDtos.CollectPaymentRequest;
import com.vidya.tenantdata.fee.dto.FeeDtos.DayBook;
import com.vidya.tenantdata.fee.dto.FeeDtos.LedgerRow;
import com.vidya.tenantdata.fee.dto.FeeDtos.ModeTotal;
import com.vidya.tenantdata.fee.dto.FeeDtos.PaymentResult;
import com.vidya.tenantdata.fee.dto.FeeDtos.Receipt;
import com.vidya.tenantdata.fee.dto.FeeDtos.ReceiptLine;
import com.vidya.tenantdata.fee.dto.FeeDtos.TransactionView;
import com.vidya.tenantdata.fee.entity.FeeLedger;
import com.vidya.tenantdata.fee.entity.FeeLedgerLine;
import com.vidya.tenantdata.fee.entity.FeePayment;
import com.vidya.tenantdata.fee.entity.FeePaymentAllocation;
import com.vidya.tenantdata.fee.enums.LineStatus;
import com.vidya.tenantdata.fee.enums.PaymentMode;
import com.vidya.tenantdata.fee.enums.PaymentStatus;
import com.vidya.tenantdata.fee.repository.FeeLedgerLineRepository;
import com.vidya.tenantdata.fee.repository.FeeLedgerRepository;
import com.vidya.tenantdata.fee.repository.FeePaymentAllocationRepository;
import com.vidya.tenantdata.fee.repository.FeePaymentRepository;
import com.vidya.tenantdata.fee.repository.FeeQueries;
import com.vidya.tenantdata.fee.repository.FeeQueries.DayBookFilter;
import com.vidya.tenantdata.operations.entity.FeeHead;
import com.vidya.tenantdata.operations.entity.FeeSettings;
import com.vidya.tenantdata.operations.enums.NotificationChannel;
import com.vidya.tenantdata.operations.enums.NotificationEvent;
import com.vidya.tenantdata.operations.repository.FeeHeadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Counter payments. Every payment holds the ledger lock while it changes lines, so two cashiers cannot take the same due twice.
 * A cheque is held on its lines as pending until it clears (→ paid) or bounces (→ released); a wrong payment is reversed, never deleted.
 */
@Service
@RequiredArgsConstructor
@Transactional(transactionManager = "tenantTransactionManager")
public class FeePaymentService {

    /** The longest day book range, so a careless request does not scan years of payments. */
    static final int MAX_DAY_BOOK_DAYS = 366;

    private final FeeContext ctx;
    private final FeeQueries queries;
    private final FeeLedgerRepository ledgers;
    private final FeeLedgerLineRepository lines;
    private final FeePaymentRepository payments;
    private final FeePaymentAllocationRepository allocations;
    private final FeeHeadRepository heads;
    private final FeeNotifier notifier;

    public PaymentResult collect(CollectPaymentRequest in) {
        AcademicYear y = ctx.year(in.academicYearId());
        LocalDate today = ctx.today();
        LocalDate paidOn = in.paidOn() != null ? in.paidOn() : today;
        if (paidOn.isAfter(today)) {
            throw FeeContext.error("FEE_PAYMENT_DATE_FUTURE", "The payment date cannot be in the future");
        }
        if (in.mode() == PaymentMode.NACH || in.mode() == PaymentMode.ONLINE) {
            throw FeeContext.error("FEE_MODE_ONLINE_ONLY", in.mode() + " payments are recorded by the payment gateway, not at the counter");
        }
        boolean cheque = in.mode() == PaymentMode.CHEQUE;
        if ((cheque || in.mode() == PaymentMode.DD) && (in.referenceNo() == null || in.referenceNo().isBlank())) {
            throw FeeContext.error("FEE_REFERENCE_REQUIRED", "Enter the " + (cheque ? "cheque" : "demand draft") + " number");
        }
        FeeLedger found = ledgers.findByStudentIdAndAcademicYearId(in.studentId(), y.getId())
                .orElseThrow(() -> FeeContext.error("FEE_LEDGER_NOT_FOUND", "This student has not been billed for " + y.getName()
                        + " yet; generate the invoice first"));
        FeeLedger ledger = ctx.lockLedger(found.getId());
        List<FeeLedgerLine> ledgerLines = lines.findByLedgerIdOrderByDueDateAscInstallmentNoAscCreatedAtAsc(ledger.getId());
        FeeSettings settings = ctx.settings(y.getId());
        Map<FeeLedgerLine, Long> plan = allocate(ledgerLines, in.amountPaise(), in.allocation(), settings.isAllowPartialPayment());

        FeePayment p = new FeePayment();
        p.setReceiptNumber(FeeMath.number(prefix(settings), y.getStartDate(), queries.nextNumber("RECEIPT", y.getId())));
        p.setLedgerId(ledger.getId());
        p.setStudentId(ledger.getStudentId());
        p.setAcademicYearId(y.getId());
        p.setAmountPaise(in.amountPaise());
        p.setMode(in.mode());
        p.setReferenceNo(blankToNull(in.referenceNo()));
        p.setChequeDate(cheque ? (in.chequeDate() != null ? in.chequeDate() : paidOn) : in.chequeDate());
        p.setBankName(blankToNull(in.bankName()));
        p.setPaidOn(paidOn);
        p.setStatus(cheque ? PaymentStatus.PENDING_CLEARANCE : PaymentStatus.SUCCESS);
        p.setRealizedOn(cheque ? null : paidOn);
        p.setRemarks(blankToNull(in.remarks()));
        p.setCollectedBy(ctx.actorId());
        p.setCollectedByName(ctx.actorName());
        payments.save(p);

        List<FeePaymentAllocation> rows = new ArrayList<>();
        List<AllocationView> views = new ArrayList<>();
        for (Map.Entry<FeeLedgerLine, Long> e : plan.entrySet()) {
            FeeLedgerLine l = e.getKey();
            if (cheque) {
                l.setPendingPaise(l.getPendingPaise() + e.getValue());
            } else {
                l.setPaidPaise(l.getPaidPaise() + e.getValue());
            }
            rows.add(new FeePaymentAllocation(p.getId(), l.getId(), e.getValue()));
            views.add(new AllocationView(l.getId(), l.getLabel(), e.getValue()));
        }
        lines.saveAll(plan.keySet());
        allocations.saveAll(rows);
        int queued = in.sendReceipt() == null ? 0 : sendReceipt(p, in.sendReceipt().channels());
        long newDue = ledgerLines.stream().mapToLong(FeeLedgerLine::duePaise).sum();
        return new PaymentResult(p.getId(), p.getReceiptNumber(), p.getStatus(), p.getAmountPaise(), views, newDue, queued);
    }

    /**
     * Which lines the money goes to. Explicit: every line on this ledger, active, at most its due, adding up to the amount. Otherwise
     * oldest due first. Without partial payments every line touched must be paid off in full.
     */
    static Map<FeeLedgerLine, Long> allocate(List<FeeLedgerLine> ledgerLines, long amount, List<Allocation> explicit, boolean allowPartial) {
        Map<FeeLedgerLine, Long> out = new LinkedHashMap<>();
        long totalDue = ledgerLines.stream().mapToLong(FeeLedgerLine::duePaise).sum();
        if (amount > totalDue) {
            throw FeeContext.error("FEE_OVERPAYMENT", "The amount " + FeeMath.rupees(amount) + " is more than what is due (" + FeeMath.rupees(totalDue) + ")");
        }
        if (explicit != null && !explicit.isEmpty()) {
            Map<UUID, FeeLedgerLine> byId = ledgerLines.stream().collect(Collectors.toMap(FeeLedgerLine::getId, Function.identity()));
            Set<UUID> seen = new HashSet<>();
            long sum = 0;
            for (Allocation a : explicit) {
                FeeLedgerLine l = byId.get(a.lineId());
                if (l == null || l.getStatus() != LineStatus.ACTIVE) {
                    throw FeeContext.error("FEE_ALLOCATION_LINE", "Line " + a.lineId() + " is not an open line of this ledger");
                }
                if (!seen.add(a.lineId())) {
                    throw FeeContext.error("FEE_ALLOCATION_LINE", "Line " + l.getLabel() + " is listed twice");
                }
                if (a.amountPaise() > l.duePaise()) {
                    throw FeeContext.error("FEE_ALLOCATION_EXCEEDS_DUE", l.getLabel() + ": " + FeeMath.rupees(a.amountPaise()) + " is more than its due "
                            + FeeMath.rupees(l.duePaise()));
                }
                if (!allowPartial && a.amountPaise() != l.duePaise()) {
                    throw FeeContext.error("FEE_PARTIAL_NOT_ALLOWED", "The school does not take part payments: pay " + l.getLabel() + " in full");
                }
                out.put(l, a.amountPaise());
                sum += a.amountPaise();
            }
            if (sum != amount) {
                throw FeeContext.error("FEE_ALLOCATION_MISMATCH", "The allocation adds up to " + FeeMath.rupees(sum) + ", not " + FeeMath.rupees(amount));
            }
            return out;
        }
        long left = amount;
        for (FeeLedgerLine l : ledgerLines) {
            long due = l.duePaise();
            if (left <= 0 || due <= 0) {
                continue;
            }
            long take = Math.min(left, due);
            if (!allowPartial && take < due) {
                throw FeeContext.error("FEE_PARTIAL_NOT_ALLOWED", "The school does not take part payments: " + FeeMath.rupees(amount)
                        + " leaves " + l.getLabel() + " partly paid");
            }
            out.put(l, take);
            left -= take;
        }
        return out;
    }

    /** A cheque clears (pending → paid, collected on {@code date}) or bounces (pending released). */
    public TransactionView chequeStatus(UUID paymentId, ChequeStatusRequest in) {
        FeePayment p = payment(paymentId);
        FeeLedger ledger = ctx.lockLedger(p.getLedgerId());
        p = payment(paymentId);
        if (p.getStatus() != PaymentStatus.PENDING_CLEARANCE) {
            throw FeeContext.error("FEE_PAYMENT_STATE", "This payment is " + p.getStatus() + ", not waiting for clearance");
        }
        LocalDate date = in.date() != null ? in.date() : ctx.today();
        if (date.isAfter(ctx.today()) || date.isBefore(p.getPaidOn())) {
            throw FeeContext.error("FEE_CLEARANCE_DATE", "The clearance date must be between the payment date and today");
        }
        boolean cleared = in.status() == ChequeOutcome.CLEARED;
        Map<UUID, FeeLedgerLine> byId = lines.findByLedgerIdOrderByDueDateAscInstallmentNoAscCreatedAtAsc(ledger.getId()).stream()
                .collect(Collectors.toMap(FeeLedgerLine::getId, Function.identity()));
        for (FeePaymentAllocation a : allocations.findByPaymentId(p.getId())) {
            FeeLedgerLine l = byId.get(a.getLedgerLineId());
            l.setPendingPaise(l.getPendingPaise() - a.getAmountPaise());
            if (cleared) {
                l.setPaidPaise(l.getPaidPaise() + a.getAmountPaise());
            }
            lines.save(l);
        }
        p.setStatus(cleared ? PaymentStatus.SUCCESS : PaymentStatus.BOUNCED);
        p.setRealizedOn(cleared ? date : null);
        mark(p, in.remarks());
        return FeeViews.transaction(payments.save(p));
    }

    // ── Gateway money (auto-debits, links, checkout orders) ────────────────────────────

    /**
     * Records money from the gateway on one ledger, oldest dues first (part payments always accepted: the money is already taken).
     * {@code pending}: an auto-debit sent to the bank, held on the lines like a cheque until {@link #settleGatewayPayment}.
     * Lines due after {@code dueBy} are not touched (null = any). Returns null when nothing is due.
     */
    public FeePayment recordGatewayPayment(UUID ledgerId, long amountPaise, PaymentMode mode, boolean pending, LocalDate dueBy, String reference,
                                           String remarks, String collectedByName) {
        FeeLedger ledger = ctx.lockLedger(ledgerId);
        List<FeeLedgerLine> open = lines.findByLedgerIdOrderByDueDateAscInstallmentNoAscCreatedAtAsc(ledger.getId()).stream()
                .filter(l -> dueBy == null || !l.getDueDate().isAfter(dueBy)).toList();
        long due = open.stream().mapToLong(FeeLedgerLine::duePaise).sum();
        long amount = Math.min(amountPaise, due);
        if (amount <= 0) {
            return null;
        }
        Map<FeeLedgerLine, Long> plan = allocate(open, amount, null, true);
        AcademicYear y = ctx.year(ledger.getAcademicYearId());
        LocalDate today = ctx.today();
        FeePayment p = new FeePayment();
        p.setReceiptNumber(FeeMath.number(prefix(ctx.settings(y.getId())), y.getStartDate(), queries.nextNumber("RECEIPT", y.getId())));
        p.setLedgerId(ledger.getId());
        p.setStudentId(ledger.getStudentId());
        p.setAcademicYearId(y.getId());
        p.setAmountPaise(amount);
        p.setMode(mode);
        p.setReferenceNo(blankToNull(reference));
        p.setPaidOn(today);
        p.setStatus(pending ? PaymentStatus.PENDING_CLEARANCE : PaymentStatus.SUCCESS);
        p.setRealizedOn(pending ? null : today);
        p.setRemarks(blankToNull(remarks));
        p.setCollectedByName(collectedByName);
        payments.save(p);
        List<FeePaymentAllocation> rows = new ArrayList<>();
        for (Map.Entry<FeeLedgerLine, Long> e : plan.entrySet()) {
            FeeLedgerLine l = e.getKey();
            if (pending) {
                l.setPendingPaise(l.getPendingPaise() + e.getValue());
            } else {
                l.setPaidPaise(l.getPaidPaise() + e.getValue());
            }
            rows.add(new FeePaymentAllocation(p.getId(), l.getId(), e.getValue()));
        }
        lines.saveAll(plan.keySet());
        allocations.saveAll(rows);
        return p;
    }

    /** A pending gateway payment (auto-debit) succeeded (→ paid, collected on {@code date}) or failed (→ released, BOUNCED). */
    public FeePayment settleGatewayPayment(UUID paymentId, boolean success, LocalDate date, String reference, String reason) {
        FeePayment p = payment(paymentId);
        FeeLedger ledger = ctx.lockLedger(p.getLedgerId());
        p = payment(paymentId);
        if (p.getStatus() != PaymentStatus.PENDING_CLEARANCE) {
            return p;
        }
        Map<UUID, FeeLedgerLine> byId = lines.findByLedgerIdOrderByDueDateAscInstallmentNoAscCreatedAtAsc(ledger.getId()).stream()
                .collect(Collectors.toMap(FeeLedgerLine::getId, Function.identity()));
        for (FeePaymentAllocation a : allocations.findByPaymentId(p.getId())) {
            FeeLedgerLine l = byId.get(a.getLedgerLineId());
            l.setPendingPaise(l.getPendingPaise() - a.getAmountPaise());
            if (success) {
                l.setPaidPaise(l.getPaidPaise() + a.getAmountPaise());
            }
            lines.save(l);
        }
        p.setStatus(success ? PaymentStatus.SUCCESS : PaymentStatus.BOUNCED);
        p.setRealizedOn(success ? date : null);
        if (reference != null && p.getReferenceNo() == null) {
            p.setReferenceNo(reference);
        }
        p.setStatusChangedAt(ctx.clock().instant());
        p.setStatusReason(blankToNull(reason));
        return payments.save(p);
    }

    /** Queues the receipt message of a gateway payment (template channels). */
    public int sendGatewayReceipt(FeePayment p) {
        return sendReceipt(p, null);
    }

    /** Takes a received payment back off its lines (wrong student, wrong amount, refund). Cheques still pending are bounced instead. */
    public TransactionView reverse(UUID paymentId, String reason) {
        FeePayment p = payment(paymentId);
        FeeLedger ledger = ctx.lockLedger(p.getLedgerId());
        p = payment(paymentId);
        if (p.getStatus() != PaymentStatus.SUCCESS) {
            throw FeeContext.error("FEE_PAYMENT_STATE", p.getStatus() == PaymentStatus.PENDING_CLEARANCE
                    ? "This cheque has not cleared; mark it bounced instead" : "This payment is already " + p.getStatus());
        }
        Map<UUID, FeeLedgerLine> byId = lines.findByLedgerIdOrderByDueDateAscInstallmentNoAscCreatedAtAsc(ledger.getId()).stream()
                .collect(Collectors.toMap(FeeLedgerLine::getId, Function.identity()));
        for (FeePaymentAllocation a : allocations.findByPaymentId(p.getId())) {
            FeeLedgerLine l = byId.get(a.getLedgerLineId());
            l.setPaidPaise(l.getPaidPaise() - a.getAmountPaise());
            lines.save(l);
        }
        p.setStatus(PaymentStatus.REVERSED);
        mark(p, reason);
        return FeeViews.transaction(payments.save(p));
    }

    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public Receipt receipt(UUID paymentId) {
        FeePayment p = payment(paymentId);
        ctx.checkStudent(p.getStudentId());
        LedgerRow row = queries.ledgerRow(p.getLedgerId(), ctx.today()).orElseThrow(() -> new ResourceNotFoundException("FeeLedger", p.getLedgerId()));
        List<FeePaymentAllocation> allocs = allocations.findByPaymentId(p.getId());
        Map<UUID, FeeLedgerLine> lineById = lines.findAllById(allocs.stream().map(FeePaymentAllocation::getLedgerLineId).toList()).stream()
                .collect(Collectors.toMap(FeeLedgerLine::getId, Function.identity()));
        Map<UUID, FeeHead> headById = heads.findAllById(lineById.values().stream().map(FeeLedgerLine::getFeeHeadId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(FeeHead::getId, Function.identity()));
        List<ReceiptLine> receiptLines = allocs.stream().map(a -> {
            FeeLedgerLine l = lineById.get(a.getLedgerLineId());
            FeeHead h = headById.get(l.getFeeHeadId());
            return new ReceiptLine(l.getId(), l.getLabel(), h == null ? null : h.getName(), l.getInstallmentName(),
                    l.getAmountPaise() - l.getConcessionPaise(), a.getAmountPaise());
        }).toList();
        return new Receipt(p.getId(), p.getReceiptNumber(), p.getStatus(), p.getPaidOn(), p.getRealizedOn(), p.getStudentId(), row.studentName(),
                row.admissionNumber(), row.className(), row.sectionName(), row.invoiceNumber(), ctx.year(p.getAcademicYearId()).getName(), p.getMode(),
                p.getReferenceNo(), p.getChequeDate(), p.getBankName(), p.getAmountPaise(), FeeMath.inWords(p.getAmountPaise()), receiptLines,
                p.getCollectedByName(), p.getRemarks(), p.getStatusReason(), row.duePaise());
    }

    /** (Re)sends the FEE_PAYMENT_RECEIVED message to the student's guardians. Returns how many messages were queued. */
    public int resend(UUID paymentId, Set<NotificationChannel> channels) {
        FeePayment p = payment(paymentId);
        if (p.getStatus() == PaymentStatus.BOUNCED || p.getStatus() == PaymentStatus.REVERSED) {
            throw FeeContext.error("FEE_PAYMENT_STATE", "A " + p.getStatus() + " payment has no receipt to send");
        }
        return sendReceipt(p, channels);
    }

    private int sendReceipt(FeePayment p, Set<NotificationChannel> channels) {
        String student = queries.ledgerRow(p.getLedgerId(), ctx.today()).map(LedgerRow::studentName).orElse("");
        Map<String, String> values = Map.of("student_name", student, "amount", FeeMath.rupees(p.getAmountPaise()), "receipt_no", p.getReceiptNumber(),
                "date", FeeMath.date(p.getPaidOn()));
        // Each resend is a new message: the dedupe key carries the moment it was asked for
        String key = "FEE_RECEIPT:" + p.getId() + ":" + ctx.clock().millis();
        return notifier.send(NotificationEvent.FEE_PAYMENT_RECEIVED, List.of(new FeeNotifier.Message(p.getStudentId(), values, "FEE_PAYMENT", p.getId(), key)),
                channels).queued();
    }

    /** Counter closing: payments taken between the dates (by payment date), with totals per mode excluding bounced and reversed. */
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public DayBook dayBook(LocalDate from, LocalDate to, PaymentMode mode, PaymentStatus status, UUID collectedBy, UUID classId, int page, int size) {
        LocalDate t = to != null ? to : ctx.today();
        LocalDate f = from != null ? from : t;
        if (f.isAfter(t)) {
            throw FeeContext.error("FEE_RANGE_INVALID", "from must not be after to");
        }
        if (ChronoUnit.DAYS.between(f, t) >= MAX_DAY_BOOK_DAYS) {
            throw FeeContext.error("FEE_RANGE_TOO_LONG", "Ask for at most " + MAX_DAY_BOOK_DAYS + " days at a time");
        }
        DayBookFilter filter = new DayBookFilter(f, t, mode, status, collectedBy, classId);
        int p = Math.max(0, page);
        int s = Math.min(Math.max(1, size), 200);
        List<ModeTotal> byMode = queries.dayBookTotals(filter);
        return new DayBook(f, t, byMode.stream().mapToLong(ModeTotal::amountPaise).sum(), byMode,
                new PageImpl<>(queries.dayBook(filter, p, s), PageRequest.of(p, s), queries.countDayBook(filter)));
    }

    private FeePayment payment(UUID id) {
        return payments.findById(id).orElseThrow(() -> new ResourceNotFoundException("FeePayment", id));
    }

    private void mark(FeePayment p, String reason) {
        p.setStatusChangedAt(ctx.clock().instant());
        p.setStatusChangedBy(ctx.actorId());
        p.setStatusReason(blankToNull(reason));
    }

    private static String prefix(FeeSettings s) {
        return s.getReceiptPrefix() == null || s.getReceiptPrefix().isBlank() ? "RCP" : s.getReceiptPrefix().trim();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
