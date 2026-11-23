package com.vidya.tenantdata.fee.online.service;

import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.tenantdata.fee.online.dto.OnlineFeeDtos.DebitSummary;
import com.vidya.tenantdata.fee.online.dto.OnlineFeeDtos.RunView;
import com.vidya.tenantdata.fee.online.dto.OnlineFeeDtos.StatusTotal;
import com.vidya.tenantdata.fee.online.entity.FeeAutoDebitSettings;
import com.vidya.tenantdata.fee.online.entity.FeeDebit;
import com.vidya.tenantdata.fee.online.enums.DebitStatus;
import com.vidya.tenantdata.fee.online.enums.MandateStatus;
import com.vidya.tenantdata.fee.online.gateway.FeeGatewayRegistry;
import com.vidya.tenantdata.fee.online.gateway.GatewayException;
import com.vidya.tenantdata.fee.online.gateway.GatewayModels.ChargeSubmitted;
import com.vidya.tenantdata.fee.online.repository.FeeDebitRepository;
import com.vidya.tenantdata.fee.online.repository.FeeDebitRunRepository;
import com.vidya.tenantdata.fee.online.repository.FeeMandateRepository;
import com.vidya.tenantdata.fee.online.repository.OnlineFeeQueries;
import com.vidya.tenantdata.fee.online.repository.OnlineFeeQueries.DebitFilter;
import com.vidya.tenantdata.fee.online.repository.OnlineFeeQueries.DebitRow;
import com.vidya.tenantdata.fee.service.FeeContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The daily auto-debit job of one school (scheduler or "run now"), like infi_payment's EnachSchedulerService: plan → invite → notices →
 * submit. Every step can be repeated safely (unique debits per attempt, locked rows, deduplicated messages), so several servers or a
 * second run the same day do no harm. Gateway calls happen outside transactions; the ledger is held through a pending payment instead.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutoDebitService {

    private final AutoDebitSteps steps;
    private final OnlineFeeConfigService config;
    private final FeeGatewayRegistry gateways;
    private final FeeDebitRepository debits;
    private final FeeDebitRunRepository runs;
    private final FeeMandateRepository mandates;
    private final OnlineFeeQueries queries;
    private final FeeContext ctx;

    /**
     * @param planMonth also (re)plan this month even outside its planning window, e.g. after the server was down over the debit day
     */
    public RunView run(String trigger, YearMonth planMonth) {
        FeeAutoDebitSettings s = config.settings();
        if (!s.isEnabled() && "SCHEDULED".equals(trigger)) {
            return null;
        }
        UUID runId = steps.startRun(trigger);
        int planned = 0, invites = 0, notices = 0, submitted = 0, skipped = 0, errors = 0;
        List<String> notes = new ArrayList<>();
        String status = "COMPLETED";
        try {
            if (!s.isEnabled()) {
                notes.add("Auto-debit is switched off");
            } else {
                LocalDate today = ctx.today();
                int lead = s.noticeDayList().stream().findFirst().orElse(1) + 1;
                for (YearMonth cycle : List.of(YearMonth.from(today), YearMonth.from(today).plusMonths(1))) {
                    LocalDate debitDate = cycle.atDay(s.getDebitDay());
                    long ahead = ChronoUnit.DAYS.between(today, debitDate);
                    boolean forced = cycle.equals(planMonth);
                    if (forced || (ahead >= 0 && ahead <= lead)) {
                        planned += steps.plan(cycle.atDay(1), forced);
                    }
                }
                invites = steps.invite(OnlineFeeConfigService.nextDebitDate(s.getDebitDay(), today.plusDays(1)));
                notices = steps.notices();
                for (UUID id : steps.dueForSubmission()) {
                    switch (submit(id)) {
                        case SUBMITTED -> submitted++;
                        case SKIPPED, CANCELLED -> skipped++;
                        default -> errors++;
                    }
                }
            }
        } catch (RuntimeException e) {
            log.error("Auto-debit run failed: {}", e.getMessage(), e);
            status = "FAILED";
            notes.add(e.getMessage());
        }
        return steps.finishRun(runId, planned, invites, notices, submitted, skipped, errors, status, notes.isEmpty() ? null : String.join("; ", notes));
    }

    /** Holds the due, sends the charge, records what the gateway said. */
    DebitStatus submit(UUID debitId) {
        AutoDebitSteps.Prepared p;
        try {
            p = steps.prepare(debitId);
        } catch (RuntimeException e) {
            log.warn("Debit {} could not be prepared: {}", debitId, e.getMessage());
            return DebitStatus.ERROR;
        }
        if (p == null) {
            return debits.findById(debitId).map(FeeDebit::getStatus).filter(st -> st != DebitStatus.SUBMITTED).orElse(DebitStatus.SKIPPED);
        }
        try {
            ChargeSubmitted r = gateways.get(p.provider()).charge(config.credentials(p.provider()), p.charge());
            steps.submitted(p.debitId(), r);
            return DebitStatus.SUBMITTED;
        } catch (GatewayException | com.vidya.common.exception.BusinessException e) {
            log.warn("Debit {} refused by {}: {}", p.charge().reference(), p.provider(), e.getMessage());
            steps.submitFailed(p.debitId(), e.getMessage());
            return DebitStatus.ERROR;
        }
    }

    // ── Views ──────────────────────────────────────────────────────────────────────────

    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public Page<DebitRow> list(YearMonth month, Set<DebitStatus> statuses, UUID mandateId, UUID studentId, int page, int size) {
        int p = Math.max(0, page);
        int sz = Math.min(Math.max(1, size), 200);
        DebitFilter f = new DebitFilter(month == null ? null : month.atDay(1), statuses, mandateId, studentId);
        return new PageImpl<>(queries.debits(f, p, sz), PageRequest.of(p, sz), queries.countDebits(f));
    }

    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public DebitSummary summary(YearMonth month) {
        FeeAutoDebitSettings s = config.settings();
        YearMonth m = month != null ? month : YearMonth.from(ctx.today());
        Map<DebitStatus, long[]> totals = queries.debitTotals(m.atDay(1));
        List<StatusTotal> rows = totals.entrySet().stream().map(e -> new StatusTotal(e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2])).toList();
        long collected = totals.getOrDefault(DebitStatus.SUCCESS, new long[3])[1];
        long failed = totals.getOrDefault(DebitStatus.FAILED, new long[3])[1];
        return new DebitSummary(m.atDay(1), m.atDay(s.getDebitDay()), rows, collected, failed, mandates.countByStatus(MandateStatus.ACTIVE),
                mandates.countByStatus(MandateStatus.PENDING));
    }

    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public List<RunView> runs(int limit) {
        return runs.findAllByOrderByStartedAtDesc(PageRequest.of(0, Math.min(Math.max(1, limit), 100))).stream().map(AutoDebitSteps::view).toList();
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public DebitRow cancel(UUID debitId, String reason) {
        FeeDebit d = steps.cancel(debitId, reason);
        return queries.debits(new DebitFilter(null, null, d.getMandateId(), d.getStudentId()), 0, 50).stream().filter(r -> r.id().equals(d.getId()))
                .findFirst().orElseThrow(() -> new ResourceNotFoundException("FeeDebit", debitId));
    }
}
