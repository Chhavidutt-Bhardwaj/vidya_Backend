package com.vidya.tenantdata.fee.service;

import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.tenantdata.academic.entity.AcademicYear;
import com.vidya.tenantdata.fee.dto.FeeDtos.AddChargeRequest;
import com.vidya.tenantdata.fee.dto.FeeDtos.ClassNotBilled;
import com.vidya.tenantdata.fee.dto.FeeDtos.GenerateInvoicesRequest;
import com.vidya.tenantdata.fee.dto.FeeDtos.GenerateInvoicesResult;
import com.vidya.tenantdata.fee.dto.FeeDtos.LineView;
import com.vidya.tenantdata.fee.entity.FeeConcessionRequest;
import com.vidya.tenantdata.fee.entity.FeeLedger;
import com.vidya.tenantdata.fee.entity.FeeLedgerLine;
import com.vidya.tenantdata.fee.enums.LineSource;
import com.vidya.tenantdata.fee.enums.LineStatus;
import com.vidya.tenantdata.fee.repository.FeeLedgerLineRepository;
import com.vidya.tenantdata.fee.repository.FeeLedgerRepository;
import com.vidya.tenantdata.fee.repository.FeeQueries;
import com.vidya.tenantdata.fee.repository.FeeQueries.RollEntry;
import com.vidya.tenantdata.operations.entity.FeeHead;
import com.vidya.tenantdata.operations.entity.FeeInstallment;
import com.vidya.tenantdata.operations.entity.FeeStructure;
import com.vidya.tenantdata.operations.entity.FeeStructureItem;
import com.vidya.tenantdata.operations.enums.ConfigStatus;
import com.vidya.tenantdata.operations.enums.FeeAppliesTo;
import com.vidya.tenantdata.operations.enums.FeeFrequency;
import com.vidya.tenantdata.operations.enums.FeeStructureStatus;
import com.vidya.tenantdata.operations.repository.FeeHeadRepository;
import com.vidya.tenantdata.operations.repository.FeeInstallmentRepository;
import com.vidya.tenantdata.operations.repository.FeeStructureItemRepository;
import com.vidya.tenantdata.operations.repository.FeeStructureRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Bills students from their class fee structure: one ledger per student and year, one line per compulsory fee head per instalment.
 * Running it again only adds what is missing (a new instalment, a head added to the structure, a student admitted later), so it is safe
 * to repeat. Optional heads (transport, hostel ...) are not billed automatically: add them as charges.
 */
@Service
@RequiredArgsConstructor
@Transactional(transactionManager = "tenantTransactionManager")
public class FeeInvoiceService {

    /** A ledger is billed in chunks so a school-wide run does not hold every row in memory at once. */
    private static final int CHUNK = 500;

    private final FeeContext ctx;
    private final FeeQueries queries;
    private final FeeLedgerRepository ledgers;
    private final FeeLedgerLineRepository lines;
    private final FeeStructureRepository structures;
    private final FeeStructureItemRepository structureItems;
    private final FeeHeadRepository heads;
    private final FeeInstallmentRepository installments;
    private final FeeConcessionService concessions;

    private record Key(UUID headId, int installmentNo) {
    }

    public GenerateInvoicesResult generate(GenerateInvoicesRequest in) {
        AcademicYear y = ctx.year(in.academicYearId());
        List<FeeInstallment> plan = installments.findByAcademicYearIdOrderByInstallmentNoAsc(y.getId());
        if (plan.isEmpty()) {
            throw FeeContext.error("FEE_NO_INSTALLMENTS", "Set up the instalment plan of " + y.getName() + " before generating invoices");
        }
        if (in.installmentId() != null && plan.stream().noneMatch(i -> i.getId().equals(in.installmentId()))) {
            throw FeeContext.error("FEE_INSTALLMENT_NOT_IN_YEAR", "The instalment is not part of " + y.getName());
        }
        Map<UUID, FeeHead> headById = heads.findByDeletedAtIsNullOrderBySortOrderAscNameAsc().stream()
                .collect(Collectors.toMap(FeeHead::getId, Function.identity()));
        List<FeeStructure> active = structures.findByAcademicYearIdAndDeletedAtIsNull(y.getId()).stream()
                .filter(s -> s.getStatus() == FeeStructureStatus.ACTIVE).toList();
        Map<UUID, List<FeeStructure>> byClass = active.stream().collect(Collectors.groupingBy(FeeStructure::getAcademicClassId));
        Map<UUID, List<FeeStructureItem>> itemsByStructure = active.isEmpty() ? Map.of()
                : structureItems.findByFeeStructureIdIn(active.stream().map(FeeStructure::getId).toList()).stream()
                .collect(Collectors.groupingBy(FeeStructureItem::getFeeStructureId));
        Map<UUID, List<FeeLedgerLine>> template = new HashMap<>();
        for (FeeStructure s : active) {
            template.put(s.getId(), plannedLines(itemsByStructure.getOrDefault(s.getId(), List.of()), headById, plan, in.installmentId()));
        }

        List<RollEntry> roll = queries.roll(y.getId(), in.classIds());
        Map<UUID, long[]> notBilled = new LinkedHashMap<>();
        Map<UUID, String> notBilledReason = new HashMap<>();
        List<Map.Entry<RollEntry, FeeStructure>> billable = new ArrayList<>();
        for (RollEntry e : roll) {
            FeeStructure s = pick(byClass.get(e.classId()), e.newAdmission());
            if (s == null) {
                notBilled.computeIfAbsent(e.classId(), k -> new long[1])[0]++;
                notBilledReason.putIfAbsent(e.classId(), byClass.containsKey(e.classId())
                        ? "No active fee structure applies to " + (e.newAdmission() ? "new admissions" : "existing students")
                        : "No active fee structure for this class");
            } else {
                billable.add(Map.entry(e, s));
            }
        }

        Counts c = new Counts();
        for (int i = 0; i < billable.size(); i += CHUNK) {
            billChunk(y, billable.subList(i, Math.min(billable.size(), i + CHUNK)), template, in.dryRun(), c);
        }
        Map<UUID, String> classNames = queries.classNames(notBilled.keySet());
        List<ClassNotBilled> skipped = notBilled.entrySet().stream()
                .map(e -> new ClassNotBilled(e.getKey(), classNames.get(e.getKey()), e.getValue()[0], notBilledReason.get(e.getKey()))).toList();
        return new GenerateInvoicesResult(in.dryRun(), roll.size(), c.studentsBilled, c.ledgersCreated, c.linesCreated, c.alreadyBilled,
                c.totalPaise, c.concessionPaise, skipped);
    }

    private static final class Counts {
        int studentsBilled;
        int ledgersCreated;
        int linesCreated;
        int alreadyBilled;
        long totalPaise;
        long concessionPaise;
    }

    private void billChunk(AcademicYear y, List<Map.Entry<RollEntry, FeeStructure>> chunk, Map<UUID, List<FeeLedgerLine>> template,
                           boolean dryRun, Counts c) {
        List<UUID> studentIds = chunk.stream().map(e -> e.getKey().studentId()).toList();
        Map<UUID, FeeLedger> existing = ledgers.findByAcademicYearIdAndStudentIdIn(y.getId(), studentIds).stream()
                .collect(Collectors.toMap(FeeLedger::getStudentId, Function.identity()));
        Map<UUID, Set<Key>> billed = new HashMap<>();
        if (!existing.isEmpty()) {
            Map<UUID, UUID> studentOfLedger = existing.values().stream().collect(Collectors.toMap(FeeLedger::getId, FeeLedger::getStudentId));
            for (FeeLedgerLine l : lines.findByLedgerIdIn(studentOfLedger.keySet())) {
                if (l.getSource() == LineSource.STRUCTURE) {
                    billed.computeIfAbsent(studentOfLedger.get(l.getLedgerId()), k -> new HashSet<>()).add(new Key(l.getFeeHeadId(), l.getInstallmentNo()));
                }
            }
        }
        Map<UUID, List<FeeConcessionRequest>> approved = dryRun ? concessions.approvedFor(y.getId(), studentIds) : Map.of();

        for (Map.Entry<RollEntry, FeeStructure> e : chunk) {
            RollEntry r = e.getKey();
            Set<Key> have = billed.getOrDefault(r.studentId(), Set.of());
            List<FeeLedgerLine> fresh = new ArrayList<>();
            for (FeeLedgerLine t : template.get(e.getValue().getId())) {
                if (have.contains(new Key(t.getFeeHeadId(), t.getInstallmentNo()))) {
                    c.alreadyBilled++;
                } else {
                    fresh.add(copy(t));
                }
            }
            if (fresh.isEmpty()) {
                continue;
            }
            c.studentsBilled++;
            c.linesCreated += fresh.size();
            c.totalPaise += fresh.stream().mapToLong(FeeLedgerLine::getAmountPaise).sum();
            FeeLedger ledger = existing.get(r.studentId());
            if (ledger == null) {
                c.ledgersCreated++;
            }
            if (dryRun) {
                for (FeeConcessionRequest cr : approved.getOrDefault(r.studentId(), List.of())) {
                    c.concessionPaise += FeeMath.concessionShares(cr, fresh, new IdentityHashMap<>()).values().stream().mapToLong(Long::longValue).sum();
                }
                continue;
            }
            if (ledger == null) {
                ledger = new FeeLedger();
                ledger.setInvoiceNumber(FeeMath.number("INV", y.getStartDate(), queries.nextNumber("INVOICE", y.getId())));
                ledger.setStudentId(r.studentId());
                ledger.setAcademicYearId(y.getId());
                ledger.setCreatedBy(ctx.actorId());
            } else {
                ledger = ctx.lockLedger(ledger.getId());
            }
            ledger.setAcademicClassId(r.classId());
            ledger.setSectionId(r.sectionId());
            ledger.setFeeStructureId(e.getValue().getId());
            ledger = ledgers.save(ledger);
            for (FeeLedgerLine l : fresh) {
                l.setLedgerId(ledger.getId());
                l.setCreatedBy(ctx.actorId());
            }
            lines.saveAll(fresh);
            c.concessionPaise += concessions.applyApproved(r.studentId(), y.getId(), fresh);
        }
    }

    /** The structure's compulsory heads as lines (not yet tied to a ledger), restricted to one instalment if asked. */
    static List<FeeLedgerLine> plannedLines(List<FeeStructureItem> items, Map<UUID, FeeHead> headById, List<FeeInstallment> plan, UUID onlyInstallment) {
        List<FeeLedgerLine> out = new ArrayList<>();
        for (FeeStructureItem item : items) {
            FeeHead h = headById.get(item.getFeeHeadId());
            if (h == null || h.isOptional() || h.getStatus() != ConfigStatus.ACTIVE) {
                continue;
            }
            long annual = FeeMath.toPaise(item.getAmount()) * h.getFrequency().timesPerYear();
            long[] parts = new long[plan.size()];
            if (h.getFrequency() == FeeFrequency.ONE_TIME) {
                parts[0] = annual;
            } else {
                parts = FeeMath.split(annual, plan);
            }
            for (int k = 0; k < plan.size(); k++) {
                FeeInstallment inst = plan.get(k);
                if (parts[k] <= 0 || (onlyInstallment != null && !onlyInstallment.equals(inst.getId()))) {
                    continue;
                }
                FeeLedgerLine l = new FeeLedgerLine();
                l.setFeeHeadId(h.getId());
                l.setInstallmentId(inst.getId());
                l.setInstallmentNo(inst.getInstallmentNo());
                l.setInstallmentName(inst.getName());
                l.setLabel(h.getName() + " — " + inst.getName());
                l.setSource(LineSource.STRUCTURE);
                l.setAmountPaise(parts[k]);
                l.setDueDate(inst.getDueDate());
                out.add(l);
            }
        }
        return out;
    }

    /** The structure that applies to the student: one for their admission type, else one for ALL. */
    static FeeStructure pick(List<FeeStructure> ofClass, boolean newAdmission) {
        if (ofClass == null) {
            return null;
        }
        FeeAppliesTo mine = newAdmission ? FeeAppliesTo.NEW_ADMISSION : FeeAppliesTo.EXISTING;
        return ofClass.stream().filter(s -> s.getAppliesTo() == mine).findFirst()
                .orElseGet(() -> ofClass.stream().filter(s -> s.getAppliesTo() == FeeAppliesTo.ALL).findFirst().orElse(null));
    }

    private static FeeLedgerLine copy(FeeLedgerLine t) {
        FeeLedgerLine l = new FeeLedgerLine();
        l.setFeeHeadId(t.getFeeHeadId());
        l.setInstallmentId(t.getInstallmentId());
        l.setInstallmentNo(t.getInstallmentNo());
        l.setInstallmentName(t.getInstallmentName());
        l.setLabel(t.getLabel());
        l.setSource(t.getSource());
        l.setAmountPaise(t.getAmountPaise());
        l.setDueDate(t.getDueDate());
        return l;
    }

    // ── One-off charges and cancelling ───────────────────────────────────────────────

    public LineView addCharge(UUID ledgerId, AddChargeRequest in) {
        FeeLedger ledger = ctx.lockLedger(ledgerId);
        FeeHead h = heads.findByIdAndDeletedAtIsNull(in.feeHeadId()).orElseThrow(() -> new ResourceNotFoundException("FeeHead", in.feeHeadId()));
        if (h.getStatus() != ConfigStatus.ACTIVE) {
            throw FeeContext.error("FEE_HEAD_INACTIVE", "Fee head " + h.getName() + " is not active");
        }
        FeeLedgerLine l = new FeeLedgerLine();
        l.setLedgerId(ledger.getId());
        l.setFeeHeadId(h.getId());
        l.setSource(LineSource.CHARGE);
        l.setLabel(in.label() == null || in.label().isBlank() ? h.getName() : in.label().trim());
        l.setAmountPaise(in.amountPaise());
        l.setDueDate(in.dueDate() != null ? in.dueDate() : ctx.today());
        l.setReason(in.reason().trim());
        l.setCreatedBy(ctx.actorId());
        lines.save(l);
        return FeeViews.line(l, h, ctx.today(), null);
    }

    /** Only a line with nothing paid or waiting to clear can be cancelled; concessions on it are given back to their requests. */
    public LineView cancelLine(UUID ledgerId, UUID lineId, String reason) {
        ctx.lockLedger(ledgerId);
        FeeLedgerLine l = lines.findById(lineId).filter(x -> Objects.equals(x.getLedgerId(), ledgerId))
                .orElseThrow(() -> new ResourceNotFoundException("FeeLedgerLine", lineId));
        if (l.getStatus() == LineStatus.CANCELLED) {
            throw FeeContext.error("FEE_LINE_CANCELLED", "This line is already cancelled");
        }
        if (l.getPaidPaise() > 0 || l.getPendingPaise() > 0) {
            throw FeeContext.error("FEE_LINE_HAS_PAYMENTS", "Money has been received against this line; reverse the payment before cancelling it");
        }
        if (reason == null || reason.isBlank()) {
            throw FeeContext.error("FEE_REASON_REQUIRED", "Give a reason for cancelling the line");
        }
        concessions.releaseLine(l);
        l.setStatus(LineStatus.CANCELLED);
        l.setCancelledAt(ctx.clock().instant());
        l.setCancelledBy(ctx.actorId());
        l.setCancelReason(reason.trim());
        lines.save(l);
        return FeeViews.line(l, heads.findById(l.getFeeHeadId()).orElse(null), ctx.today(), null);
    }
}
