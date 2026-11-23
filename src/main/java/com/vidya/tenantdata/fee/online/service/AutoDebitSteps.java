package com.vidya.tenantdata.fee.online.service;

import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.tenantdata.academic.entity.AcademicYear;
import com.vidya.tenantdata.fee.dto.FeeDtos.AddChargeRequest;
import com.vidya.tenantdata.fee.entity.FeeLedgerLine;
import com.vidya.tenantdata.fee.entity.FeePayment;
import com.vidya.tenantdata.fee.enums.PaymentMode;
import com.vidya.tenantdata.fee.online.dto.OnlineFeeDtos.RunView;
import com.vidya.tenantdata.fee.online.entity.FeeAutoDebitSettings;
import com.vidya.tenantdata.fee.online.entity.FeeDebit;
import com.vidya.tenantdata.fee.online.entity.FeeDebitRun;
import com.vidya.tenantdata.fee.online.entity.FeeGatewayPayment;
import com.vidya.tenantdata.fee.online.entity.FeeMandate;
import com.vidya.tenantdata.fee.online.enums.DebitStatus;
import com.vidya.tenantdata.fee.online.enums.GatewayProvider;
import com.vidya.tenantdata.fee.online.enums.MandateStatus;
import com.vidya.tenantdata.fee.online.gateway.FeeGatewayRegistry;
import com.vidya.tenantdata.fee.online.gateway.GatewayModels.Charge;
import com.vidya.tenantdata.fee.online.gateway.GatewayModels.ChargeSubmitted;
import com.vidya.tenantdata.fee.online.gateway.GatewayModels.PaymentReport;
import com.vidya.tenantdata.fee.online.repository.FeeDebitRepository;
import com.vidya.tenantdata.fee.online.repository.FeeDebitRunRepository;
import com.vidya.tenantdata.fee.online.repository.FeeGatewayPaymentRepository;
import com.vidya.tenantdata.fee.online.repository.FeeMandateRepository;
import com.vidya.tenantdata.fee.online.repository.OnlineFeeQueries;
import com.vidya.tenantdata.fee.online.repository.OnlineFeeQueries.Child;
import com.vidya.tenantdata.fee.online.repository.OnlineFeeQueries.GuardianRow;
import com.vidya.tenantdata.fee.repository.FeeLedgerLineRepository;
import com.vidya.tenantdata.fee.service.FeeContext;
import com.vidya.tenantdata.fee.service.FeeInvoiceService;
import com.vidya.tenantdata.fee.service.FeeMath;
import com.vidya.tenantdata.fee.service.FeeNotifier;
import com.vidya.tenantdata.fee.service.FeePaymentService;
import com.vidya.tenantdata.operations.entity.FeeHead;
import com.vidya.tenantdata.operations.enums.FeeCategory;
import com.vidya.tenantdata.operations.enums.FeeFrequency;
import com.vidya.tenantdata.operations.enums.NotificationEvent;
import com.vidya.tenantdata.operations.repository.FeeHeadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The transactional pieces of the monthly auto-debit (each call one transaction; {@link AutoDebitService} strings them together and
 * calls the gateway in between, outside any transaction):
 * <ol>
 *   <li>plan: one SCHEDULED debit per active mandate and child with something due by the debit day; children higher class first,
 *       each next child {@code sibling_gap_days} later</li>
 *   <li>invite: guardians with dues and no mandate are asked to set one up</li>
 *   <li>notices: the payer is told the amount and date before each debit (pre-debit notification)</li>
 *   <li>prepare / submitted / submitFailed: the due is held on the lines as a pending NACH payment while the bank decides</li>
 *   <li>outcome: success clears the payment; failure releases it, adds the NACH bounce charge and its GST, schedules a retry</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(transactionManager = "tenantTransactionManager")
public class AutoDebitSteps {

    static final String BOUNCE_HEAD = "NACH_BOUNCE";
    static final String BOUNCE_GST_HEAD = "NACH_BOUNCE_GST";
    static final String COLLECTOR = "Auto-debit";

    private final FeeDebitRepository debits;
    private final FeeMandateRepository mandates;
    private final FeeDebitRunRepository runs;
    private final FeeGatewayPaymentRepository gatewayPayments;
    private final OnlineFeeQueries queries;
    private final OnlineFeeConfigService config;
    private final FeePaymentService payments;
    private final FeeInvoiceService invoices;
    private final FeeLedgerLineRepository lines;
    private final FeeHeadRepository heads;
    private final FeeContext ctx;
    private final FeeNotifier notifier;
    private final FeeGatewayRegistry gateways;

    /** What to send to the gateway for one prepared debit. */
    public record Prepared(UUID debitId, GatewayProvider provider, Charge charge) {
    }

    // ── 1. Plan ────────────────────────────────────────────────────────────────────────

    /**
     * Schedules the month's debits (safe to repeat: existing attempts are kept). Mandates activated on or after the debit day wait
     * for next month unless {@code catchUp}. A debit is never scheduled earlier than tomorrow, so the payer always gets a notice first.
     */
    public int plan(LocalDate cycleMonth, boolean catchUp) {
        FeeAutoDebitSettings s = config.settings();
        LocalDate today = ctx.today();
        LocalDate debitDate = cycleMonth.withDayOfMonth(s.getDebitDay());
        AcademicYear year = yearOn(debitDate);
        ZoneId zone = ctx.clock().getZone();
        int created = 0;
        for (FeeMandate m : mandates.findByStatusIn(Set.of(MandateStatus.ACTIVE))) {
            if (!catchUp && (m.getActivatedAt() == null || !m.getActivatedAt().atZone(zone).toLocalDate().isBefore(debitDate))) {
                continue;
            }
            List<UUID> studentIds = queries.mandateStudents(List.of(m.getId())).getOrDefault(m.getId(), List.of());
            Map<UUID, FeeDebit> existing = debits.findByMandateIdAndCycleMonth(m.getId(), cycleMonth).stream().filter(d -> d.getAttemptNo() == 1)
                    .collect(Collectors.toMap(FeeDebit::getStudentId, d -> d, (a, b) -> a));
            int rank = 0;
            for (Child c : queries.children(year.getId(), studentIds)) {
                if (existing.containsKey(c.studentId())) {
                    rank++;
                    continue;
                }
                LocalDate on = debitDate.plusDays((long) rank * s.getSiblingGapDays());
                long due = Math.min(dueBy(c.ledgerId(), on), m.getMaxAmountPaise());
                if (due <= 0) {
                    continue;
                }
                if (!on.isAfter(today)) {
                    on = today.plusDays(1);
                }
                FeeDebit d = new FeeDebit();
                d.setReference(OnlineSupport.reference("DB"));
                d.setMandateId(m.getId());
                d.setStudentId(c.studentId());
                d.setLedgerId(c.ledgerId());
                d.setCycleMonth(cycleMonth);
                d.setAttemptNo((short) 1);
                d.setSiblingRank((short) rank);
                d.setScheduledOn(on);
                d.setAmountPaise(due);
                debits.save(d);
                created++;
                rank++;
            }
        }
        return created;
    }

    /** The academic year of the date, else the current one. */
    AcademicYear yearOn(LocalDate date) {
        return ctx.year(queries.yearOn(date).orElse(null));
    }

    /** What the ledger owes on lines due on or before the date (concessions, payments and pending cheques / debits taken off). */
    long dueBy(UUID ledgerId, LocalDate date) {
        return lines.findByLedgerIdOrderByDueDateAscInstallmentNoAscCreatedAtAsc(ledgerId).stream().filter(l -> !l.getDueDate().isAfter(date))
                .mapToLong(FeeLedgerLine::duePaise).sum();
    }

    // ── 2. Invite guardians without a mandate ──────────────────────────────────────────

    /** Between {@code invite_days_before} and one day before the debit day: once per month per guardian and channel. */
    public int invite(LocalDate debitDate) {
        FeeAutoDebitSettings s = config.settings();
        long days = ChronoUnit.DAYS.between(ctx.today(), debitDate);
        if (s.getInviteDaysBefore() <= 0 || days < 1 || days > s.getInviteDaysBefore()) {
            return 0;
        }
        AcademicYear year = yearOn(debitDate);
        List<UUID> students = queries.uncoveredWithDue(year.getId(), debitDate);
        if (students.isEmpty()) {
            return 0;
        }
        Map<UUID, Child> child = queries.children(year.getId(), students).stream().collect(Collectors.toMap(Child::studentId, c -> c, (a, b) -> a,
                LinkedHashMap::new));
        // One message per person, naming all their children without a mandate
        Map<String, List<GuardianRow>> byPerson = new LinkedHashMap<>();
        for (GuardianRow g : queries.guardiansOfStudents(students)) {
            String key = g.guardianUserId() != null ? "U" + g.guardianUserId() : "P" + PayerAccess.lastTen(g.phone());
            if (key.length() > 1 && child.containsKey(g.studentId())) {
                byPerson.computeIfAbsent(key, k -> new ArrayList<>()).add(g);
            }
        }
        String link = s.getMandateSetupUrl() != null ? s.getMandateSetupUrl() : "the school's parent app (or ask the school office)";
        List<FeeNotifier.Message> messages = new ArrayList<>();
        for (List<GuardianRow> rows : byPerson.values()) {
            List<Child> theirs = rows.stream().map(g -> child.get(g.studentId())).distinct()
                    .sorted((a, b) -> Integer.compare(b.classSort(), a.classSort())).toList();
            long amount = theirs.stream().mapToLong(c -> dueBy(c.ledgerId(), debitDate)).sum();
            if (amount <= 0) {
                continue;
            }
            Child first = theirs.getFirst();
            messages.add(new FeeNotifier.Message(first.studentId(), Map.of("student_name", theirs.stream().map(Child::studentName).collect(Collectors.joining(", ")),
                    "amount", FeeMath.rupees(amount), "debit_date", FeeMath.date(debitDate), "setup_link", link), "FEE_MANDATE_INVITE", null,
                    "FEE_MANDATE_INVITE:" + debitDate, rows.stream().map(GuardianRow::guardianId).collect(Collectors.toSet())));
        }
        return notifier.send(NotificationEvent.FEE_MANDATE_INVITE, messages, null).queued();
    }

    // ── 3. Pre-debit notices ───────────────────────────────────────────────────────────

    /**
     * Debits {@code notice_days} away get a notice. A debit planned late (fewer days ahead than the earliest notice) is told the day it is
     * planned. Dedupe: once per debit, day and channel.
     */
    public int notices() {
        FeeAutoDebitSettings s = config.settings();
        List<Integer> days = s.noticeDayList();
        int widest = days.isEmpty() ? 1 : days.getFirst();
        LocalDate today = ctx.today();
        ZoneId zone = ctx.clock().getZone();
        List<FeeNotifier.Message> messages = new ArrayList<>();
        Map<UUID, FeeMandate> mandateById = new HashMap<>();
        Map<UUID, Map<UUID, UUID>> guardians = new HashMap<>();
        List<LocalDate> window = new ArrayList<>();
        for (int i = 1; i <= widest; i++) {
            window.add(today.plusDays(i));
        }
        for (FeeDebit d : debits.findByStatusAndScheduledOnIn(DebitStatus.SCHEDULED, window)) {
            int ahead = (int) ChronoUnit.DAYS.between(today, d.getScheduledOn());
            boolean plannedToday = d.getCreatedAt() != null && d.getCreatedAt().atZone(zone).toLocalDate().equals(today);
            if (!days.contains(ahead) && !(plannedToday && ahead < widest)) {
                continue;
            }
            FeeMandate m = mandateById.computeIfAbsent(d.getMandateId(), id -> mandates.findById(id).orElse(null));
            if (m == null || m.getStatus() != MandateStatus.ACTIVE) {
                continue;
            }
            long amount = Math.min(dueBy(d.getLedgerId(), d.getScheduledOn()), m.getMaxAmountPaise());
            if (amount <= 0) {
                continue;
            }
            UUID guardian = guardians.computeIfAbsent(m.getId(), queries::mandateGuardians).get(d.getStudentId());
            String name = queries.briefs(List.of(d.getStudentId())).values().stream().findFirst().map(b -> b.studentName()).orElse("");
            messages.add(new FeeNotifier.Message(d.getStudentId(), Map.of("student_name", name, "amount", FeeMath.rupees(amount),
                    "debit_date", FeeMath.date(d.getScheduledOn()), "method", MandateService.method(m.getMethod())), "FEE_DEBIT", d.getId(),
                    "FEE_DEBIT_NOTICE:" + d.getId() + ":" + today, guardian == null ? null : Set.of(guardian)));
        }
        return messages.isEmpty() ? 0 : notifier.send(NotificationEvent.FEE_AUTO_DEBIT_NOTICE, messages, null).queued();
    }

    // ── 4. Submit ──────────────────────────────────────────────────────────────────────

    /** Scheduled debits to send now: due on or before today plus the gateway's presentation lead. */
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public List<UUID> dueForSubmission() {
        LocalDate today = ctx.today();
        Map<UUID, GatewayProvider> providerOf = new HashMap<>();
        List<UUID> out = new ArrayList<>();
        for (FeeDebit d : debits.findByStatusAndScheduledOnLessThanEqualOrderByScheduledOnAscSiblingRankAsc(DebitStatus.SCHEDULED, today.plusDays(7))) {
            GatewayProvider p = providerOf.computeIfAbsent(d.getMandateId(), id -> mandates.findById(id).map(FeeMandate::getProvider).orElse(null));
            if (p != null && !d.getScheduledOn().minusDays(gateways.get(p).presentationLeadDays()).isAfter(today)) {
                out.add(d.getId());
            }
        }
        return out;
    }

    /**
     * Holds what is due by the debit day on the lines (pending NACH payment) and returns the charge to send; null when the debit is
     * skipped (paid before the day, mandate no longer active) or was taken by another server.
     */
    public Prepared prepare(UUID debitId) {
        FeeDebit d = lockDebit(debitId);
        if (d.getStatus() != DebitStatus.SCHEDULED) {
            return null;
        }
        FeeMandate m = mandates.findById(d.getMandateId()).orElseThrow();
        if (m.getStatus() != MandateStatus.ACTIVE) {
            d.setStatus(m.getStatus() == MandateStatus.PAUSED ? DebitStatus.SKIPPED : DebitStatus.CANCELLED);
            d.setFailureReason("Mandate " + m.getStatus().name().toLowerCase());
            debits.save(d);
            return null;
        }
        FeePayment p = payments.recordGatewayPayment(d.getLedgerId(), m.getMaxAmountPaise(), PaymentMode.NACH, true, d.getScheduledOn(), d.getReference(),
                "Auto-debit " + d.getReference() + " (" + MandateService.method(m.getMethod()) + ")", COLLECTOR);
        if (p == null) {
            d.setStatus(DebitStatus.SKIPPED);
            d.setFailureReason("Nothing due on the debit day (paid before)");
            d.setAmountPaise(0);
            debits.save(d);
            return null;
        }
        d.setAmountPaise(p.getAmountPaise());
        d.setFeePaymentId(p.getId());
        d.setStatus(DebitStatus.SUBMITTING);
        d.setSubmittedAt(ctx.clock().instant());
        debits.save(d);
        int lead = gateways.get(m.getProvider()).presentationLeadDays();
        LocalDate earliest = ctx.today().plusDays(lead);
        LocalDate debitOn = d.getScheduledOn().isBefore(earliest) ? earliest : d.getScheduledOn();
        String name = queries.briefs(List.of(d.getStudentId())).values().stream().findFirst().map(b -> b.studentName()).orElse("student");
        return new Prepared(d.getId(), m.getProvider(), new Charge(d.getReference(), MandateService.ref(m), MandateService.payer(m), p.getAmountPaise(),
                debitOn, cut("School fees " + name + " " + d.getCycleMonth().getMonth() + " " + d.getCycleMonth().getYear(), 100)));
    }

    /** The gateway took the debit; the bank's answer comes by webhook (which may already have arrived). */
    public void submitted(UUID debitId, ChargeSubmitted r) {
        FeeDebit d = lockDebit(debitId);
        if (d.getGatewayPaymentId() == null) {
            d.setGatewayPaymentId(r.gatewayPaymentId());
        }
        if (d.getGatewayOrderId() == null) {
            d.setGatewayOrderId(r.gatewayOrderId());
        }
        if (d.getStatus() == DebitStatus.SUBMITTING) {
            d.setStatus(DebitStatus.SUBMITTED);
        }
        debits.save(d);
    }

    /** The gateway refused the debit (not a bank bounce: no charge). The held amount is released; the office can look into it. */
    public void submitFailed(UUID debitId, String message) {
        FeeDebit d = lockDebit(debitId);
        if (d.getStatus() != DebitStatus.SUBMITTING) {
            return;
        }
        if (d.getFeePaymentId() != null) {
            payments.settleGatewayPayment(d.getFeePaymentId(), false, ctx.today(), null, "Auto-debit not sent: " + message);
        }
        d.setStatus(DebitStatus.ERROR);
        d.setFailureReason(cut(message, 500));
        debits.save(d);
    }

    // ── 5. Outcome ─────────────────────────────────────────────────────────────────────

    /**
     * The bank's answer. Returns null when the report is not about an auto-debit (then it may be a checkout order), else what was done.
     */
    public String outcome(GatewayProvider provider, PaymentReport r) {
        FeeDebit found = r.reference() != null ? debits.findByReference(r.reference()).orElse(null) : null;
        if (found == null && r.gatewayOrderId() != null) {
            found = debits.findFirstByGatewayOrderId(r.gatewayOrderId()).orElse(null);
        }
        if (found == null && r.gatewayPaymentId() != null) {
            found = debits.findFirstByGatewayPaymentId(r.gatewayPaymentId()).orElse(null);
        }
        if (found == null) {
            return null;
        }
        FeeDebit d = lockDebit(found.getId());
        if (d.getStatus() != DebitStatus.SUBMITTED && d.getStatus() != DebitStatus.SUBMITTING) {
            return "IGNORED: debit " + d.getReference() + " is " + d.getStatus();
        }
        if (r.gatewayPaymentId() != null) {
            if (gatewayPayments.existsByProviderAndGatewayPaymentId(provider, r.gatewayPaymentId())) {
                return "IGNORED: payment " + r.gatewayPaymentId() + " already applied";
            }
            d.setGatewayPaymentId(r.gatewayPaymentId());
        }
        if (r.gatewayOrderId() != null && d.getGatewayOrderId() == null) {
            d.setGatewayOrderId(r.gatewayOrderId());
        }
        d.setSettledAt(ctx.clock().instant());
        if (r.success()) {
            if (r.amountPaise() > 0 && r.amountPaise() != d.getAmountPaise()) {
                log.warn("Debit {}: gateway reports {} paise, {} were sent", d.getReference(), r.amountPaise(), d.getAmountPaise());
            }
            FeePayment p = payments.settleGatewayPayment(d.getFeePaymentId(), true, ctx.today(), r.gatewayPaymentId(), null);
            recordGatewayPayment(provider, r.gatewayPaymentId(), d, r.amountPaise() > 0 ? r.amountPaise() : d.getAmountPaise(), r.method());
            d.setStatus(DebitStatus.SUCCESS);
            debits.save(d);
            payments.sendGatewayReceipt(p);
            return "APPLIED: debit " + d.getReference() + " succeeded";
        }
        String reason = r.reason() == null || r.reason().isBlank() ? "declined by the bank" : r.reason();
        payments.settleGatewayPayment(d.getFeePaymentId(), false, ctx.today(), r.gatewayPaymentId(), "Auto-debit failed: " + reason);
        recordGatewayPayment(provider, r.gatewayPaymentId(), d, 0, r.method());
        d.setStatus(DebitStatus.FAILED);
        d.setFailureReason(cut(reason, 500));
        bounce(d, reason);
        debits.save(d);
        FeeDebit retry = scheduleRetry(d);
        notifyFailure(d, reason, retry);
        return "APPLIED: debit " + d.getReference() + " failed (" + reason + ")" + (retry != null ? ", retry on " + retry.getScheduledOn() : "");
    }

    /** NACH bounce charge and GST on it, as two charge lines due today on the student's ledger (paid with the next debit or at the counter). */
    void bounce(FeeDebit d, String reason) {
        FeeAutoDebitSettings s = config.settings();
        long base = s.getBounceChargePaise();
        if (base <= 0) {
            return;
        }
        long gst = OnlineFeeConfigService.gstOn(base, s.getBounceGstPercent());
        String why = cut("Auto-debit " + d.getReference() + " of " + FeeMath.date(d.getScheduledOn()) + " failed: " + reason, 500);
        invoices.addCharge(d.getLedgerId(), new AddChargeRequest(head(BOUNCE_HEAD, "NACH bounce charge").getId(), base, ctx.today(),
                "NACH bounce charge (" + FeeMath.date(d.getScheduledOn()) + ")", why));
        if (gst > 0) {
            invoices.addCharge(d.getLedgerId(), new AddChargeRequest(head(BOUNCE_GST_HEAD, "GST on NACH bounce charge").getId(), gst, ctx.today(),
                    "GST " + s.getBounceGstPercent().stripTrailingZeros().toPlainString() + "% on NACH bounce charge", why));
        }
        d.setBounceChargePaise(base);
        d.setBounceGstPaise(gst);
    }

    /** The school's head for the charge (matched by code, else by name), created on first use. */
    FeeHead head(String code, String name) {
        for (FeeHead h : heads.findByDeletedAtIsNullOrderBySortOrderAscNameAsc()) {
            if (code.equalsIgnoreCase(h.getCode()) || name.equalsIgnoreCase(h.getName())) {
                return h;
            }
        }
        FeeHead h = new FeeHead();
        h.setCode(code);
        h.setName(name);
        h.setCategory(FeeCategory.OTHER);
        h.setFrequency(FeeFrequency.ONE_TIME);
        h.setOptional(true);
        h.setDescription("Added automatically when an auto-debit bounces");
        h.setSortOrder(900);
        return heads.save(h);
    }

    /** Another attempt {@code retry_after_days} later while attempts are left and the mandate is active; it includes the bounce charge. */
    FeeDebit scheduleRetry(FeeDebit failed) {
        FeeAutoDebitSettings s = config.settings();
        FeeMandate m = mandates.findById(failed.getMandateId()).orElseThrow();
        if (failed.getAttemptNo() >= s.getMaxAttempts() || m.getStatus() != MandateStatus.ACTIVE) {
            return null;
        }
        LocalDate on = ctx.today().plusDays(s.getRetryAfterDays());
        FeeDebit d = new FeeDebit();
        d.setReference(OnlineSupport.reference("DB"));
        d.setMandateId(failed.getMandateId());
        d.setStudentId(failed.getStudentId());
        d.setLedgerId(failed.getLedgerId());
        d.setCycleMonth(failed.getCycleMonth());
        d.setAttemptNo((short) (failed.getAttemptNo() + 1));
        d.setSiblingRank(failed.getSiblingRank());
        d.setScheduledOn(on);
        d.setAmountPaise(Math.min(dueBy(failed.getLedgerId(), on), m.getMaxAmountPaise()));
        return debits.save(d);
    }

    private void notifyFailure(FeeDebit d, String reason, FeeDebit retry) {
        FeeMandate m = mandates.findById(d.getMandateId()).orElseThrow();
        UUID guardian = queries.mandateGuardians(m.getId()).get(d.getStudentId());
        String name = queries.briefs(List.of(d.getStudentId())).values().stream().findFirst().map(b -> b.studentName()).orElse("");
        String next = retry != null ? "We will try again on " + FeeMath.date(retry.getScheduledOn()) + "; you can also pay before that online or at the school."
                : "Please pay online or at the school office.";
        notifier.send(NotificationEvent.FEE_AUTO_DEBIT_FAILED, List.of(new FeeNotifier.Message(d.getStudentId(), Map.of("student_name", name,
                "amount", FeeMath.rupees(d.getAmountPaise()), "debit_date", FeeMath.date(d.getScheduledOn()), "reason", reason,
                "bounce_charge", FeeMath.rupees(d.getBounceChargePaise() + d.getBounceGstPaise()), "next_attempt", next), "FEE_DEBIT", d.getId(),
                "FEE_DEBIT_FAILED:" + d.getId(), guardian == null ? null : Set.of(guardian))), null);
    }

    private void recordGatewayPayment(GatewayProvider provider, String gatewayPaymentId, FeeDebit d, long amount, String method) {
        if (gatewayPaymentId == null) {
            return;
        }
        FeeGatewayPayment gp = new FeeGatewayPayment();
        gp.setProvider(provider);
        gp.setGatewayPaymentId(gatewayPaymentId);
        gp.setDebitId(d.getId());
        gp.setAmountPaise(amount);
        gp.setAllocatedPaise(amount);
        gp.setMethod(cut(method, 40));
        gatewayPayments.save(gp);
    }

    // ── Office actions and run log ─────────────────────────────────────────────────────

    /** Skips a debit not sent yet (the parent paid another way, a dispute ...). */
    public FeeDebit cancel(UUID debitId, String reason) {
        FeeDebit d = lockDebit(debitId);
        if (d.getStatus() != DebitStatus.SCHEDULED) {
            throw FeeContext.error("FEE_DEBIT_STATE", "Only a scheduled debit can be cancelled; this one is " + d.getStatus());
        }
        d.setStatus(DebitStatus.CANCELLED);
        d.setFailureReason(cut("Cancelled by " + ctx.actorName() + ": " + reason, 500));
        return debits.save(d);
    }

    public UUID startRun(String trigger) {
        FeeDebitRun r = new FeeDebitRun();
        r.setRunDate(ctx.today());
        r.setRunTrigger(trigger);
        r.setTriggeredBy(ctx.actorId());
        return runs.save(r).getId();
    }

    public RunView finishRun(UUID runId, int planned, int invites, int notices, int submitted, int skipped, int errors, String status, String detail) {
        FeeDebitRun r = runs.findById(runId).orElseThrow(() -> new ResourceNotFoundException("FeeDebitRun", runId));
        r.setPlanned(planned);
        r.setInvites(invites);
        r.setNotices(notices);
        r.setSubmitted(submitted);
        r.setSkipped(skipped);
        r.setErrors(errors);
        r.setStatus(status);
        r.setDetail(cut(detail, 1000));
        r.setFinishedAt(ctx.clock().instant());
        return view(runs.save(r));
    }

    static RunView view(FeeDebitRun r) {
        return new RunView(r.getId(), r.getRunDate(), r.getRunTrigger(), r.getStatus(), r.getPlanned(), r.getInvites(), r.getNotices(), r.getSubmitted(),
                r.getSkipped(), r.getErrors(), r.getDetail(), r.getStartedAt(), r.getFinishedAt());
    }

    private FeeDebit lockDebit(UUID id) {
        return debits.lock(id).orElseThrow(() -> new ResourceNotFoundException("FeeDebit", id));
    }

    static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
