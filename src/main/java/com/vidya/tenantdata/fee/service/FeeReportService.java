package com.vidya.tenantdata.fee.service;

import com.vidya.tenantdata.academic.entity.AcademicYear;
import com.vidya.tenantdata.fee.dto.FeeDtos.CollectionRow;
import com.vidya.tenantdata.fee.dto.FeeDtos.CollectionsReport;
import com.vidya.tenantdata.fee.dto.FeeDtos.DashboardFees;
import com.vidya.tenantdata.fee.dto.FeeDtos.FeeBreakdown;
import com.vidya.tenantdata.fee.dto.FeeDtos.FeeGroup;
import com.vidya.tenantdata.fee.dto.FeeDtos.FeeSummary;
import com.vidya.tenantdata.fee.dto.FeeDtos.FeeTrend;
import com.vidya.tenantdata.fee.dto.FeeDtos.FeeTrendPoint;
import com.vidya.tenantdata.fee.dto.FeeDtos.ModeShare;
import com.vidya.tenantdata.fee.dto.FeeDtos.ModeTotal;
import com.vidya.tenantdata.fee.repository.FeeQueries;
import com.vidya.tenantdata.fee.repository.FeeQueries.CollectionGroup;
import com.vidya.tenantdata.fee.repository.FeeQueries.GroupTotals;
import com.vidya.tenantdata.fee.repository.FeeQueries.Totals;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Fee totals: the fees summary cards, the collections report, and the figures the overview dashboard shows. */
@Service
@RequiredArgsConstructor
@Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
public class FeeReportService {

    static final int MAX_RANGE_DAYS = 366;

    private final FeeContext ctx;
    private final FeeQueries queries;

    /** Window default: the academic year start to today (capped at the year end). */
    public FeeSummary summary(UUID academicYearId, LocalDate from, LocalDate to, UUID classId, UUID sectionId) {
        AcademicYear y = ctx.year(academicYearId);
        LocalDate t = to != null ? to : min(ctx.today(), y.getEndDate());
        LocalDate f = from != null ? from : y.getStartDate();
        checkRange(f, t, false);
        Totals tot = queries.totals(y.getId(), classId, sectionId, ctx.today(), t);
        List<ModeTotal> modes = queries.collectedByMode(y.getId(), classId, sectionId, f, t);
        long collected = modes.stream().mapToLong(ModeTotal::amountPaise).sum();
        List<ModeShare> split = modes.stream().map(m -> new ModeShare(m.mode(), m.count(), m.amountPaise(), percent(m.amountPaise(), collected))).toList();
        return new FeeSummary(y.getId(), f, t, tot.ledgers(), tot.total(), tot.concession(), tot.total() - tot.concession(), tot.paid(), tot.pending(),
                tot.due(), tot.overdue(), tot.overdueLedgers(), tot.netDueByTo(), percent(tot.paidDueByTo(), tot.netDueByTo()), collected, split,
                queries.pendingConcessionRequests(y.getId()));
    }

    public CollectionsReport collections(UUID academicYearId, LocalDate from, LocalDate to, CollectionGroup groupBy) {
        AcademicYear y = ctx.year(academicYearId);
        LocalDate t = to != null ? to : ctx.today();
        LocalDate f = from != null ? from : t.withDayOfMonth(1);
        checkRange(f, t, true);
        CollectionGroup by = groupBy == null ? CollectionGroup.DAY : groupBy;
        List<CollectionRow> rows = queries.collections(y.getId(), f, t, by).stream()
                .map(r -> new CollectionRow(r.key(), r.label(), r.count(), r.amountPaise())).toList();
        return new CollectionsReport(y.getId(), f, t, by.name(), rows.stream().mapToLong(CollectionRow::amountPaise).sum(), rows);
    }

    // ── Dashboard ────────────────────────────────────────────────────────────────────

    public DashboardFees dashboard(UUID yearId, LocalDate from, LocalDate to) {
        LocalDate today = ctx.today();
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        Totals tot = queries.totals(yearId, null, null, today, to);
        long collected = sum(queries.collectedByMode(yearId, null, null, from, to));
        long previous = sum(queries.collectedByMode(yearId, null, null, from.minusDays(days), from.minusDays(1)));
        List<ModeTotal> todayModes = queries.collectedByMode(yearId, null, null, today, today);
        return new DashboardFees(collected, queries.netDueBetween(yearId, from, to), percent(tot.paidDueByTo(), tot.netDueByTo()), previous,
                tot.overdue(), tot.overdueLedgers(), tot.ledgers(), sum(todayModes), todayModes.stream().mapToLong(ModeTotal::count).sum(),
                queries.pendingCheques(yearId));
    }

    public FeeTrend trend(UUID yearId, LocalDate from, LocalDate to) {
        Map<LocalDate, Long> byDay = queries.collectedByDay(yearId, from, to);
        List<FeeTrendPoint> points = new ArrayList<>();
        long running = 0;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            long v = byDay.getOrDefault(d, 0L);
            running += v;
            points.add(new FeeTrendPoint(d, v, running));
        }
        return new FeeTrend(yearId, from, to, running, points);
    }

    public FeeBreakdown breakdown(UUID yearId) {
        LocalDate today = ctx.today();
        return new FeeBreakdown(yearId, today, queries.totalsByHead(yearId, today).stream().map(FeeReportService::group).toList(),
                queries.totalsByClass(yearId, today).stream().map(FeeReportService::group).toList());
    }

    private static FeeGroup group(GroupTotals g) {
        long net = g.billed() - g.concession();
        return new FeeGroup(g.id(), g.name(), g.billed(), g.concession(), net, g.paid(), g.pending(), g.due(), g.overdue(), percent(g.paid(), net));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────────

    /** part ÷ whole in %, 1 decimal; null when whole is 0. */
    static BigDecimal percent(long part, long whole) {
        return whole <= 0 ? null : BigDecimal.valueOf(part * 100.0 / whole).setScale(1, RoundingMode.HALF_UP);
    }

    private static long sum(List<ModeTotal> m) {
        return m.stream().mapToLong(ModeTotal::amountPaise).sum();
    }

    private static LocalDate min(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }

    private static void checkRange(LocalDate f, LocalDate t, boolean capped) {
        if (f.isAfter(t)) {
            throw FeeContext.error("FEE_RANGE_INVALID", "from must not be after to");
        }
        if (capped && ChronoUnit.DAYS.between(f, t) >= MAX_RANGE_DAYS) {
            throw FeeContext.error("FEE_RANGE_TOO_LONG", "Ask for at most " + MAX_RANGE_DAYS + " days at a time");
        }
    }
}
