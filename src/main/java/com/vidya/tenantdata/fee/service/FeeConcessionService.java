package com.vidya.tenantdata.fee.service;

import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.tenantdata.academic.entity.AcademicYear;
import com.vidya.tenantdata.fee.dto.FeeDtos.ApproveRequest;
import com.vidya.tenantdata.fee.dto.FeeDtos.ConcessionView;
import com.vidya.tenantdata.fee.dto.FeeDtos.CreateConcessionRequest;
import com.vidya.tenantdata.fee.dto.FeeDtos.RejectRequest;
import com.vidya.tenantdata.fee.dto.FeeDtos.RevokeRequest;
import com.vidya.tenantdata.fee.entity.FeeConcessionRequest;
import com.vidya.tenantdata.fee.entity.FeeLedger;
import com.vidya.tenantdata.fee.entity.FeeLedgerLine;
import com.vidya.tenantdata.fee.enums.ConcessionRequestStatus;
import com.vidya.tenantdata.fee.repository.FeeConcessionRequestRepository;
import com.vidya.tenantdata.fee.repository.FeeLedgerLineRepository;
import com.vidya.tenantdata.fee.repository.FeeLedgerRepository;
import com.vidya.tenantdata.fee.repository.FeeQueries;
import com.vidya.tenantdata.fee.repository.FeeQueries.ConcessionFilter;
import com.vidya.tenantdata.operations.entity.FeeConcessionType;
import com.vidya.tenantdata.operations.enums.ConcessionKind;
import com.vidya.tenantdata.operations.enums.ConfigStatus;
import com.vidya.tenantdata.operations.repository.FeeConcessionTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Concession requests: someone with FEE_MANAGE asks, someone else with FEE_APPROVE approves (a type that does not require approval is
 * approved on creation). Approval takes the concession off the student's ledger lines; revoking gives back what lines due on or after
 * {@code effectiveFrom} received. Ledgers billed later get approved concessions when they are generated.
 */
@Service
@RequiredArgsConstructor
@Transactional(transactionManager = "tenantTransactionManager")
public class FeeConcessionService {

    private final FeeContext ctx;
    private final FeeQueries queries;
    private final FeeConcessionRequestRepository requests;
    private final FeeConcessionTypeRepository types;
    private final FeeLedgerRepository ledgers;
    private final FeeLedgerLineRepository lines;

    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public Page<ConcessionView> list(UUID academicYearId, ConcessionRequestStatus status, UUID concessionTypeId, UUID classId, String search,
                                     int page, int size) {
        AcademicYear y = ctx.year(academicYearId);
        ConcessionFilter f = new ConcessionFilter(y.getId(), null, status, concessionTypeId, classId, search, null);
        int p = Math.max(0, page);
        int s = Math.min(Math.max(1, size), 200);
        return new PageImpl<>(queries.concessions(f, p, s), PageRequest.of(p, s), queries.countConcessions(f));
    }

    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public ConcessionView get(UUID id) {
        return view(id);
    }

    /** A student's requests of the year (for the ledger screen). */
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public List<ConcessionView> forStudent(UUID studentId, UUID academicYearId) {
        return queries.concessions(new ConcessionFilter(academicYearId, studentId, null, null, null, null, null), 0, 200);
    }

    public ConcessionView create(CreateConcessionRequest in) {
        AcademicYear y = ctx.year(in.academicYearId());
        if (!queries.studentExists(in.studentId())) {
            throw new ResourceNotFoundException("Student", in.studentId());
        }
        FeeConcessionType type = types.findByIdAndDeletedAtIsNull(in.concessionTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("FeeConcessionType", in.concessionTypeId()));
        if (type.getStatus() != ConfigStatus.ACTIVE) {
            throw FeeContext.error("FEE_CONCESSION_TYPE_INACTIVE", "Concession type " + type.getName() + " is not active");
        }
        if (requests.existsByStudentIdAndAcademicYearIdAndConcessionTypeIdAndStatusIn(in.studentId(), y.getId(), type.getId(),
                List.of(ConcessionRequestStatus.PENDING, ConcessionRequestStatus.APPROVED))) {
            throw duplicate(type);
        }
        FeeConcessionRequest r = new FeeConcessionRequest();
        r.setRequestNumber(FeeMath.number("CON", y.getStartDate(), queries.nextNumber("CONCESSION", y.getId())));
        r.setStudentId(in.studentId());
        r.setAcademicYearId(y.getId());
        r.setConcessionTypeId(type.getId());
        r.setKind(type.getKind());
        r.setFeeHeadId(type.getFeeHeadId());
        if (type.getKind() == ConcessionKind.PERCENT) {
            if (in.amountPaise() != null) {
                throw FeeContext.error("FEE_CONCESSION_VALUE", type.getName() + " is a percent concession; send percent, not amountPaise");
            }
            BigDecimal pct = in.percent() != null ? in.percent() : type.getValue();
            if (pct == null || pct.signum() <= 0 || pct.compareTo(BigDecimal.valueOf(100)) > 0) {
                throw FeeContext.error("FEE_CONCESSION_VALUE", "The percent must be above 0 and at most 100");
            }
            r.setPercent(pct.setScale(2, RoundingMode.HALF_UP));
        } else {
            if (in.percent() != null) {
                throw FeeContext.error("FEE_CONCESSION_VALUE", type.getName() + " is a fixed concession; send amountPaise, not percent");
            }
            long amount = in.amountPaise() != null ? in.amountPaise() : FeeMath.toPaise(type.getValue());
            if (amount <= 0) {
                throw FeeContext.error("FEE_CONCESSION_VALUE", "The amount must be above 0");
            }
            r.setAmountPaise(amount);
        }
        r.setReason(in.reason().trim());
        r.setRequestedBy(ctx.actorId());
        r.setRequestedByName(ctx.actorName());
        try {
            requests.saveAndFlush(r);
        } catch (DataIntegrityViolationException e) {
            throw duplicate(type);  // a request for the same student and type created at the same moment
        }
        if (!type.isRequiresApproval()) {
            approveNow(r, null);
        }
        return view(r.getId());
    }

    public ConcessionView approve(UUID id, ApproveRequest in) {
        FeeConcessionRequest r = pending(id);
        if (Objects.equals(r.getRequestedBy(), ctx.actorId())) {
            throw FeeContext.error("FEE_CONCESSION_SELF_APPROVAL", "A concession must be approved by someone other than who asked for it");
        }
        approveNow(r, in == null ? null : in.remarks());
        return view(id);
    }

    public ConcessionView reject(UUID id, RejectRequest in) {
        FeeConcessionRequest r = pending(id);
        r.setStatus(ConcessionRequestStatus.REJECTED);
        review(r, in.remarks());
        return view(id);
    }

    public ConcessionView revoke(UUID id, RevokeRequest in) {
        FeeConcessionRequest r = requests.findById(id).orElseThrow(() -> new ResourceNotFoundException("FeeConcessionRequest", id));
        if (r.getStatus() != ConcessionRequestStatus.APPROVED) {
            throw FeeContext.error("FEE_CONCESSION_STATE", "Only an approved concession can be revoked (this one is " + r.getStatus() + ")");
        }
        LocalDate from = in.effectiveFrom() != null ? in.effectiveFrom() : ctx.today();
        ledgers.findByStudentIdAndAcademicYearId(r.getStudentId(), r.getAcademicYearId()).ifPresent(l -> {
            ctx.lockLedger(l.getId());
            Map<UUID, Long> given = queries.lineConcessions(r.getId());
            for (FeeLedgerLine line : lines.findAllById(given.keySet())) {
                if (line.getDueDate().isBefore(from)) {
                    continue;
                }
                long back = given.get(line.getId());
                line.setConcessionPaise(line.getConcessionPaise() - back);
                queries.removeLineConcession(line.getId(), r.getId());
                r.setAppliedPaise(r.getAppliedPaise() - back);
            }
        });
        r.setStatus(ConcessionRequestStatus.REVOKED);
        r.setRevokedBy(ctx.actorId());
        r.setRevokedAt(ctx.clock().instant());
        r.setRevokeReason(in.reason().trim());
        r.setEffectiveFrom(from);
        requests.save(r);
        return view(id);
    }

    /**
     * Applies the student's approved concessions to lines (new lines on generation, or every line on approval). Returns paise given.
     * Lines must belong to a ledger whose lock the caller holds, and must already be saved.
     */
    long applyApproved(UUID studentId, UUID academicYearId, List<FeeLedgerLine> ledgerLines) {
        long total = 0;
        for (FeeConcessionRequest r : requests.findByAcademicYearIdAndStudentIdInAndStatus(academicYearId, List.of(studentId),
                ConcessionRequestStatus.APPROVED)) {
            total += apply(r, ledgerLines);
        }
        return total;
    }

    /** A line being cancelled gives its concessions back to their requests (a FIXED request can then be spent elsewhere). */
    void releaseLine(FeeLedgerLine line) {
        for (Map.Entry<UUID, Long> e : queries.concessionsOnLine(line.getId()).entrySet()) {
            requests.findById(e.getKey()).ifPresent(r -> {
                r.setAppliedPaise(r.getAppliedPaise() - e.getValue());
                requests.save(r);
            });
            queries.removeLineConcession(line.getId(), e.getKey());
        }
        line.setConcessionPaise(0);
    }

    /** Dry-run estimate: what approved concessions would take off unsaved lines, per student. */
    Map<UUID, List<FeeConcessionRequest>> approvedFor(UUID academicYearId, List<UUID> studentIds) {
        Map<UUID, List<FeeConcessionRequest>> out = new HashMap<>();
        if (studentIds.isEmpty()) {
            return out;
        }
        requests.findByAcademicYearIdAndStudentIdInAndStatus(academicYearId, studentIds, ConcessionRequestStatus.APPROVED)
                .forEach(r -> out.computeIfAbsent(r.getStudentId(), k -> new java.util.ArrayList<>()).add(r));
        return out;
    }

    private void approveNow(FeeConcessionRequest r, String remarks) {
        r.setStatus(ConcessionRequestStatus.APPROVED);
        review(r, remarks);
        ledgers.findByStudentIdAndAcademicYearId(r.getStudentId(), r.getAcademicYearId()).ifPresent(l -> {
            FeeLedger locked = ctx.lockLedger(l.getId());
            apply(r, lines.findByLedgerIdOrderByDueDateAscInstallmentNoAscCreatedAtAsc(locked.getId()));
        });
        requests.save(r);
    }

    private long apply(FeeConcessionRequest r, List<FeeLedgerLine> ledgerLines) {
        Map<UUID, Long> byId = queries.lineConcessions(r.getId());
        Map<FeeLedgerLine, Long> given = new IdentityHashMap<>();
        ledgerLines.forEach(l -> {
            if (byId.containsKey(l.getId())) {
                given.put(l, byId.get(l.getId()));
            }
        });
        long total = 0;
        for (Map.Entry<FeeLedgerLine, Long> e : FeeMath.concessionShares(r, ledgerLines, given).entrySet()) {
            FeeLedgerLine l = e.getKey();
            l.setConcessionPaise(l.getConcessionPaise() + e.getValue());
            lines.save(l);
            queries.addLineConcession(l.getId(), r.getId(), e.getValue());
            r.setAppliedPaise(r.getAppliedPaise() + e.getValue());
            total += e.getValue();
        }
        requests.save(r);
        return total;
    }

    private static RuntimeException duplicate(FeeConcessionType type) {
        return FeeContext.error("FEE_CONCESSION_DUPLICATE", "This student already has a pending or approved " + type.getName() + " concession this year");
    }

    private FeeConcessionRequest pending(UUID id) {
        FeeConcessionRequest r = requests.findById(id).orElseThrow(() -> new ResourceNotFoundException("FeeConcessionRequest", id));
        if (r.getStatus() != ConcessionRequestStatus.PENDING) {
            throw FeeContext.error("FEE_CONCESSION_STATE", "This request is already " + r.getStatus());
        }
        return r;
    }

    private void review(FeeConcessionRequest r, String remarks) {
        r.setReviewedBy(ctx.actorId());
        r.setReviewedByName(ctx.actorName());
        r.setReviewedAt(ctx.clock().instant());
        r.setReviewRemarks(remarks == null || remarks.isBlank() ? null : remarks.trim());
        requests.save(r);
    }

    private ConcessionView view(UUID id) {
        requests.flush();
        List<ConcessionView> v = queries.concessions(new ConcessionFilter(null, null, null, null, null, null, id), 0, 1);
        if (v.isEmpty()) {
            throw new ResourceNotFoundException("FeeConcessionRequest", id);
        }
        return v.get(0);
    }
}
