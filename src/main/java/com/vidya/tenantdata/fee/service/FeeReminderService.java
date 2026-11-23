package com.vidya.tenantdata.fee.service;

import com.vidya.tenantdata.academic.entity.AcademicYear;
import com.vidya.tenantdata.fee.dto.FeeDtos.LedgerRow;
import com.vidya.tenantdata.fee.dto.FeeDtos.ReminderFilter;
import com.vidya.tenantdata.fee.dto.FeeDtos.ReminderRequest;
import com.vidya.tenantdata.fee.dto.FeeDtos.ReminderResult;
import com.vidya.tenantdata.fee.entity.FeeLedgerLine;
import com.vidya.tenantdata.fee.repository.FeeLedgerLineRepository;
import com.vidya.tenantdata.fee.repository.FeeQueries;
import com.vidya.tenantdata.fee.repository.FeeQueries.LedgerFilter;
import com.vidya.tenantdata.operations.entity.FeeSettings;
import com.vidya.tenantdata.operations.enums.NotificationChannel;
import com.vidya.tenantdata.operations.enums.NotificationEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Fee reminders to guardians. An overdue ledger gets FEE_OVERDUE (what is late, since when, the late fine so far); any other ledger with
 * something due gets FEE_DUE_REMINDER for its next due date. A ledger is reminded at most once a day per guardian and channel.
 */
@Service
@RequiredArgsConstructor
@Transactional(transactionManager = "tenantTransactionManager")
public class FeeReminderService {

    /** The most ledgers one request may remind. */
    static final int MAX_LEDGERS = 2000;

    private final FeeContext ctx;
    private final FeeQueries queries;
    private final FeeLedgerLineRepository lines;
    private final FeeNotifier notifier;

    public ReminderResult send(ReminderRequest in) {
        LocalDate today = ctx.today();
        List<LedgerRow> rows = targets(in, today);
        List<String> notes = new ArrayList<>();
        if (rows.size() > MAX_LEDGERS) {
            throw FeeContext.error("FEE_REMINDER_TOO_MANY", "That is " + rows.size() + " ledgers; remind at most " + MAX_LEDGERS + " at a time");
        }
        Map<UUID, List<FeeLedgerLine>> linesByLedger = rows.isEmpty() ? Map.of()
                : lines.findByLedgerIdIn(rows.stream().map(LedgerRow::id).toList()).stream().collect(Collectors.groupingBy(FeeLedgerLine::getLedgerId));
        Map<UUID, FeeSettings> settingsByYear = new HashMap<>();

        List<FeeNotifier.Message> overdue = new ArrayList<>();
        List<FeeNotifier.Message> upcoming = new ArrayList<>();
        Map<UUID, LedgerRow> ledgerOfStudent = new HashMap<>();
        int nothingDue = 0;
        for (LedgerRow r : rows) {
            List<FeeLedgerLine> open = linesByLedger.getOrDefault(r.id(), List.of()).stream().filter(l -> l.duePaise() > 0).toList();
            if (open.isEmpty()) {
                nothingDue++;
                continue;
            }
            ledgerOfStudent.put(r.studentId(), r);
            String key = "FEE_REMINDER:" + r.id() + ":" + today;
            if (r.overduePaise() > 0) {
                FeeSettings s = settingsByYear.computeIfAbsent(r.academicYearId(), ctx::settings);
                List<FeeLedgerLine> late = open.stream().filter(l -> l.getDueDate().isBefore(today)).toList();
                long fine = late.stream().mapToLong(l -> FeeMath.lateFine(l.duePaise(), l.getDueDate(), today, s)).sum();
                overdue.add(new FeeNotifier.Message(r.studentId(), Map.of("student_name", r.studentName(), "installment_name", names(late),
                        "amount", FeeMath.rupees(r.overduePaise()), "due_date", FeeMath.date(r.oldestOverdueDate()), "fine_amount", FeeMath.rupees(fine)),
                        "FEE_LEDGER", r.id(), key));
            } else {
                List<FeeLedgerLine> next = open.stream().filter(l -> l.getDueDate().equals(r.nextDueDate())).toList();
                upcoming.add(new FeeNotifier.Message(r.studentId(), Map.of("student_name", r.studentName(), "installment_name", names(next),
                        "amount", FeeMath.rupees(next.stream().mapToLong(FeeLedgerLine::duePaise).sum()), "due_date", FeeMath.date(r.nextDueDate())),
                        "FEE_LEDGER", r.id(), key));
            }
        }

        Set<NotificationChannel> channels = in.channels();
        int queued = 0;
        int unreachable = 0;
        for (Map.Entry<NotificationEvent, List<FeeNotifier.Message>> e : Map.of(NotificationEvent.FEE_OVERDUE, overdue,
                NotificationEvent.FEE_DUE_REMINDER, upcoming).entrySet()) {
            if (e.getValue().isEmpty()) {
                continue;
            }
            FeeNotifier.Outcome o = notifier.send(e.getKey(), e.getValue(), channels);
            if (!o.templateEnabled()) {
                notes.add(e.getKey() + " is switched off in the notification templates; " + e.getValue().size() + " ledger(s) not reminded");
                continue;
            }
            if (o.channels().isEmpty()) {
                notes.add("No channel to send " + e.getKey() + " on (none asked for is switched on for the school)");
                continue;
            }
            queued += o.queued();
            unreachable += o.unreachable().size();
            String channelText = o.channels().stream().map(Enum::name).sorted().collect(Collectors.joining(","));
            for (FeeNotifier.Message m : e.getValue()) {
                int n = o.perStudent().getOrDefault(m.studentId(), 0);
                if (n > 0) {
                    queries.recordReminder(m.refId(), e.getKey().name(), channelText, n, ctx.actorId());
                }
            }
        }
        if (queued == 0 && !ledgerOfStudent.isEmpty() && notes.isEmpty()) {
            notes.add("Nothing new was queued: these ledgers were already reminded today on these channels");
        }
        return new ReminderResult(rows.size(), unreachable, nothingDue, queued, notes);
    }

    private List<LedgerRow> targets(ReminderRequest in, LocalDate today) {
        if (in.ledgerIds() != null && !in.ledgerIds().isEmpty()) {
            Set<UUID> ids = new LinkedHashSet<>(in.ledgerIds());
            if (ids.size() > MAX_LEDGERS) {
                throw FeeContext.error("FEE_REMINDER_TOO_MANY", "Remind at most " + MAX_LEDGERS + " ledgers at a time");
            }
            return queries.ledgerRows(new LedgerFilter(null, ids, null, null, null, null, null, null, null, false, null, null, today),
                    List.of(), 0, MAX_LEDGERS);
        }
        ReminderFilter f = in.filter();
        if (f == null) {
            throw FeeContext.error("FEE_REMINDER_TARGET", "Give ledgerIds or a filter");
        }
        AcademicYear y = ctx.year(f.academicYearId());
        return queries.ledgerRows(new LedgerFilter(y.getId(), null, null, f.classId(), f.sectionId(), null, null, null, null, f.bucket() != null,
                f.bucket(), f.minDuePaise(), today), List.of(), 0, MAX_LEDGERS + 1);
    }

    /** "Term 1, Term 2" (instalment names, else line labels). */
    private static String names(List<FeeLedgerLine> ls) {
        return ls.stream().map(l -> l.getInstallmentName() != null ? l.getInstallmentName() : l.getLabel()).filter(Objects::nonNull)
                .distinct().collect(Collectors.joining(", "));
    }
}
