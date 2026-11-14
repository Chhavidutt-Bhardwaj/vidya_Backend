package com.vidya.tenantdata.assessment.moderation;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.common.util.CodeGenerator;
import com.vidya.tenantdata.assessment.AssessmentAccess;
import com.vidya.tenantdata.assessment.AssessmentQueries;
import com.vidya.tenantdata.assessment.AssessmentQueries.ConfigInfo;
import com.vidya.tenantdata.assessment.AssessmentQueries.EffectiveMark;
import com.vidya.tenantdata.assessment.AssessmentQueries.Score;
import com.vidya.tenantdata.assessment.entity.Assessment;
import com.vidya.tenantdata.assessment.entity.AssessmentScheme;
import com.vidya.tenantdata.assessment.entity.MarkAdjustment;
import com.vidya.tenantdata.assessment.entity.ModerationBatch;
import com.vidya.tenantdata.assessment.entity.ReevaluationRequest;
import com.vidya.tenantdata.assessment.enums.AdjustmentStatus;
import com.vidya.tenantdata.assessment.enums.AdjustmentType;
import com.vidya.tenantdata.assessment.enums.ModerationStatus;
import com.vidya.tenantdata.assessment.enums.ReevaluationStatus;
import com.vidya.tenantdata.assessment.formative.AssessmentService;
import com.vidya.tenantdata.assessment.moderation.ModerationDtos.AdjustmentResponse;
import com.vidya.tenantdata.assessment.moderation.ModerationDtos.ModerationBatchResponse;
import com.vidya.tenantdata.assessment.moderation.ModerationDtos.ModerationLine;
import com.vidya.tenantdata.assessment.moderation.ModerationDtos.ModerationPreview;
import com.vidya.tenantdata.assessment.moderation.ModerationDtos.ModerationRequest;
import com.vidya.tenantdata.assessment.moderation.ModerationDtos.ProposeAdjustmentRequest;
import com.vidya.tenantdata.assessment.moderation.ModerationDtos.ReevaluationCreateRequest;
import com.vidya.tenantdata.assessment.moderation.ModerationDtos.ReevaluationDecision;
import com.vidya.tenantdata.assessment.moderation.ModerationDtos.ReevaluationResponse;
import com.vidya.tenantdata.assessment.repository.AssessmentSchemeRepository;
import com.vidya.tenantdata.assessment.repository.MarkAdjustmentRepository;
import com.vidya.tenantdata.assessment.repository.ModerationBatchRepository;
import com.vidya.tenantdata.assessment.repository.ReevaluationRequestRepository;
import com.vidya.tenantdata.assessment.result.ResultCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.vidya.tenantdata.assessment.AssessmentQueries.params;
import static com.vidya.tenantdata.assessment.AssessmentQueries.uuid;

/**
 * Changes to marks after entry, all kept as {@link MarkAdjustment} rows so the raw mark and every change stay visible:
 *
 * <ul>
 *   <li><b>Grace / correction</b> for one student: proposed by a teacher or the exam cell, approved or rejected by someone
 *       else with MARKS_MODERATE (maker–checker). Grace is limited by the scheme's per-subject grace.</li>
 *   <li><b>Moderation</b> of a whole exam paper (add, scale, raise-to-pass): previewed, then applied as one batch that can be
 *       reverted as a whole.</li>
 *   <li><b>Re-evaluation</b>: a family or teacher asks for a re-check within the scheme's window after results are published;
 *       a reviewer decides REVISED (new marks), UNCHANGED or REJECTED.</li>
 * </ul>
 *
 * Only APPROVED adjustments change results; regenerate report cards after approving.
 */
@Service
@RequiredArgsConstructor
@Transactional(transactionManager = "tenantTransactionManager")
public class ModerationService {

    static final int DEFAULT_WINDOW_DAYS = 7;

    private final MarkAdjustmentRepository adjustmentRepository;
    private final ModerationBatchRepository batchRepository;
    private final ReevaluationRequestRepository reevaluationRepository;
    private final AssessmentSchemeRepository schemeRepository;
    private final AssessmentService assessmentService;
    private final AssessmentQueries queries;
    private final AssessmentAccess access;
    private final Clock clock;

    /** Current, raw and maximum marks of a target, and which class / term it belongs to. */
    record Target(UUID configId, UUID assessmentId, UUID subjectId, UUID classId, UUID yearId, UUID termId, BigDecimal max, BigDecimal pass,
                  BigDecimal raw, BigDecimal current, boolean absent) {
    }

    // ───────────────────────────── single adjustments ─────────────────────────────

    public AdjustmentResponse propose(ProposeAdjustmentRequest r) {
        if ((r.examSubjectConfigId() == null) == (r.assessmentId() == null)) {
            throw new BusinessException("INVALID_TARGET", "Name either an exam paper (examSubjectConfigId) or an assessment");
        }
        if (r.type() == AdjustmentType.MODERATION || r.type() == AdjustmentType.REEVALUATION) {
            throw new BusinessException("INVALID_TYPE", "Use the moderation and re-evaluation endpoints for those");
        }
        if (r.delta().signum() == 0) {
            throw new BusinessException("INVALID_DELTA", "The change must not be zero");
        }
        Target t = target(r.examSubjectConfigId(), r.assessmentId(), r.studentId(), true);
        access.requireStudent(r.studentId(), "adjust marks");
        if (t.absent() || t.raw() == null) {
            throw new BusinessException("NO_MARKS", "The student has no marks to adjust (absent, exempt or not entered)");
        }
        BigDecimal after = t.current().add(r.delta());
        if (after.signum() < 0 || after.compareTo(t.max()) > 0) {
            throw new BusinessException("OUT_OF_RANGE", "Marks would become " + plain(after) + " (allowed 0–" + plain(t.max()) + ")");
        }
        if (r.type() == AdjustmentType.GRACE) {
            if (r.delta().signum() < 0) {
                throw new BusinessException("INVALID_DELTA", "Grace adds marks");
            }
            AssessmentScheme scheme = schemeRepository.findActiveFor(t.yearId(), t.classId()).orElse(null);
            if (scheme != null) {
                BigDecimal limit = scheme.getGraceMaxPerSubject();
                if (limit.signum() <= 0) {
                    throw new BusinessException("GRACE_NOT_ALLOWED", "The scheme " + scheme.getName() + " gives no grace marks");
                }
                BigDecimal given = adjustments(t).stream().filter(a -> a.getAdjustmentType() == AdjustmentType.GRACE
                                && (a.getStatus() == AdjustmentStatus.APPROVED || a.getStatus() == AdjustmentStatus.PROPOSED))
                        .map(MarkAdjustment::getDelta).reduce(BigDecimal.ZERO, BigDecimal::add);
                if (given.add(r.delta()).compareTo(limit) > 0) {
                    throw new BusinessException("GRACE_LIMIT", "Grace in a subject is limited to " + plain(limit) + " (already "
                            + plain(given) + ")");
                }
            }
        }
        MarkAdjustment a = new MarkAdjustment();
        a.setExamSubjectConfigId(r.examSubjectConfigId());
        a.setAssessmentId(r.assessmentId());
        a.setStudentId(r.studentId());
        a.setAdjustmentType(r.type());
        a.setDelta(r.delta());
        a.setReason(r.reason().trim());
        a.setProposedBy(access.actorId());
        return toResponses(List.of(adjustmentRepository.save(a))).get(0);
    }

    public AdjustmentResponse approve(UUID id) {
        MarkAdjustment a = proposed(id);
        if (Objects.equals(a.getProposedBy(), access.actorId())) {
            throw new AccessDeniedException("Someone other than the proposer must approve a mark change");
        }
        Target t = target(a.getExamSubjectConfigId(), a.getAssessmentId(), a.getStudentId(), false);
        BigDecimal after = t.current().add(a.getDelta());
        if (after.signum() < 0 || after.compareTo(t.max()) > 0) {
            throw new BusinessException("OUT_OF_RANGE", "Marks would become " + plain(after) + " (allowed 0–" + plain(t.max()) + ")");
        }
        decide(a, AdjustmentStatus.APPROVED);
        return toResponses(List.of(a)).get(0);
    }

    public AdjustmentResponse reject(UUID id) {
        MarkAdjustment a = proposed(id);
        decide(a, AdjustmentStatus.REJECTED);
        return toResponses(List.of(a)).get(0);
    }

    public AdjustmentResponse revert(UUID id) {
        MarkAdjustment a = adjustmentRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("MarkAdjustment", id));
        if (a.getStatus() != AdjustmentStatus.APPROVED) {
            throw new BusinessException("NOT_APPROVED", "Only an approved change can be reverted");
        }
        if (a.getModerationBatchId() != null) {
            throw new BusinessException("PART_OF_BATCH", "Revert the moderation batch instead");
        }
        decide(a, AdjustmentStatus.REVERTED);
        return toResponses(List.of(a)).get(0);
    }

    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public List<AdjustmentResponse> list(UUID configId, UUID assessmentId, AdjustmentStatus status) {
        List<MarkAdjustment> list;
        if (configId != null) {
            access.requireExamPaper(configId, "see mark changes");
            list = adjustmentRepository.findByExamSubjectConfigIdOrderByCreatedAtDesc(configId);
        } else if (assessmentId != null) {
            Assessment a = assessmentService.find(assessmentId);
            access.requireTeaches(a.getSubjectId(), a.getAcademicClassId(), "see mark changes");
            list = adjustmentRepository.findByAssessmentIdOrderByCreatedAtDesc(assessmentId);
        } else {
            if (!access.all()) {
                throw new AccessDeniedException("Filter by exam paper or assessment");
            }
            list = adjustmentRepository.findByStatus(status != null ? status : AdjustmentStatus.PROPOSED);
        }
        return toResponses(list.stream().filter(a -> status == null || a.getStatus() == status).toList());
    }

    // ───────────────────────────── moderation ─────────────────────────────

    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public ModerationPreview preview(ModerationRequest r) {
        ConfigInfo c = config(r.examSubjectConfigId());
        List<EffectiveMark> marks = queries.effectiveMarks(List.of(c.configId())).stream()
                .filter(m -> !m.absent() && !m.exempt() && m.raw() != null).toList();
        Map<UUID, String> names = queries.studentNames(marks.stream().map(EffectiveMark::studentId).toList());
        List<ModerationLine> lines = new ArrayList<>();
        BigDecimal sumBefore = BigDecimal.ZERO;
        BigDecimal sumAfter = BigDecimal.ZERO;
        int passBefore = 0;
        int passAfter = 0;
        for (EffectiveMark m : marks) {
            BigDecimal delta = r.onlyBelow() != null && m.marks().compareTo(r.onlyBelow()) >= 0 ? BigDecimal.ZERO
                    : ResultCalculator.moderationDelta(r.method(), r.value(), m.raw(), m.marks(), c.maxMarks(), c.passMarks());
            BigDecimal after = m.marks().add(delta);
            boolean pb = c.passMarks() == null || m.marks().compareTo(c.passMarks()) >= 0;
            boolean pa = c.passMarks() == null || after.compareTo(c.passMarks()) >= 0;
            lines.add(new ModerationLine(m.studentId(), names.get(m.studentId()), m.marks(), delta, after, pb, pa));
            sumBefore = sumBefore.add(m.marks());
            sumAfter = sumAfter.add(after);
            passBefore += pb ? 1 : 0;
            passAfter += pa ? 1 : 0;
        }
        int n = marks.size();
        return new ModerationPreview(c.configId(), c.maxMarks(), c.passMarks(), n, (int) lines.stream().filter(l -> l.delta().signum() != 0).count(),
                n == 0 ? null : sumBefore.divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_UP),
                n == 0 ? null : sumAfter.divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_UP), passBefore, passAfter, lines);
    }

    public ModerationBatchResponse apply(ModerationRequest r) {
        if (r.reason() == null || r.reason().isBlank()) {
            throw new BusinessException("REASON_REQUIRED", "Give the reason for moderation");
        }
        ModerationPreview p = preview(r);
        if (p.affected() == 0) {
            throw new BusinessException("NOTHING_TO_MODERATE", "The moderation changes no student's marks");
        }
        Instant now = clock.instant();
        ModerationBatch b = new ModerationBatch();
        b.setBatchCode(CodeGenerator.moderationBatchCode());
        b.setExamSubjectConfigId(r.examSubjectConfigId());
        b.setMethod(r.method());
        b.setValue(r.value());
        b.setOnlyBelow(r.onlyBelow());
        b.setReason(r.reason().trim());
        b.setStudentsAffected(p.affected());
        b.setAppliedBy(access.actorId());
        b.setAppliedAt(now);
        b = batchRepository.save(b);
        for (ModerationLine l : p.lines()) {
            if (l.delta().signum() == 0) {
                continue;
            }
            MarkAdjustment a = new MarkAdjustment();
            a.setExamSubjectConfigId(r.examSubjectConfigId());
            a.setStudentId(l.studentId());
            a.setAdjustmentType(AdjustmentType.MODERATION);
            a.setDelta(l.delta());
            a.setReason(b.getReason());
            a.setModerationBatchId(b.getId());
            a.setStatus(AdjustmentStatus.APPROVED);
            a.setProposedBy(access.actorId());
            a.setDecidedBy(access.actorId());
            a.setDecidedAt(now);
            adjustmentRepository.save(a);
        }
        return toResponse(b);
    }

    public ModerationBatchResponse revertBatch(UUID batchId) {
        ModerationBatch b = batchRepository.findById(batchId).orElseThrow(() -> new ResourceNotFoundException("ModerationBatch", batchId));
        config(b.getExamSubjectConfigId());
        if (b.getStatus() == ModerationStatus.REVERTED) {
            throw new BusinessException("ALREADY_REVERTED", "The moderation was already reverted");
        }
        Instant now = clock.instant();
        for (MarkAdjustment a : adjustmentRepository.findByModerationBatchId(batchId)) {
            a.setStatus(AdjustmentStatus.REVERTED);
            a.setDecidedBy(access.actorId());
            a.setDecidedAt(now);
            adjustmentRepository.save(a);
        }
        b.setStatus(ModerationStatus.REVERTED);
        b.setRevertedBy(access.actorId());
        b.setRevertedAt(now);
        return toResponse(batchRepository.save(b));
    }

    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public List<ModerationBatchResponse> batches(UUID configId) {
        access.requireExamPaper(configId, "see moderation");
        return batchRepository.findByExamSubjectConfigIdOrderByCreatedAtDesc(configId).stream().map(this::toResponse).toList();
    }

    // ───────────────────────────── re-evaluation ─────────────────────────────

    public ReevaluationResponse request(ReevaluationCreateRequest r) {
        access.requireStudent(r.studentId(), "ask for re-evaluation");
        ConfigInfo c = queries.config(r.examSubjectConfigId()).orElseThrow(() -> new ResourceNotFoundException("ExamSubjectConfig", r.examSubjectConfigId()));
        EffectiveMark m = queries.effectiveMark(c.configId(), r.studentId())
                .filter(x -> !x.absent() && !x.exempt() && x.raw() != null)
                .orElseThrow(() -> new BusinessException("NO_MARKS", "There are no marks to re-evaluate for this student and paper"));
        Optional<Instant> published = queries.rows("SELECT published_at FROM student_report_card WHERE exam_term_id = :t AND student_id = :s "
                        + "AND deleted_at IS NULL AND status = 'PUBLISHED'", params("t", c.termId(), "s", r.studentId())).stream()
                .map(x -> instant(x[0])).filter(Objects::nonNull).findFirst();
        if (published.isPresent()) {
            int days = schemeRepository.findActiveFor(c.yearId(), c.classId()).map(AssessmentScheme::getReevaluationWindowDays).orElse(DEFAULT_WINDOW_DAYS);
            Instant closes = published.get().plus(Duration.ofDays(days));
            if (clock.instant().isAfter(closes)) {
                throw new BusinessException("WINDOW_CLOSED", "Re-evaluation requests closed " + days + " day(s) after results were published");
            }
        }
        boolean open = reevaluationRepository.search(null, c.configId(), r.studentId()).stream()
                .anyMatch(x -> x.getStatus() == ReevaluationStatus.REQUESTED || x.getStatus() == ReevaluationStatus.UNDER_REVIEW);
        if (open) {
            throw new BusinessException("ALREADY_REQUESTED", "A re-evaluation of this paper is already in progress");
        }
        ReevaluationRequest q = new ReevaluationRequest();
        q.setRequestCode(CodeGenerator.reevaluationRequestCode());
        q.setExamSubjectConfigId(c.configId());
        q.setStudentId(r.studentId());
        q.setRequestedBy(access.actorId());
        q.setReason(r.reason().trim());
        q.setOriginalMarks(m.marks());
        return toRequestResponses(List.of(reevaluationRepository.save(q))).get(0);
    }

    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public List<ReevaluationResponse> requests(ReevaluationStatus status, UUID configId, UUID studentId) {
        if (studentId != null) {
            access.requireStudent(studentId, "see re-evaluation requests");
        } else if (configId != null) {
            access.requireExamPaper(configId, "see re-evaluation requests");
        } else if (!access.all()) {
            throw new AccessDeniedException("Filter by exam paper or student");
        }
        return toRequestResponses(reevaluationRepository.search(status, configId, studentId));
    }

    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public List<ReevaluationResponse> mine() {
        Set<UUID> students = access.familyStudentIds();
        return students.isEmpty() ? List.of() : toRequestResponses(reevaluationRepository.findByStudents(students));
    }

    public ReevaluationResponse assign(UUID id, UUID reviewerStaffId) {
        ReevaluationRequest q = reevaluation(id);
        access.requireExamPaper(q.getExamSubjectConfigId(), "review re-evaluation requests");
        if (q.getStatus() != ReevaluationStatus.REQUESTED && q.getStatus() != ReevaluationStatus.UNDER_REVIEW) {
            throw new BusinessException("REQUEST_CLOSED", "The request is already " + q.getStatus());
        }
        UUID reviewer = reviewerStaffId != null ? reviewerStaffId : access.staffId();
        if (reviewer == null) {
            throw new BusinessException("REVIEWER_REQUIRED", "Give reviewerStaffId (you have no staff record)");
        }
        if (queries.rows("SELECT id FROM staff WHERE id = :s AND deleted_at IS NULL", Map.of("s", reviewer)).isEmpty()) {
            throw new ResourceNotFoundException("Staff", reviewer);
        }
        q.setReviewerStaffId(reviewer);
        q.setStatus(ReevaluationStatus.UNDER_REVIEW);
        return toRequestResponses(List.of(reevaluationRepository.save(q))).get(0);
    }

    public ReevaluationResponse decide(UUID id, ReevaluationDecision d) {
        ReevaluationRequest q = reevaluation(id);
        if (q.getStatus() != ReevaluationStatus.REQUESTED && q.getStatus() != ReevaluationStatus.UNDER_REVIEW) {
            throw new BusinessException("REQUEST_CLOSED", "The request is already " + q.getStatus());
        }
        boolean reviewer = q.getReviewerStaffId() != null && access.own().staffIds().contains(q.getReviewerStaffId());
        if (!reviewer && !access.all()) {
            throw new AccessDeniedException("Only the assigned reviewer or the exam cell can decide");
        }
        Instant now = clock.instant();
        switch (d.outcome()) {
            case REVISED -> {
                if (d.revisedMarks() == null) {
                    throw new BusinessException("MARKS_REQUIRED", "Give the revised marks");
                }
                Target t = target(q.getExamSubjectConfigId(), null, q.getStudentId(), false);
                if (d.revisedMarks().compareTo(t.max()) > 0) {
                    throw new BusinessException("OUT_OF_RANGE", "Revised marks above the maximum of " + plain(t.max()));
                }
                BigDecimal delta = d.revisedMarks().subtract(t.current());
                if (delta.signum() == 0) {
                    throw new BusinessException("UNCHANGED", "The revised marks equal the current marks; decide UNCHANGED");
                }
                MarkAdjustment a = new MarkAdjustment();
                a.setExamSubjectConfigId(q.getExamSubjectConfigId());
                a.setStudentId(q.getStudentId());
                a.setAdjustmentType(AdjustmentType.REEVALUATION);
                a.setDelta(delta);
                a.setReason("Re-evaluation " + q.getRequestCode() + (d.note() != null ? ": " + d.note() : ""));
                a.setReevaluationRequestId(q.getId());
                a.setStatus(AdjustmentStatus.APPROVED);
                a.setProposedBy(access.actorId());
                a.setDecidedBy(access.actorId());
                a.setDecidedAt(now);
                adjustmentRepository.save(a);
                q.setRevisedMarks(d.revisedMarks());
            }
            case UNCHANGED, REJECTED -> {
            }
            default -> throw new BusinessException("INVALID_OUTCOME", "Decide REVISED, UNCHANGED or REJECTED");
        }
        if (q.getReviewerStaffId() == null) {
            q.setReviewerStaffId(access.staffId());
        }
        q.setStatus(d.outcome());
        q.setReviewerNote(d.note());
        q.setDecidedAt(now);
        return toRequestResponses(List.of(reevaluationRepository.save(q))).get(0);
    }

    public ReevaluationResponse withdraw(UUID id) {
        ReevaluationRequest q = reevaluation(id);
        if (!Objects.equals(q.getRequestedBy(), access.actorId())) {
            throw new AccessDeniedException("Only the person who asked can withdraw the request");
        }
        if (q.getStatus() != ReevaluationStatus.REQUESTED) {
            throw new BusinessException("REQUEST_IN_REVIEW", "The request is already " + q.getStatus());
        }
        q.setStatus(ReevaluationStatus.WITHDRAWN);
        q.setDecidedAt(clock.instant());
        return toRequestResponses(List.of(reevaluationRepository.save(q))).get(0);
    }

    // ───────────────────────────── helpers ─────────────────────────────

    private Target target(UUID configId, UUID assessmentId, UUID studentId, boolean checkAccess) {
        if (configId != null) {
            ConfigInfo c = queries.config(configId).orElseThrow(() -> new ResourceNotFoundException("ExamSubjectConfig", configId));
            if (checkAccess) {
                access.requireExamPaper(configId, "adjust marks");
            }
            EffectiveMark m = queries.effectiveMark(configId, studentId).orElse(null);
            return new Target(configId, null, c.subjectId(), c.classId(), c.yearId(), c.termId(), c.maxMarks(), c.passMarks(),
                    m == null ? null : m.raw(), m == null || m.marks() == null ? BigDecimal.ZERO : m.marks(), m == null || m.absent() || m.exempt());
        }
        Assessment a = assessmentService.find(assessmentId);
        if (checkAccess) {
            access.requireTeaches(a.getSubjectId(), a.getAcademicClassId(), "adjust marks");
        }
        Score s = queries.scores(List.of(assessmentId)).stream().filter(x -> x.studentId().equals(studentId)).findFirst().orElse(null);
        return new Target(null, assessmentId, a.getSubjectId(), a.getAcademicClassId(), a.getAcademicYearId(), a.getExamTermId(), a.getMaxMarks(),
                null, s == null ? null : s.raw(), s == null || s.marks() == null ? BigDecimal.ZERO : s.marks(), s == null || s.absent() || s.exempt());
    }

    private List<MarkAdjustment> adjustments(Target t) {
        return (t.configId() != null ? adjustmentRepository.findByExamSubjectConfigIdOrderByCreatedAtDesc(t.configId())
                : adjustmentRepository.findByAssessmentIdOrderByCreatedAtDesc(t.assessmentId()));
    }

    private ConfigInfo config(UUID configId) {
        ConfigInfo c = queries.config(configId).orElseThrow(() -> new ResourceNotFoundException("ExamSubjectConfig", configId));
        access.requireExamPaper(configId, "moderate marks");
        return c;
    }

    private MarkAdjustment proposed(UUID id) {
        MarkAdjustment a = adjustmentRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("MarkAdjustment", id));
        if (a.getStatus() != AdjustmentStatus.PROPOSED) {
            throw new BusinessException("ALREADY_DECIDED", "The change is already " + a.getStatus());
        }
        if (a.getExamSubjectConfigId() != null) {
            access.requireExamPaper(a.getExamSubjectConfigId(), "decide mark changes");
        }
        return a;
    }

    private void decide(MarkAdjustment a, AdjustmentStatus status) {
        a.setStatus(status);
        a.setDecidedBy(access.actorId());
        a.setDecidedAt(clock.instant());
        adjustmentRepository.save(a);
    }

    private ReevaluationRequest reevaluation(UUID id) {
        return reevaluationRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("ReevaluationRequest", id));
    }

    private List<AdjustmentResponse> toResponses(List<MarkAdjustment> list) {
        Map<UUID, String> names = queries.studentNames(list.stream().map(MarkAdjustment::getStudentId).distinct().toList());
        return list.stream().map(a -> new AdjustmentResponse(a.getId(), a.getExamSubjectConfigId(), a.getAssessmentId(), a.getStudentId(),
                names.get(a.getStudentId()), a.getAdjustmentType(), a.getDelta(), a.getReason(), a.getStatus(), a.getModerationBatchId(),
                a.getReevaluationRequestId(), a.getProposedBy(), a.getDecidedBy(), a.getDecidedAt(), a.getCreatedAt())).toList();
    }

    private ModerationBatchResponse toResponse(ModerationBatch b) {
        return new ModerationBatchResponse(b.getId(), b.getBatchCode(), b.getExamSubjectConfigId(), b.getMethod(), b.getValue(), b.getOnlyBelow(),
                b.getReason(), b.getStatus(), b.getStudentsAffected(), b.getAppliedBy(), b.getAppliedAt(), b.getRevertedBy(), b.getRevertedAt());
    }

    private List<ReevaluationResponse> toRequestResponses(List<ReevaluationRequest> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        List<UUID> configs = list.stream().map(ReevaluationRequest::getExamSubjectConfigId).distinct().toList();
        Map<UUID, Object[]> info = new HashMap<>();
        queries.rows("SELECT c.id, sub.name, t.name, c.max_marks FROM exam_subject_config c JOIN subject sub ON sub.id = c.subject_id "
                + "JOIN exam_schedule sc ON sc.id = c.exam_schedule_id JOIN exam_term t ON t.id = sc.exam_term_id WHERE c.id IN (:ids)",
                Map.of("ids", configs)).forEach(r -> info.put(uuid(r[0]), r));
        Map<String, BigDecimal> current = new HashMap<>();
        queries.effectiveMarks(configs).forEach(m -> current.put(m.configId() + ":" + m.studentId(), m.marks()));
        Map<UUID, String> names = queries.studentNames(list.stream().map(ReevaluationRequest::getStudentId).distinct().toList());
        return list.stream().map(q -> {
            Object[] i = info.getOrDefault(q.getExamSubjectConfigId(), new Object[4]);
            return new ReevaluationResponse(q.getId(), q.getRequestCode(), q.getExamSubjectConfigId(), (String) i[1], (String) i[2],
                    q.getStudentId(), names.get(q.getStudentId()), q.getReason(), q.getStatus(), q.getOriginalMarks(),
                    current.get(q.getExamSubjectConfigId() + ":" + q.getStudentId()), q.getRevisedMarks(), (BigDecimal) i[3],
                    q.getReviewerStaffId(), q.getReviewerNote(), q.getDecidedAt(), q.getCreatedAt());
        }).toList();
    }

    private static Instant instant(Object o) {
        return o == null ? null : o instanceof Instant i ? i : o instanceof Timestamp t ? t.toInstant()
                : o instanceof java.time.OffsetDateTime odt ? odt.toInstant() : null;
    }

    private static String plain(BigDecimal d) {
        return d.stripTrailingZeros().toPlainString();
    }
}
