package com.vidya.tenantdata.assessment.bank;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.common.util.CodeGenerator;
import com.vidya.tenantdata.assessment.AssessmentAccess;
import com.vidya.tenantdata.assessment.AssessmentQueries;
import com.vidya.tenantdata.assessment.bank.BankDtos.PoolCount;
import com.vidya.tenantdata.assessment.bank.BankDtos.QuestionRequest;
import com.vidya.tenantdata.assessment.bank.BankDtos.QuestionResponse;
import com.vidya.tenantdata.assessment.entity.LearningOutcome;
import com.vidya.tenantdata.assessment.entity.Question;
import com.vidya.tenantdata.assessment.enums.Difficulty;
import com.vidya.tenantdata.assessment.enums.QuestionStatus;
import com.vidya.tenantdata.assessment.enums.QuestionType;
import com.vidya.tenantdata.assessment.repository.LearningOutcomeRepository;
import com.vidya.tenantdata.assessment.repository.QuestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static com.vidya.tenantdata.assessment.AssessmentQueries.params;
import static com.vidya.tenantdata.assessment.AssessmentQueries.uuid;

/**
 * Question bank: questions in ten formats tagged with subject, class, syllabus unit / topic, difficulty, Bloom level and
 * learning outcomes. Teachers write questions for subjects they teach; a reviewer (QUESTION_BANK_REVIEW, not the author)
 * approves them. Only approved questions go into generated papers. A question used in a finalised paper is frozen:
 * retire it and write a new version instead.
 */
@Service
@RequiredArgsConstructor
@Transactional(transactionManager = "tenantTransactionManager")
public class QuestionService {

    private final QuestionRepository repository;
    private final LearningOutcomeRepository outcomeRepository;
    private final AssessmentQueries queries;
    private final AssessmentAccess access;
    private final Clock clock;

    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public Page<QuestionResponse> search(UUID subjectId, UUID classId, QuestionType type, Difficulty difficulty, QuestionStatus status, UUID unitId,
                                         String text, Pageable pageable) {
        Page<Question> page = repository.search(subjectId, classId, type, difficulty, status, unitId,
                text == null || text.isBlank() ? null : text.trim(), pageable);
        List<QuestionResponse> views = toResponses(page.getContent());
        Map<UUID, QuestionResponse> byId = new HashMap<>();
        views.forEach(v -> byId.put(v.id(), v));
        return page.map(q -> byId.get(q.getId()));
    }

    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public QuestionResponse get(UUID id) {
        return toResponses(List.of(find(id))).get(0);
    }

    /** Approved questions available per type and difficulty (to plan blueprints). */
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public List<PoolCount> pool(UUID subjectId, UUID classId) {
        return queries.rows("SELECT question_type, difficulty, count(*) FROM question WHERE deleted_at IS NULL AND parent_id IS NULL "
                        + "AND status = 'APPROVED' AND subject_id = :s AND (CAST(:c AS uuid) IS NULL OR academic_class_id IS NULL OR academic_class_id = :c) "
                        + "GROUP BY question_type, difficulty ORDER BY question_type, difficulty", params("s", subjectId, "c", classId)).stream()
                .map(r -> new PoolCount(QuestionType.valueOf((String) r[0]), Difficulty.valueOf((String) r[1]), ((Number) r[2]).longValue())).toList();
    }

    public QuestionResponse create(QuestionRequest r) {
        return get(createOne(r, null).getId());
    }

    /** All or nothing. */
    public List<QuestionResponse> createAll(List<QuestionRequest> requests) {
        List<Question> saved = new ArrayList<>();
        for (QuestionRequest r : requests) {
            saved.add(createOne(r, null));
        }
        return toResponses(saved);
    }

    private Question createOne(QuestionRequest r, Question parent) {
        validate(r, parent);
        Question q = new Question();
        q.setQuestionCode(CodeGenerator.questionCode());
        q.setCreatedBy(access.actorId());
        apply(q, r, parent);
        q = repository.save(q);
        setOutcomes(q.getId(), r.outcomeIds());
        if (r.questionType() == QuestionType.CASE_BASED) {
            for (QuestionRequest child : r.children()) {
                createOne(child, q);
            }
        }
        return q;
    }

    public QuestionResponse update(UUID id, QuestionRequest r) {
        Question q = find(id);
        access.requireTeaches(q.getSubjectId(), q.getAcademicClassId(), "edit questions");
        requireNotFrozen(q);
        validate(r, null);
        if (q.getParentId() != null) {
            throw new BusinessException("CHILD_QUESTION", "Edit the case-based question this question belongs to");
        }
        apply(q, r, null);
        q.setStatus(QuestionStatus.DRAFT);
        q.setReviewedAt(null);
        q.setReviewedBy(null);
        q.setUpdatedBy(access.actorId());
        repository.save(q);
        setOutcomes(q.getId(), r.outcomeIds());
        if (r.questionType() == QuestionType.CASE_BASED || !repository.findByParentIdAndDeletedAtIsNullOrderByCreatedAt(id).isEmpty()) {
            Instant now = clock.instant();
            for (Question old : repository.findByParentIdAndDeletedAtIsNullOrderByCreatedAt(id)) {
                old.setDeletedAt(now);
                repository.save(old);
            }
            if (r.children() != null && r.questionType() == QuestionType.CASE_BASED) {
                for (QuestionRequest child : r.children()) {
                    createOne(child, q);
                }
            }
        }
        return get(id);
    }

    public QuestionResponse submit(UUID id) {
        Question q = find(id);
        access.requireTeaches(q.getSubjectId(), q.getAcademicClassId(), "submit questions");
        if (q.getStatus() != QuestionStatus.DRAFT) {
            throw new BusinessException("NOT_DRAFT", "Only a draft can be submitted for review");
        }
        q.setStatus(QuestionStatus.SUBMITTED);
        repository.save(q);
        return get(id);
    }

    public QuestionResponse review(UUID id, boolean approve, String note) {
        Question q = find(id);
        if (q.getStatus() != QuestionStatus.SUBMITTED && q.getStatus() != QuestionStatus.DRAFT) {
            throw new BusinessException("NOT_FOR_REVIEW", "The question is " + q.getStatus());
        }
        if (Objects.equals(q.getCreatedBy(), access.actorId()) && !access.all()) {
            throw new AccessDeniedException("Another teacher must review your question");
        }
        access.requireTeaches(q.getSubjectId(), null, "review questions");
        q.setStatus(approve ? QuestionStatus.APPROVED : QuestionStatus.DRAFT);
        q.setReviewedBy(access.actorId());
        q.setReviewedAt(clock.instant());
        q.setReviewNote(note);
        repository.save(q);
        for (Question child : repository.findByParentIdAndDeletedAtIsNullOrderByCreatedAt(id)) {
            child.setStatus(q.getStatus());
            repository.save(child);
        }
        return get(id);
    }

    public QuestionResponse retire(UUID id) {
        Question q = find(id);
        access.requireTeaches(q.getSubjectId(), q.getAcademicClassId(), "retire questions");
        q.setStatus(QuestionStatus.RETIRED);
        repository.save(q);
        return get(id);
    }

    /** Deletes a question never used in a paper; a used one is retired instead. */
    public void delete(UUID id) {
        Question q = find(id);
        access.requireTeaches(q.getSubjectId(), q.getAcademicClassId(), "delete questions");
        if (usedInPapers(id) > 0) {
            q.setStatus(QuestionStatus.RETIRED);
            repository.save(q);
            return;
        }
        Instant now = clock.instant();
        q.setDeletedAt(now);
        repository.save(q);
        repository.findByParentIdAndDeletedAtIsNullOrderByCreatedAt(id).forEach(c -> {
            c.setDeletedAt(now);
            repository.save(c);
        });
    }

    // ───────────────────────────── helpers ─────────────────────────────

    Question find(UUID id) {
        return repository.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> new ResourceNotFoundException("Question", id));
    }

    private void validate(QuestionRequest r, Question parent) {
        access.requireTeaches(r.subjectId(), r.academicClassId(), "write questions");
        QuestionType type = r.questionType();
        if (parent != null && type == QuestionType.CASE_BASED) {
            throw new BusinessException("INVALID_QUESTION", "A case-based question cannot contain another case-based question");
        }
        if (parent != null && !parent.getSubjectId().equals(r.subjectId())) {
            throw new BusinessException("INVALID_QUESTION", "Child questions must be of the passage's subject");
        }
        if (type == QuestionType.CASE_BASED) {
            if (r.children() == null || r.children().isEmpty()) {
                throw new BusinessException("INVALID_QUESTION", "A case-based question needs its child questions");
            }
        } else {
            if (r.children() != null && !r.children().isEmpty()) {
                throw new BusinessException("INVALID_QUESTION", "Only a case-based question has child questions");
            }
            if (r.marks() == null || r.marks().signum() <= 0) {
                throw new BusinessException("INVALID_QUESTION", "Give the marks of the question");
            }
        }
        QuestionFormats.validate(type, r.options(), r.answer());
        if (r.syllabusUnitId() != null && queries.rows("SELECT u.id FROM syllabus_unit u JOIN syllabus s ON s.id = u.syllabus_id "
                + "WHERE u.id = :u AND s.subject_id = :s", params("u", r.syllabusUnitId(), "s", r.subjectId())).isEmpty()) {
            throw new BusinessException("INVALID_QUESTION", "The syllabus unit is not of the question's subject");
        }
        if (r.syllabusTopicId() != null && queries.rows("SELECT t.id FROM syllabus_topic t JOIN syllabus s ON s.id = t.syllabus_id "
                + "WHERE t.id = :t AND s.subject_id = :s AND (CAST(:u AS uuid) IS NULL OR t.unit_id = :u)",
                params("t", r.syllabusTopicId(), "s", r.subjectId(), "u", r.syllabusUnitId())).isEmpty()) {
            throw new BusinessException("INVALID_QUESTION", "The syllabus topic is not of the question's subject / unit");
        }
        if (r.outcomeIds() != null && !r.outcomeIds().isEmpty()) {
            Set<UUID> ids = new LinkedHashSet<>(r.outcomeIds());
            List<LearningOutcome> found = outcomeRepository.findByIdInAndDeletedAtIsNull(ids);
            if (found.size() != ids.size() || found.stream().anyMatch(o -> !o.getSubjectId().equals(r.subjectId()))) {
                throw new BusinessException("INVALID_QUESTION", "Learning outcomes must exist and be of the question's subject");
            }
        }
    }

    private void apply(Question q, QuestionRequest r, Question parent) {
        q.setSubjectId(r.subjectId());
        q.setAcademicClassId(parent != null ? parent.getAcademicClassId() : r.academicClassId());
        q.setSyllabusUnitId(r.syllabusUnitId() != null || parent == null ? r.syllabusUnitId() : parent.getSyllabusUnitId());
        q.setSyllabusTopicId(r.syllabusTopicId());
        q.setParentId(parent == null ? null : parent.getId());
        q.setQuestionType(r.questionType());
        q.setDifficulty(r.difficulty() != null ? r.difficulty() : parent != null ? parent.getDifficulty() : Difficulty.MEDIUM);
        q.setBloomLevel(r.bloomLevel());
        BigDecimal marks = r.questionType() == QuestionType.CASE_BASED
                ? r.children().stream().map(QuestionRequest::marks).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add) : r.marks();
        q.setMarks(marks);
        q.setNegativeMarks(r.negativeMarks() != null ? r.negativeMarks() : BigDecimal.ZERO);
        q.setStem(r.stem());
        q.setOptions(r.options());
        q.setAnswer(r.answer());
        q.setExplanation(r.explanation());
        q.setExpectedSeconds(r.expectedSeconds());
        q.setLanguage(r.language() != null && !r.language().isBlank() ? r.language() : "en");
        q.setBoard(r.board());
        q.setSource(r.source());
        if (parent != null) {
            q.setStatus(parent.getStatus());
            q.setCreatedBy(parent.getCreatedBy());
        }
    }

    private void setOutcomes(UUID questionId, List<UUID> outcomeIds) {
        queries.update("DELETE FROM question_outcome WHERE question_id = :q", Map.of("q", questionId));
        if (outcomeIds != null) {
            for (UUID o : new LinkedHashSet<>(outcomeIds)) {
                queries.update("INSERT INTO question_outcome (question_id, learning_outcome_id) VALUES (:q, :o)", params("q", questionId, "o", o));
            }
        }
    }

    private void requireNotFrozen(Question q) {
        long finalised = ((Number) queries.rows("SELECT count(*) FROM question_paper_item i JOIN question_paper p ON p.id = i.paper_id "
                + "WHERE p.status = 'FINALISED' AND p.deleted_at IS NULL AND (i.question_id = :q OR i.question_id IN "
                + "(SELECT id FROM question WHERE parent_id = :q))", Map.of("q", q.getId())).get(0)[0]).longValue();
        if (finalised > 0) {
            throw new BusinessException("QUESTION_IN_USE", "The question is in a finalised paper; retire it and add a new version");
        }
    }

    private long usedInPapers(UUID id) {
        return ((Number) queries.rows("SELECT count(*) FROM question_paper_item WHERE question_id = :q OR question_id IN "
                + "(SELECT id FROM question WHERE parent_id = :q)", Map.of("q", id)).get(0)[0]).longValue();
    }

    List<QuestionResponse> toResponses(List<Question> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = list.stream().map(Question::getId).toList();
        Map<UUID, List<Question>> children = new HashMap<>();
        repository.findByParentIdInAndDeletedAtIsNullOrderByCreatedAt(ids).forEach(c -> children.computeIfAbsent(c.getParentId(), k -> new ArrayList<>()).add(c));
        List<UUID> all = new ArrayList<>(ids);
        children.values().forEach(cs -> cs.forEach(c -> all.add(c.getId())));
        Map<UUID, List<UUID>> outcomes = new HashMap<>();
        queries.rows("SELECT question_id, learning_outcome_id FROM question_outcome WHERE question_id IN (:ids)", Map.of("ids", all))
                .forEach(r -> outcomes.computeIfAbsent(uuid(r[0]), k -> new ArrayList<>()).add(uuid(r[1])));
        Map<UUID, String> names = new HashMap<>();
        queries.rows("SELECT id, name FROM subject WHERE id IN (:s) UNION ALL SELECT id, name FROM academic_class WHERE id IN (:c)",
                params("s", list.stream().map(Question::getSubjectId).distinct().toList(),
                        "c", list.stream().map(q -> q.getAcademicClassId() != null ? q.getAcademicClassId() : q.getSubjectId()).distinct().toList()))
                .forEach(r -> names.put(uuid(r[0]), (String) r[1]));
        return list.stream().map(q -> view(q, names, outcomes, children.getOrDefault(q.getId(), List.of()).stream()
                .map(c -> view(c, names, outcomes, List.of())).toList())).toList();
    }

    private static QuestionResponse view(Question q, Map<UUID, String> names, Map<UUID, List<UUID>> outcomes, List<QuestionResponse> children) {
        return new QuestionResponse(q.getId(), q.getQuestionCode(), q.getSubjectId(), names.get(q.getSubjectId()), q.getAcademicClassId(),
                q.getAcademicClassId() == null ? null : names.get(q.getAcademicClassId()), q.getSyllabusUnitId(), q.getSyllabusTopicId(), q.getParentId(),
                q.getQuestionType(), q.getDifficulty(), q.getBloomLevel(), q.getMarks(), q.getNegativeMarks(), q.getStem(), q.getOptions(), q.getAnswer(),
                q.getExplanation(), q.getExpectedSeconds(), q.getLanguage(), q.getBoard(), q.getSource(), q.getStatus(), q.getReviewedBy(),
                q.getReviewedAt(), q.getReviewNote(), q.getTimesUsed(), q.getLastUsedAt(), q.getFacilityIndex(), q.getDiscriminationIndex(),
                q.getResponsesAnalysed(), outcomes.getOrDefault(q.getId(), List.of()), children, q.getCreatedBy(), q.getUpdatedAt());
    }
}
