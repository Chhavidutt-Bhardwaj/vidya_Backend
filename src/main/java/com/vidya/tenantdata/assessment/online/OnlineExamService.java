package com.vidya.tenantdata.assessment.online;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.common.util.CodeGenerator;
import com.vidya.tenantdata.assessment.AssessmentAccess;
import com.vidya.tenantdata.assessment.AssessmentQueries;
import com.vidya.tenantdata.assessment.AssessmentQueries.Enrolled;
import com.vidya.tenantdata.assessment.bank.PaperScoring;
import com.vidya.tenantdata.assessment.bank.PaperService;
import com.vidya.tenantdata.assessment.bank.PaperService.Leaf;
import com.vidya.tenantdata.assessment.bank.QuestionFormats;
import com.vidya.tenantdata.assessment.bank.ScorePosting;
import com.vidya.tenantdata.assessment.bank.ScorePosting.QuestionMark;
import com.vidya.tenantdata.assessment.entity.OnlineExam;
import com.vidya.tenantdata.assessment.entity.OnlineExamAttempt;
import com.vidya.tenantdata.assessment.entity.OnlineExamResponse;
import com.vidya.tenantdata.assessment.entity.Question;
import com.vidya.tenantdata.assessment.entity.QuestionPaper;
import com.vidya.tenantdata.assessment.enums.AttemptStatus;
import com.vidya.tenantdata.assessment.enums.OnlineExamStatus;
import com.vidya.tenantdata.assessment.enums.PaperStatus;
import com.vidya.tenantdata.assessment.enums.QuestionType;
import com.vidya.tenantdata.assessment.enums.ResultVisibility;
import com.vidya.tenantdata.assessment.enums.ScoreSource;
import com.vidya.tenantdata.assessment.online.OnlineDtos.AnswerEntry;
import com.vidya.tenantdata.assessment.online.OnlineDtos.AttemptItem;
import com.vidya.tenantdata.assessment.online.OnlineDtos.AttemptResult;
import com.vidya.tenantdata.assessment.online.OnlineDtos.AttemptSummary;
import com.vidya.tenantdata.assessment.online.OnlineDtos.AttemptView;
import com.vidya.tenantdata.assessment.online.OnlineDtos.ExamRequest;
import com.vidya.tenantdata.assessment.online.OnlineDtos.ExamView;
import com.vidya.tenantdata.assessment.online.OnlineDtos.GradeEntry;
import com.vidya.tenantdata.assessment.online.OnlineDtos.ItemResult;
import com.vidya.tenantdata.assessment.online.OnlineDtos.ManualItem;
import com.vidya.tenantdata.assessment.online.OnlineDtos.Posted;
import com.vidya.tenantdata.assessment.repository.OnlineExamAttemptRepository;
import com.vidya.tenantdata.assessment.repository.OnlineExamRepository;
import com.vidya.tenantdata.assessment.repository.OnlineExamResponseRepository;
import com.vidya.tenantdata.assessment.repository.QuestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.vidya.tenantdata.assessment.AssessmentQueries.params;
import static com.vidya.tenantdata.assessment.AssessmentQueries.uuid;

/**
 * Online exams on a finalised question paper. A student starts an attempt inside the window and gets their own question
 * and option order; answers autosave until the deadline (start + duration, never after the window closes; 30 seconds of
 * grace for network delay). Submission (or the deadline) marks objective questions at once; written answers wait for a
 * teacher. Results become visible per the exam's setting, and the best attempt of each student can be posted as exam
 * or assessment marks.
 */
@Service
@RequiredArgsConstructor
@Transactional(transactionManager = "tenantTransactionManager")
public class OnlineExamService {

    static final Duration GRACE = Duration.ofSeconds(30);
    private static final Random RANDOM = new SecureRandom();

    private final OnlineExamRepository examRepository;
    private final OnlineExamAttemptRepository attemptRepository;
    private final OnlineExamResponseRepository responseRepository;
    private final QuestionRepository questionRepository;
    private final PaperService papers;
    private final ScorePosting posting;
    private final AssessmentQueries queries;
    private final AssessmentAccess access;
    private final Clock clock;

    // ───────────────────────────── set-up (staff) ─────────────────────────────

    public ExamView create(ExamRequest r) {
        OnlineExam e = new OnlineExam();
        e.setExamCode(CodeGenerator.onlineExamCode());
        e.setCreatedBy(access.actorId());
        apply(e, r);
        return view(examRepository.save(e), null);
    }

    public ExamView update(UUID id, ExamRequest r) {
        OnlineExam e = staffExam(id);
        if (e.getStatus() == OnlineExamStatus.CANCELLED || !attemptRepository.findByExamIdOrderByStudentIdAscAttemptNoAsc(id).isEmpty()) {
            throw new BusinessException("EXAM_STARTED", "The exam is cancelled or already has attempts and cannot change");
        }
        apply(e, r);
        e.setUpdatedBy(access.actorId());
        return view(examRepository.save(e), null);
    }

    private void apply(OnlineExam e, ExamRequest r) {
        QuestionPaper p = papers.findPaper(r.questionPaperId());
        access.requireTeaches(p.getSubjectId(), p.getAcademicClassId(), "set online exams");
        if (p.getStatus() != PaperStatus.FINALISED) {
            throw new BusinessException("PAPER_NOT_FINAL", "Finalise the question paper first");
        }
        if (!r.closesAt().isAfter(r.opensAt())) {
            throw new BusinessException("INVALID_WINDOW", "The exam must close after it opens");
        }
        if (r.sectionId() != null && queries.rows("SELECT id FROM section WHERE id = :s AND academic_class_id = :c",
                params("s", r.sectionId(), "c", p.getAcademicClassId())).isEmpty()) {
            throw new BusinessException("INVALID_SECTION", "The section is not of the paper's class");
        }
        UUID config = r.examSubjectConfigId() != null || r.assessmentId() != null ? r.examSubjectConfigId() : p.getExamSubjectConfigId();
        UUID assessment = r.examSubjectConfigId() != null || r.assessmentId() != null ? r.assessmentId() : p.getAssessmentId();
        if (config != null && assessment != null) {
            throw new BusinessException("INVALID_TARGET", "Post marks to an exam paper or an assessment, not both");
        }
        if (config != null || assessment != null) {
            ScorePosting.Target t = posting.target(config, assessment);
            if (!t.subjectId().equals(p.getSubjectId()) || !t.classId().equals(p.getAcademicClassId())) {
                throw new BusinessException("INVALID_TARGET", "The exam paper / assessment is of another subject or class");
            }
        }
        long window = Duration.between(r.opensAt(), r.closesAt()).toMinutes();
        int duration = r.durationMinutes() != null ? r.durationMinutes() : p.getDurationMinutes() != null ? p.getDurationMinutes() : (int) Math.min(window, 600);
        if (duration <= 0) {
            throw new BusinessException("INVALID_WINDOW", "Give the duration");
        }
        e.setTitle(r.title().trim());
        e.setQuestionPaperId(p.getId());
        e.setAcademicClassId(p.getAcademicClassId());
        e.setSectionId(r.sectionId());
        e.setExamSubjectConfigId(config);
        e.setAssessmentId(assessment);
        e.setOpensAt(r.opensAt());
        e.setClosesAt(r.closesAt());
        e.setDurationMinutes(duration);
        e.setShuffleQuestions(r.shuffleQuestions() == null || r.shuffleQuestions());
        e.setShuffleOptions(r.shuffleOptions() == null || r.shuffleOptions());
        e.setNegativeMarking(r.negativeMarking());
        e.setMaxAttempts(r.maxAttempts() != null ? r.maxAttempts() : 1);
        e.setResultVisibility(r.resultVisibility() != null ? r.resultVisibility() : ResultVisibility.AFTER_PUBLISH);
        e.setInstructions(r.instructions());
    }

    public ExamView schedule(UUID id) {
        OnlineExam e = staffExam(id);
        if (e.getStatus() != OnlineExamStatus.DRAFT) {
            throw new BusinessException("NOT_DRAFT", "The exam is " + e.getStatus());
        }
        if (!e.getClosesAt().isAfter(clock.instant())) {
            throw new BusinessException("INVALID_WINDOW", "The exam window is over; change it first");
        }
        e.setStatus(OnlineExamStatus.SCHEDULED);
        return view(examRepository.save(e), null);
    }

    public ExamView cancel(UUID id) {
        OnlineExam e = staffExam(id);
        if (e.getMarksPostedAt() != null) {
            throw new BusinessException("MARKS_POSTED", "Marks were posted from this exam");
        }
        e.setStatus(OnlineExamStatus.CANCELLED);
        return view(examRepository.save(e), null);
    }

    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public List<ExamView> list(UUID classId, UUID sectionId) {
        UUID self = access.all() || access.own().staffIds().size() > 0 ? null : access.selfStudentId();
        if (self != null) {
            Enrolled en = queries.currentEnrolment(self).orElse(null);
            if (en == null) {
                return List.of();
            }
            return examRepository.search(en.classId(), en.sectionId()).stream().filter(e -> e.getStatus() == OnlineExamStatus.SCHEDULED)
                    .map(e -> view(e, self)).toList();
        }
        if (classId != null) {
            access.requireClass(classId, sectionId, "see online exams");
        }
        return examRepository.search(classId, sectionId).stream()
                .filter(e -> access.all() || access.teaches(papers.findPaper(e.getQuestionPaperId()).getSubjectId(), e.getAcademicClassId()))
                .map(e -> view(e, null)).toList();
    }

    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public ExamView get(UUID id) {
        OnlineExam e = find(id);
        UUID self = access.selfStudentId();
        if (self != null && !access.all()) {
            requireEligible(e, self);
            return view(e, self);
        }
        staffCheck(e);
        return view(e, null);
    }

    // ───────────────────────────── taking the exam (student) ─────────────────────────────

    /** Starts a new attempt, or resumes the one in progress. */
    public AttemptView start(UUID examId, String clientInfo) {
        UUID student = self();
        OnlineExam e = find(examId);
        requireEligible(e, student);
        Instant now = clock.instant();
        if (e.getStatus() != OnlineExamStatus.SCHEDULED || now.isBefore(e.getOpensAt()) || !now.isBefore(e.getClosesAt())) {
            throw new BusinessException("EXAM_NOT_OPEN", "The exam is not open now");
        }
        List<OnlineExamAttempt> mine = attemptRepository.findByExamIdAndStudentIdOrderByAttemptNo(examId, student);
        for (OnlineExamAttempt a : mine) {
            if (a.getStatus() == AttemptStatus.IN_PROGRESS) {
                if (expired(a, now)) {
                    finish(attemptRepository.lockById(a.getId()).orElseThrow(), e, AttemptStatus.AUTO_SUBMITTED);
                } else {
                    return attemptView(a, e);
                }
            }
        }
        if (mine.size() >= e.getMaxAttempts()) {
            throw new BusinessException("NO_ATTEMPTS_LEFT", "You have used all " + e.getMaxAttempts() + " attempt(s)");
        }
        List<Leaf> leaves = papers.leaves(e.getQuestionPaperId());
        OnlineExamAttempt a = new OnlineExamAttempt();
        a.setExamId(examId);
        a.setStudentId(student);
        a.setAttemptNo(mine.size() + 1);
        a.setStartedAt(now);
        Instant deadline = now.plus(Duration.ofMinutes(e.getDurationMinutes()));
        a.setDeadlineAt(deadline.isAfter(e.getClosesAt()) ? e.getClosesAt() : deadline);
        a.setItemOrder(order(leaves, e.isShuffleQuestions(), RANDOM).stream().map(UUID::toString).toList());
        a.setOptionOrder(e.isShuffleOptions() ? optionOrder(leaves, RANDOM) : null);
        a.setMaxScore(papers.findPaper(e.getQuestionPaperId()).getTotalMarks());
        a.setClientInfo(clientInfo == null ? null : clientInfo.substring(0, Math.min(clientInfo.length(), 300)));
        return attemptView(attemptRepository.save(a), e);
    }

    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public AttemptView attempt(UUID attemptId) {
        OnlineExamAttempt a = attemptRepository.findById(attemptId).orElseThrow(() -> new ResourceNotFoundException("OnlineExamAttempt", attemptId));
        OnlineExam e = find(a.getExamId());
        if (!a.getStudentId().equals(access.selfStudentId())) {
            if (!access.canSeeStudent(a.getStudentId())) {
                throw new AccessDeniedException("Not your attempt");
            }
            if (access.familyStudentIds().contains(a.getStudentId()) && a.getStatus() == AttemptStatus.IN_PROGRESS) {
                throw new AccessDeniedException("The attempt is in progress");
            }
        }
        return attemptView(a, e);
    }

    /** Autosave: answers replace earlier answers of the same questions. */
    public AttemptView save(UUID attemptId, List<AnswerEntry> answers) {
        OnlineExamAttempt a = ownAttempt(attemptId);
        OnlineExam e = find(a.getExamId());
        Instant now = clock.instant();
        if (a.getStatus() != AttemptStatus.IN_PROGRESS) {
            throw new BusinessException("ATTEMPT_CLOSED", "The attempt is already submitted");
        }
        if (expired(a, now)) {
            finish(a, e, AttemptStatus.AUTO_SUBMITTED);
            throw new BusinessException("TIME_UP", "Time is up; the attempt was submitted with the answers saved before the deadline");
        }
        Map<UUID, Leaf> leaves = papers.leaves(e.getQuestionPaperId()).stream().collect(Collectors.toMap(l -> l.item().getId(), Function.identity()));
        for (AnswerEntry ans : answers) {
            Leaf l = leaves.get(ans.itemId());
            if (l == null) {
                throw new BusinessException("INVALID_ITEM", "Item " + ans.itemId() + " is not a question of this exam");
            }
            OnlineExamResponse resp = responseRepository.findByAttemptIdAndPaperItemId(a.getId(), ans.itemId()).orElseGet(() -> {
                OnlineExamResponse n = new OnlineExamResponse();
                n.setAttemptId(a.getId());
                n.setPaperItemId(ans.itemId());
                n.setQuestionId(l.question().getId());
                return n;
            });
            resp.setAnswer(ans.answer() == null ? Map.of() : ans.answer());
            resp.setAnsweredAt(now);
            responseRepository.save(resp);
        }
        return attemptView(a, e);
    }

    public AttemptView submit(UUID attemptId) {
        OnlineExamAttempt a = ownAttempt(attemptId);
        OnlineExam e = find(a.getExamId());
        if (a.getStatus() != AttemptStatus.IN_PROGRESS) {
            throw new BusinessException("ATTEMPT_CLOSED", "The attempt is already submitted");
        }
        finish(a, e, expired(a, clock.instant()) ? AttemptStatus.AUTO_SUBMITTED : AttemptStatus.SUBMITTED);
        return attemptView(a, e);
    }

    /** Attempts past their deadline (plus grace); the scheduler submits them one by one. */
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public List<UUID> expiredAttempts(int limit) {
        return attemptRepository.expiredIds(clock.instant().minus(GRACE), PageRequest.of(0, limit));
    }

    public boolean autoSubmit(UUID attemptId) {
        OnlineExamAttempt a = attemptRepository.lockById(attemptId).orElse(null);
        if (a == null || a.getStatus() != AttemptStatus.IN_PROGRESS || !expired(a, clock.instant())) {
            return false;
        }
        finish(a, find(a.getExamId()), AttemptStatus.AUTO_SUBMITTED);
        return true;
    }

    // ───────────────────────────── marking (staff) ─────────────────────────────

    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public List<AttemptSummary> attempts(UUID examId) {
        OnlineExam e = staffExam(examId);
        List<OnlineExamAttempt> list = attemptRepository.findByExamIdOrderByStudentIdAscAttemptNoAsc(e.getId());
        Map<UUID, String> names = queries.studentNames(list.stream().map(OnlineExamAttempt::getStudentId).distinct().toList());
        return list.stream().map(a -> new AttemptSummary(a.getId(), a.getStudentId(), names.get(a.getStudentId()), a.getAttemptNo(), a.getStatus(),
                        a.getStartedAt(), a.getSubmittedAt(), a.getAutoScore(), a.getManualScore(), a.getTotalScore(), a.getMaxScore(), a.getPendingManual()))
                .sorted(Comparator.comparing(AttemptSummary::studentName, Comparator.nullsLast(String::compareTo))
                        .thenComparing(AttemptSummary::attemptNo)).toList();
    }

    /** Written answers to mark (or all written answers with {@code pendingOnly=false}). */
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public List<ManualItem> manualQueue(UUID examId, boolean pendingOnly) {
        OnlineExam e = staffExam(examId);
        List<OnlineExamAttempt> list = attemptRepository.findByExamIdOrderByStudentIdAscAttemptNoAsc(e.getId()).stream()
                .filter(a -> a.getStatus() != AttemptStatus.IN_PROGRESS).toList();
        if (list.isEmpty()) {
            return List.of();
        }
        Map<UUID, OnlineExamAttempt> attempts = list.stream().collect(Collectors.toMap(OnlineExamAttempt::getId, Function.identity()));
        Map<UUID, Leaf> leaves = papers.leaves(e.getQuestionPaperId()).stream().collect(Collectors.toMap(l -> l.item().getId(), Function.identity()));
        Map<UUID, String> names = queries.studentNames(list.stream().map(OnlineExamAttempt::getStudentId).distinct().toList());
        return responseRepository.findByAttemptIdIn(attempts.keySet()).stream()
                .filter(r -> r.isNeedsManual() && (!pendingOnly || r.getMarksAwarded() == null))
                .map(r -> {
                    Leaf l = leaves.get(r.getPaperItemId());
                    OnlineExamAttempt a = attempts.get(r.getAttemptId());
                    return new ManualItem(r.getId(), a.getId(), a.getStudentId(), names.get(a.getStudentId()), r.getPaperItemId(), l.item().getPosition(),
                            l.question().getStem(), l.question().getAnswer(), l.item().getMarks(), r.getAnswer(), r.getMarksAwarded(), r.getFeedback());
                })
                .sorted(Comparator.comparing(ManualItem::position).thenComparing(ManualItem::studentName, Comparator.nullsLast(String::compareTo)))
                .toList();
    }

    public List<AttemptSummary> grade(UUID examId, List<GradeEntry> grades) {
        // Re-marking after posting is allowed; post marks again for it to reach the report card.
        OnlineExam e = staffExam(examId);
        Map<UUID, Leaf> leaves = papers.leaves(e.getQuestionPaperId()).stream().collect(Collectors.toMap(l -> l.item().getId(), Function.identity()));
        Map<UUID, OnlineExamAttempt> touched = new LinkedHashMap<>();
        Instant now = clock.instant();
        for (GradeEntry g : grades) {
            OnlineExamResponse r = responseRepository.findById(g.responseId()).orElseThrow(() -> new ResourceNotFoundException("OnlineExamResponse", g.responseId()));
            OnlineExamAttempt a = touched.computeIfAbsent(r.getAttemptId(), id -> attemptRepository.lockById(id).orElseThrow());
            if (!a.getExamId().equals(examId) || !r.isNeedsManual()) {
                throw new BusinessException("INVALID_RESPONSE", "Response " + g.responseId() + " is not a written answer of this exam");
            }
            BigDecimal max = leaves.get(r.getPaperItemId()).item().getMarks();
            if (g.marks().compareTo(max) > 0) {
                throw new BusinessException("MARKS_ABOVE_MAX", "Marks above the question's " + max.stripTrailingZeros().toPlainString());
            }
            r.setMarksAwarded(g.marks());
            r.setFeedback(g.feedback());
            r.setGradedByStaffId(access.staffId());
            r.setGradedAt(now);
            responseRepository.save(r);
        }
        List<Leaf> all = new ArrayList<>(leaves.values());
        touched.values().forEach(a -> recompute(a, all));
        return attempts(examId);
    }

    public ExamView publishResults(UUID examId) {
        OnlineExam e = staffExam(examId);
        submitExpired(e);
        long pending = attemptRepository.findByExamIdOrderByStudentIdAscAttemptNoAsc(examId).stream().filter(a -> a.getPendingManual() > 0).count();
        if (pending > 0) {
            throw new BusinessException("MARKING_PENDING", pending + " attempt(s) still have written answers to mark");
        }
        e.setResultsPublishedAt(clock.instant());
        return view(examRepository.save(e), null);
    }

    /**
     * Posts each student's best attempt to the linked exam paper or assessment (scaled to its maximum); eligible
     * students who did not attempt are marked absent. Allowed once the exam has closed and all answers are marked;
     * posting again overwrites.
     */
    public Posted postMarks(UUID examId) {
        OnlineExam e = staffExam(examId);
        Instant now = clock.instant();
        if (now.isBefore(e.getClosesAt())) {
            throw new BusinessException("EXAM_OPEN", "Post marks after the exam closes");
        }
        submitExpired(e);
        List<OnlineExamAttempt> list = attemptRepository.findByExamIdOrderByStudentIdAscAttemptNoAsc(examId);
        if (list.stream().anyMatch(a -> a.getPendingManual() > 0 || a.getTotalScore() == null)) {
            throw new BusinessException("MARKING_PENDING", "Mark all written answers first");
        }
        Map<UUID, OnlineExamAttempt> best = new LinkedHashMap<>();
        for (OnlineExamAttempt a : list) {
            best.merge(a.getStudentId(), a, (x, y) -> y.getTotalScore().compareTo(x.getTotalScore()) > 0 ? y : x);
        }
        QuestionPaper paper = papers.findPaper(e.getQuestionPaperId());
        Map<UUID, BigDecimal> totals = new LinkedHashMap<>();
        best.forEach((s, a) -> totals.put(s, a.getTotalScore()));
        ScorePosting.PostResult result = posting.post(e.getExamSubjectConfigId(), e.getAssessmentId(), e.getSectionId(), paper.getTotalMarks(), totals,
                "Online exam " + e.getExamCode());
        ScorePosting.Target target = posting.target(e.getExamSubjectConfigId(), e.getAssessmentId());
        List<Leaf> leaves = papers.leaves(paper.getId());
        Map<UUID, List<OnlineExamResponse>> responses = responseRepository.findByAttemptIdIn(best.values().stream().map(OnlineExamAttempt::getId).toList())
                .stream().collect(Collectors.groupingBy(OnlineExamResponse::getAttemptId));
        List<QuestionMark> marks = new ArrayList<>();
        for (OnlineExamAttempt a : best.values()) {
            Map<UUID, BigDecimal> got = new HashMap<>();
            responses.getOrDefault(a.getId(), List.of()).forEach(r -> got.put(r.getPaperItemId(), r.getMarksAwarded()));
            LocalDate on = LocalDate.ofInstant(a.getSubmittedAt() != null ? a.getSubmittedAt() : a.getDeadlineAt(), clock.getZone());
            for (Leaf l : leaves) {
                marks.add(new QuestionMark(a.getStudentId(), l.question().getId(), paper.getId(), got.get(l.item().getId()), l.item().getMarks(), on));
            }
        }
        posting.questionScores(target.termId(), ScoreSource.ONLINE, marks);
        e.setMarksPostedAt(now);
        examRepository.save(e);
        List<String> notes = new ArrayList<>();
        if (result.skippedNotEnrolled() > 0) {
            notes.add(result.skippedNotEnrolled() + " student(s) are no longer enrolled in the class and were not posted");
        }
        return new Posted(examId, result, notes);
    }

    // ───────────────────────────── grading engine ─────────────────────────────

    private void finish(OnlineExamAttempt a, OnlineExam e, AttemptStatus status) {
        Instant now = clock.instant();
        List<Leaf> leaves = papers.leaves(e.getQuestionPaperId());
        Map<UUID, OnlineExamResponse> byItem = responseRepository.findByAttemptId(a.getId()).stream()
                .collect(Collectors.toMap(OnlineExamResponse::getPaperItemId, Function.identity()));
        for (Leaf l : leaves) {
            OnlineExamResponse r = byItem.get(l.item().getId());
            if (r == null) {
                continue;
            }
            Question q = l.question();
            QuestionFormats.Grade g = QuestionFormats.grade(q.getQuestionType(), q.getAnswer(), r.getAnswer(), l.item().getMarks(),
                    e.isNegativeMarking() ? l.item().getNegativeMarks() : BigDecimal.ZERO);
            r.setIsCorrect(g.correct());
            if (g.manual()) {
                r.setNeedsManual(!g.blank());
                r.setMarksAwarded(g.blank() ? BigDecimal.ZERO : r.getMarksAwarded());
            } else {
                r.setNeedsManual(false);
                r.setMarksAwarded(g.marks());
            }
            responseRepository.save(r);
        }
        a.setStatus(status);
        a.setSubmittedAt(status == AttemptStatus.AUTO_SUBMITTED && now.isAfter(a.getDeadlineAt()) ? a.getDeadlineAt() : now);
        recompute(a, leaves);
    }

    private void recompute(OnlineExamAttempt a, List<Leaf> leaves) {
        List<OnlineExamResponse> responses = responseRepository.findByAttemptId(a.getId());
        BigDecimal auto = BigDecimal.ZERO;
        BigDecimal manual = BigDecimal.ZERO;
        int pending = 0;
        Map<UUID, BigDecimal> byItem = new HashMap<>();
        for (OnlineExamResponse r : responses) {
            if (r.isNeedsManual() && r.getMarksAwarded() == null) {
                pending++;
                continue;
            }
            BigDecimal m = r.getMarksAwarded() == null ? BigDecimal.ZERO : r.getMarksAwarded();
            byItem.put(r.getPaperItemId(), m);
            if (r.isNeedsManual()) {
                manual = manual.add(m);
            } else {
                auto = auto.add(m);
            }
        }
        a.setAutoScore(auto);
        a.setManualScore(manual);
        a.setPendingManual(pending);
        a.setTotalScore(pending == 0 ? PaperScoring.total(PaperScoring.of(leaves, byItem)).max(BigDecimal.ZERO) : null);
        if (pending == 0 && a.getStatus() != AttemptStatus.IN_PROGRESS) {
            a.setStatus(AttemptStatus.GRADED);
        }
        attemptRepository.save(a);
    }

    private void submitExpired(OnlineExam e) {
        Instant now = clock.instant();
        for (OnlineExamAttempt a : attemptRepository.findByExamIdOrderByStudentIdAscAttemptNoAsc(e.getId())) {
            if (a.getStatus() == AttemptStatus.IN_PROGRESS) {
                if (!expired(a, now)) {
                    throw new BusinessException("ATTEMPTS_RUNNING", "Some students are still writing the exam");
                }
                finish(attemptRepository.lockById(a.getId()).orElseThrow(), e, AttemptStatus.AUTO_SUBMITTED);
            }
        }
    }

    private static boolean expired(OnlineExamAttempt a, Instant now) {
        return now.isAfter(a.getDeadlineAt().plus(GRACE));
    }

    /** Leaf items in display order: questions shuffled within their section, a passage's parts kept together and in order. */
    static List<UUID> order(List<Leaf> leaves, boolean shuffle, Random random) {
        Map<Integer, Map<UUID, List<UUID>>> sections = new LinkedHashMap<>();
        for (Leaf l : leaves) {
            UUID unit = l.item().getParentItemId() != null ? l.item().getParentItemId() : l.item().getId();
            sections.computeIfAbsent(l.sectionPosition(), k -> new LinkedHashMap<>()).computeIfAbsent(unit, k -> new ArrayList<>()).add(l.item().getId());
        }
        List<UUID> out = new ArrayList<>();
        for (Map<UUID, List<UUID>> units : sections.values()) {
            List<List<UUID>> list = new ArrayList<>(units.values());
            if (shuffle) {
                Collections.shuffle(list, random);
            }
            list.forEach(out::addAll);
        }
        return out;
    }

    /** Shuffled choice order of MCQ items (assertion-reason choices keep their standard order). */
    static Map<String, Object> optionOrder(List<Leaf> leaves, Random random) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Leaf l : leaves) {
            QuestionType t = l.question().getQuestionType();
            if (t == QuestionType.MCQ_SINGLE || t == QuestionType.MCQ_MULTI) {
                List<String> keys = new ArrayList<>(QuestionFormats.choiceKeys(l.question().getOptions()));
                Collections.shuffle(keys, random);
                out.put(l.item().getId().toString(), keys);
            }
        }
        return out;
    }

    // ───────────────────────────── views ─────────────────────────────

    private AttemptView attemptView(OnlineExamAttempt a, OnlineExam e) {
        Instant now = clock.instant();
        Map<UUID, Leaf> leaves = papers.leaves(e.getQuestionPaperId()).stream().collect(Collectors.toMap(l -> l.item().getId(), Function.identity()));
        Map<UUID, OnlineExamResponse> responses = responseRepository.findByAttemptId(a.getId()).stream()
                .collect(Collectors.toMap(OnlineExamResponse::getPaperItemId, Function.identity()));
        Map<UUID, String> passages = new HashMap<>();
        List<UUID> parents = leaves.values().stream().map(l -> l.item().getParentItemId()).filter(Objects::nonNull).distinct().toList();
        if (!parents.isEmpty()) {
            queries.rows("SELECT i.id, q.stem FROM question_paper_item i JOIN question q ON q.id = i.question_id WHERE i.id IN (:ids)", Map.of("ids", parents))
                    .forEach(r -> passages.put(uuid(r[0]), (String) r[1]));
        }
        boolean writing = a.getStatus() == AttemptStatus.IN_PROGRESS;
        List<AttemptItem> items = new ArrayList<>();
        int n = 0;
        for (String id : a.getItemOrder()) {
            Leaf l = leaves.get(UUID.fromString(id));
            if (l == null) {
                continue;
            }
            Question q = l.question();
            OnlineExamResponse r = responses.get(l.item().getId());
            items.add(new AttemptItem(l.item().getId(), ++n, l.sectionPosition(), l.item().getSectionTitle(), l.sectionAttempt(),
                    passages.get(l.item().getParentItemId()), q.getQuestionType(), l.item().getMarks(),
                    e.isNegativeMarking() ? l.item().getNegativeMarks() : BigDecimal.ZERO, q.getStem(), reorder(q.getOptions(), a.getOptionOrder(), id),
                    r == null ? null : r.getAnswer()));
        }
        AttemptResult result = writing || !resultVisible(a, e, now) ? null : result(a, leaves, responses);
        long left = writing ? Math.max(0, Duration.between(now, a.getDeadlineAt()).getSeconds()) : 0;
        return new AttemptView(a.getId(), e.getId(), e.getTitle(), a.getAttemptNo(), a.getStatus(), a.getStartedAt(), a.getDeadlineAt(), left,
                e.getInstructions(), items, result);
    }

    private boolean resultVisible(OnlineExamAttempt a, OnlineExam e, Instant now) {
        if (!a.getStudentId().equals(access.selfStudentId()) && !access.familyStudentIds().contains(a.getStudentId())) {
            return true;
        }
        return switch (e.getResultVisibility()) {
            case IMMEDIATE -> true;
            case AFTER_CLOSE -> !now.isBefore(e.getClosesAt());
            case AFTER_PUBLISH -> e.getResultsPublishedAt() != null;
        };
    }

    private static AttemptResult result(OnlineExamAttempt a, Map<UUID, Leaf> leaves, Map<UUID, OnlineExamResponse> responses) {
        List<ItemResult> items = new ArrayList<>();
        for (String id : a.getItemOrder()) {
            Leaf l = leaves.get(UUID.fromString(id));
            if (l == null) {
                continue;
            }
            OnlineExamResponse r = responses.get(l.item().getId());
            boolean waiting = r != null && r.isNeedsManual() && r.getMarksAwarded() == null;
            items.add(new ItemResult(l.item().getId(), r == null ? BigDecimal.ZERO : r.getMarksAwarded(), r == null ? null : r.getIsCorrect(), waiting,
                    l.question().getAnswer(), l.question().getExplanation(), r == null ? null : r.getFeedback()));
        }
        return new AttemptResult(a.getTotalScore(), a.getMaxScore(), a.getPendingManual(), items);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> reorder(Map<String, Object> options, Map<String, Object> order, String itemId) {
        if (options == null || order == null || !(order.get(itemId) instanceof List<?> keys) || !(options.get("choices") instanceof List<?> choices)) {
            return options;
        }
        Map<String, Object> byKey = new HashMap<>();
        choices.forEach(c -> byKey.put(String.valueOf(((Map<String, Object>) c).get("key")), c));
        List<Object> sorted = new ArrayList<>();
        keys.forEach(k -> {
            Object c = byKey.remove(String.valueOf(k));
            if (c != null) {
                sorted.add(c);
            }
        });
        sorted.addAll(byKey.values());
        Map<String, Object> copy = new LinkedHashMap<>(options);
        copy.put("choices", sorted);
        return copy;
    }

    private ExamView view(OnlineExam e, UUID student) {
        QuestionPaper p = papers.findPaper(e.getQuestionPaperId());
        String subject = queries.rows("SELECT name FROM subject WHERE id = :s", Map.of("s", p.getSubjectId())).stream().map(r -> (String) r[0])
                .findFirst().orElse(null);
        int count = papers.leaves(p.getId()).size();
        Integer mine = student == null ? null : attemptRepository.findByExamIdAndStudentIdOrderByAttemptNo(e.getId(), student).size();
        return new ExamView(e.getId(), e.getExamCode(), e.getTitle(), p.getId(), p.getPaperCode(), p.getSubjectId(), subject, e.getAcademicClassId(),
                e.getSectionId(), e.getExamSubjectConfigId(), e.getAssessmentId(), e.getOpensAt(), e.getClosesAt(), e.getDurationMinutes(),
                e.isShuffleQuestions(), e.isShuffleOptions(), e.isNegativeMarking(), e.getMaxAttempts(), e.getResultVisibility(), e.getInstructions(),
                e.getStatus(), p.getTotalMarks(), count, e.getResultsPublishedAt(), e.getMarksPostedAt(), mine);
    }

    // ───────────────────────────── access ─────────────────────────────

    private OnlineExam find(UUID id) {
        return examRepository.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> new ResourceNotFoundException("OnlineExam", id));
    }

    private OnlineExam staffExam(UUID id) {
        OnlineExam e = find(id);
        staffCheck(e);
        return e;
    }

    private void staffCheck(OnlineExam e) {
        access.requireTeaches(papers.findPaper(e.getQuestionPaperId()).getSubjectId(), e.getAcademicClassId(), "manage online exams");
    }

    private UUID self() {
        UUID s = access.selfStudentId();
        if (s == null) {
            throw new AccessDeniedException("Only a student can take an online exam");
        }
        return s;
    }

    private OnlineExamAttempt ownAttempt(UUID attemptId) {
        OnlineExamAttempt a = attemptRepository.lockById(attemptId).orElseThrow(() -> new ResourceNotFoundException("OnlineExamAttempt", attemptId));
        if (!a.getStudentId().equals(self())) {
            throw new AccessDeniedException("Not your attempt");
        }
        return a;
    }

    private void requireEligible(OnlineExam e, UUID student) {
        Enrolled en = queries.currentEnrolment(student).orElseThrow(() -> new AccessDeniedException("You are not enrolled in a class"));
        if (!en.classId().equals(e.getAcademicClassId()) || (e.getSectionId() != null && !e.getSectionId().equals(en.sectionId()))
                || e.getStatus() == OnlineExamStatus.DRAFT) {
            throw new AccessDeniedException("This exam is not for your class");
        }
    }
}
