package com.vidya.tenantdata.fee.service;

import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.tenantdata.academic.entity.AcademicYear;
import com.vidya.tenantdata.fee.dto.FeeDtos.Defaulters;
import com.vidya.tenantdata.fee.dto.FeeDtos.LedgerDetail;
import com.vidya.tenantdata.fee.dto.FeeDtos.LedgerRow;
import com.vidya.tenantdata.fee.dto.FeeDtos.LineView;
import com.vidya.tenantdata.fee.entity.FeeLedger;
import com.vidya.tenantdata.fee.enums.AgeingBucket;
import com.vidya.tenantdata.fee.enums.DueStatus;
import com.vidya.tenantdata.fee.repository.FeeLedgerLineRepository;
import com.vidya.tenantdata.fee.repository.FeeLedgerRepository;
import com.vidya.tenantdata.fee.repository.FeePaymentRepository;
import com.vidya.tenantdata.fee.repository.FeeQueries;
import com.vidya.tenantdata.fee.repository.FeeQueries.LedgerFilter;
import com.vidya.tenantdata.operations.entity.FeeHead;
import com.vidya.tenantdata.operations.entity.FeeSettings;
import com.vidya.tenantdata.operations.repository.FeeHeadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Reading ledgers: the school-wide list, defaulters with ageing, and one student's ledger with lines, payments and concessions. */
@Service
@RequiredArgsConstructor
@Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
public class FeeLedgerService {

    private final FeeContext ctx;
    private final FeeQueries queries;
    private final FeeLedgerRepository ledgers;
    private final FeeLedgerLineRepository lines;
    private final FeePaymentRepository payments;
    private final FeeHeadRepository heads;
    private final FeeConcessionService concessions;

    public Page<LedgerRow> list(UUID academicYearId, String search, UUID classId, UUID sectionId, Set<DueStatus> statuses, UUID feeHeadId,
                                UUID installmentId, int page, int size, List<String> sort) {
        AcademicYear y = ctx.year(academicYearId);
        LedgerFilter f = new LedgerFilter(y.getId(), null, null, classId, sectionId, search, statuses, feeHeadId, installmentId, false, null, null,
                ctx.today());
        return page(f, page, size, sort, List.<String[]>of(new String[]{"duePaise", "desc"}));
    }

    /** Ledgers with something past due, oldest debts first, with counts per ageing bucket. */
    public Defaulters defaulters(UUID academicYearId, UUID classId, UUID sectionId, AgeingBucket bucket, Long minDuePaise, String search, int page,
                                 int size, List<String> sort) {
        AcademicYear y = ctx.year(academicYearId);
        LedgerFilter f = new LedgerFilter(y.getId(), null, null, classId, sectionId, search, null, null, null, true, bucket, minDuePaise, ctx.today());
        return new Defaulters(ctx.today(), queries.buckets(f), page(f, page, size, sort, List.<String[]>of(new String[]{"ageingDays", "desc"},
                new String[]{"overduePaise", "desc"})));
    }

    public LedgerDetail detail(UUID ledgerId) {
        FeeLedger l = ledgers.findById(ledgerId).orElseThrow(() -> new ResourceNotFoundException("FeeLedger", ledgerId));
        ctx.checkStudent(l.getStudentId());
        return detail(l);
    }

    /** The student's ledger of the year (default current); 404 FEE_LEDGER_NOT_FOUND when not billed yet. */
    public LedgerDetail forStudent(UUID studentId, UUID academicYearId) {
        ctx.checkStudent(studentId);
        AcademicYear y = ctx.year(academicYearId);
        FeeLedger l = ledgers.findByStudentIdAndAcademicYearId(studentId, y.getId())
                .orElseThrow(() -> new ResourceNotFoundException("FeeLedger", "student " + studentId + " in " + y.getName()));
        return detail(l);
    }

    private LedgerDetail detail(FeeLedger l) {
        LocalDate today = ctx.today();
        LedgerRow row = queries.ledgerRow(l.getId(), today).orElseThrow(() -> new ResourceNotFoundException("FeeLedger", l.getId()));
        FeeSettings settings = ctx.settings(l.getAcademicYearId());
        Map<UUID, FeeHead> headById = heads.findAll().stream().collect(Collectors.toMap(FeeHead::getId, Function.identity()));
        List<LineView> lineViews = lines.findByLedgerIdOrderByDueDateAscInstallmentNoAscCreatedAtAsc(l.getId()).stream()
                .map(x -> FeeViews.line(x, headById.get(x.getFeeHeadId()), today, settings)).toList();
        long fine = lineViews.stream().mapToLong(LineView::lateFinePaise).sum();
        String yearName = ctx.year(l.getAcademicYearId()).getName();
        return new LedgerDetail(row, yearName, lineViews,
                payments.findByLedgerIdOrderByPaidOnAscCreatedAtAsc(l.getId()).stream().map(FeeViews::transaction).toList(),
                concessions.forStudent(l.getStudentId(), l.getAcademicYearId()), fine);
    }

    private Page<LedgerRow> page(LedgerFilter f, int page, int size, List<String> sort, List<String[]> defaultSort) {
        int p = Math.max(0, page);
        int s = Math.min(Math.max(1, size), 200);
        return new PageImpl<>(queries.ledgerRows(f, parseSort(sort, defaultSort), p, s), PageRequest.of(p, s), queries.countLedgers(f));
    }

    /** {@code sort=duePaise,desc} (repeatable); unknown keys are rejected with FEE_SORT_INVALID. */
    static List<String[]> parseSort(List<String> sort, List<String[]> fallback) {
        if (sort == null || sort.isEmpty()) {
            return fallback;
        }
        List<String[]> out = new ArrayList<>();
        for (int i = 0; i < sort.size(); i++) {
            String s = sort.get(i);
            if (s == null || s.isBlank()) {
                continue;
            }
            String[] parts = s.split(",");
            // Spring splits "a,desc" into two list items: re-join a bare direction with the key before it
            if (parts.length == 1 && i + 1 < sort.size() && isDirection(sort.get(i + 1))) {
                parts = new String[]{s, sort.get(++i)};
            }
            String key = parts[0].trim();
            if (!FeeQueries.sortable(key)) {
                throw FeeContext.error("FEE_SORT_INVALID", "Cannot sort by " + key);
            }
            out.add(new String[]{key, parts.length > 1 ? parts[1].trim() : "asc"});
        }
        return out.isEmpty() ? fallback : out;
    }

    private static boolean isDirection(String s) {
        return "asc".equalsIgnoreCase(s.trim()) || "desc".equalsIgnoreCase(s.trim());
    }
}
