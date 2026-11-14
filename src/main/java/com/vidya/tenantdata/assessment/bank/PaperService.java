package com.vidya.tenantdata.assessment.bank;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.common.util.CodeGenerator;
import com.vidya.tenantdata.assessment.AssessmentAccess;
import com.vidya.tenantdata.assessment.AssessmentQueries;
import com.vidya.tenantdata.assessment.bank.BankDtos.AnswerKey;
import com.vidya.tenantdata.assessment.bank.BankDtos.BlueprintCheck;
import com.vidya.tenantdata.assessment.bank.BankDtos.BlueprintRequest;
import com.vidya.tenantdata.assessment.bank.BankDtos.BlueprintResponse;
import com.vidya.tenantdata.assessment.bank.BankDtos.BlueprintSectionRequest;
import com.vidya.tenantdata.assessment.bank.BankDtos.BlueprintSectionResponse;
import com.vidya.tenantdata.assessment.bank.BankDtos.GenerateRequest;
import com.vidya.tenantdata.assessment.bank.BankDtos.KeyEntry;
import com.vidya.tenantdata.assessment.bank.BankDtos.ManualPaperRequest;
import com.vidya.tenantdata.assessment.bank.BankDtos.ManualSection;
import com.vidya.tenantdata.assessment.bank.BankDtos.PaperItemView;
import com.vidya.tenantdata.assessment.bank.BankDtos.PaperResponse;
import com.vidya.tenantdata.assessment.bank.BankDtos.PaperSectionView;
import com.vidya.tenantdata.assessment.bank.BankDtos.SetRef;
import com.vidya.tenantdata.assessment.bank.BankDtos.Shortfall;
import com.vidya.tenantdata.assessment.bank.PaperGenerator.Candidate;
import com.vidya.tenantdata.assessment.bank.PaperGenerator.SectionSpec;
import com.vidya.tenantdata.assessment.entity.Assessment;
import com.vidya.tenantdata.assessment.entity.Blueprint;
import com.vidya.tenantdata.assessment.entity.BlueprintSection;
import com.vidya.tenantdata.assessment.entity.Question;
import com.vidya.tenantdata.assessment.entity.QuestionPaper;
import com.vidya.tenantdata.assessment.entity.QuestionPaperItem;
import com.vidya.tenantdata.assessment.enums.BloomLevel;
import com.vidya.tenantdata.assessment.enums.BlueprintStatus;
import com.vidya.tenantdata.assessment.enums.PaperStatus;
import com.vidya.tenantdata.assessment.enums.QuestionStatus;
import com.vidya.tenantdata.assessment.enums.QuestionType;
import com.vidya.tenantdata.assessment.repository.AssessmentRepository;
import com.vidya.tenantdata.assessment.repository.BlueprintRepository;
import com.vidya.tenantdata.assessment.repository.BlueprintSectionRepository;
import com.vidya.tenantdata.assessment.repository.QuestionPaperItemRepository;
import com.vidya.tenantdata.assessment.repository.QuestionPaperRepository;
import com.vidya.tenantdata.assessment.repository.QuestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.vidya.tenantdata.assessment.AssessmentQueries.params;
import static com.vidya.tenantdata.assessment.AssessmentQueries.uuid;

/**
 * Blueprints and question papers. A paper is generated from a blueprint ({@link PaperGenerator}) in one or more sets,
 * or assembled by hand from chosen questions; any question can be swapped while the paper is a draft. Finalising
 * freezes the paper (and its questions) and records their use, so later papers prefer fresh questions.
 */
@Service
@RequiredArgsConstructor
@Transactional(transactionManager = "tenantTransactionManager")
public class PaperService {

    private static final String SET_LABELS = "ABCDEF";

    private final BlueprintRepository blueprintRepository;
    private final BlueprintSectionRepository sectionRepository;
    private final QuestionPaperRepository paperRepository;
    private final QuestionPaperItemRepository itemRepository;
    private final QuestionRepository questionRepository;
    private final AssessmentRepository assessmentRepository;
    private final AssessmentQueries queries;
    private final AssessmentAccess access;
    private final Clock clock;

    // ───────────────────────────── blueprints ─────────────────────────────

    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public List<BlueprintResponse> blueprints(UUID subjectId, UUID classId) {
        return blueprintRepository.search(subjectId, classId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public BlueprintResponse blueprint(UUID id) {
        return toResponse(findBlueprint(id));
    }

    public BlueprintResponse createBlueprint(BlueprintRequest r) {
        Blueprint b = new Blueprint();
        b.setBlueprintCode(CodeGenerator.blueprintCode());
        b.setCreatedBy(access.actorId());
        return saveBlueprint(b, r);
    }

    public BlueprintResponse updateBlueprint(UUID id, BlueprintRequest r) {
        Blueprint b = findBlueprint(id);
        access.requireTeaches(b.getSubjectId(), b.getAcademicClassId(), "change blueprints");
        return saveBlueprint(b, r);
    }

    public BlueprintResponse archiveBlueprint(UUID id) {
        Blueprint b = findBlueprint(id);
        access.requireTeaches(b.getSubjectId(), b.getAcademicClassId(), "archive blueprints");
        b.setStatus(BlueprintStatus.ARCHIVED);
        return toResponse(blueprintRepository.save(b));
    }

    private BlueprintResponse saveBlueprint(Blueprint b, BlueprintRequest r) {
        access.requireTeaches(r.subjectId(), r.academicClassId(), "design blueprints");
        validate(r);
        b.setName(r.name().trim());
        b.setSubjectId(r.subjectId());
        b.setAcademicClassId(r.academicClassId());
        b.setBoard(r.board());
        b.setTotalMarks(r.totalMarks());
        b.setDurationMinutes(r.durationMinutes());
        b.setInstructions(r.instructions());
        b.setUpdatedBy(access.actorId());
        b = blueprintRepository.save(b);
        sectionRepository.deleteByBlueprint(b.getId());
        sectionRepository.flush();
        int pos = 0;
        for (BlueprintSectionRequest s : r.sections()) {
            BlueprintSection e = new BlueprintSection();
            e.setBlueprintId(b.getId());
            e.setPosition(++pos);
            e.setTitle(s.title().trim());
            e.setInstructions(s.instructions());
            e.setQuestionType(s.questionType());
            e.setMarksPerQuestion(s.marksPerQuestion());
            e.setQuestionCount(s.questionCount());
            e.setAttemptCount(s.attemptCount() != null && s.attemptCount() < s.questionCount() ? s.attemptCount() : null);
            e.setEasyCount(s.easyCount());
            e.setMediumCount(s.mediumCount());
            e.setHardCount(s.hardCount());
            e.setBloomLevels(s.bloomLevels() == null || s.bloomLevels().isEmpty() ? null
                    : s.bloomLevels().stream().map(Enum::name).distinct().collect(Collectors.joining(",")));
            e.setUnitIds(s.unitIds() == null || s.unitIds().isEmpty() ? null : s.unitIds().stream().map(UUID::toString).distinct().toList());
            e.setOutcomeIds(s.outcomeIds() == null || s.outcomeIds().isEmpty() ? null : s.outcomeIds().stream().map(UUID::toString).distinct().toList());
            sectionRepository.save(e);
        }
        return toResponse(b);
    }

    static void validate(BlueprintRequest r) {
        List<String> errors = new ArrayList<>();
        BigDecimal sum = BigDecimal.ZERO;
        int pos = 0;
        for (BlueprintSectionRequest s : r.sections()) {
            pos++;
            if (s.easyCount() + s.mediumCount() + s.hardCount() > s.questionCount()) {
                errors.add("section " + pos + ": easy + medium + hard exceed the question count");
            }
            if (s.attemptCount() != null && s.attemptCount() > s.questionCount()) {
                errors.add("section " + pos + ": attempt count above the question count");
            }
            int counted = s.attemptCount() != null ? Math.min(s.attemptCount(), s.questionCount()) : s.questionCount();
            sum = sum.add(s.marksPerQuestion().multiply(BigDecimal.valueOf(counted)));
        }
        if (sum.compareTo(r.totalMarks()) != 0) {
            errors.add("sections add up to " + sum.stripTrailingZeros().toPlainString() + " marks, not " + r.totalMarks().stripTrailingZeros().toPlainString());
        }
        if (!errors.isEmpty()) {
            throw new BusinessException("INVALID_BLUEPRINT", String.join("; ", errors));
        }
    }

    /** Can the bank fill the blueprint now? */
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public BlueprintCheck check(UUID id) {
        Blueprint b = findBlueprint(id);
        List<Candidate> pool = pool(b.getSubjectId(), b.getAcademicClassId());
        PaperGenerator.Result result = PaperGenerator.generate(specs(b.getId()), pool, Set.of(), 1L);
        return new BlueprintCheck(id, result.complete(), pool.size(), shortfalls(result));
    }

    // ───────────────────────────── papers ─────────────────────────────

    /** Generates set A (and further sets: different questions while the bank allows, else the same questions reordered). */
    public PaperResponse generate(UUID blueprintId, GenerateRequest r) {
        Blueprint b = findBlueprint(blueprintId);
        access.requireTeaches(b.getSubjectId(), b.getAcademicClassId(), "generate papers");
        if (b.getStatus() != BlueprintStatus.ACTIVE) {
            throw new BusinessException("BLUEPRINT_ARCHIVED", "The blueprint is archived");
        }
        validateTarget(r.examSubjectConfigId(), r.assessmentId(), b.getSubjectId(), b.getAcademicClassId());
        List<SectionSpec> specs = specs(b.getId());
        List<BlueprintSection> sections = sectionRepository.findByBlueprintIdOrderByPosition(b.getId());
        List<Candidate> pool = pool(b.getSubjectId(), b.getAcademicClassId());
        Set<UUID> exclude = new HashSet<>();
        if (r.avoidUsedWithinDays() != null && r.avoidUsedWithinDays() > 0) {
            Instant since = clock.instant().minus(Duration.ofDays(r.avoidUsedWithinDays()));
            pool.stream().filter(c -> c.lastUsedAt() != null && c.lastUsedAt().isAfter(since)).forEach(c -> exclude.add(c.id()));
        }
        long seed = r.seed() != null ? r.seed() : new Random().nextLong();
        PaperGenerator.Result first = PaperGenerator.generate(specs, pool, exclude, seed);
        if (!first.complete()) {
            throw new BusinessException("BANK_TOO_SMALL", "Not enough approved questions: "
                    + String.join("; ", first.shortfalls().stream().map(PaperGenerator.Shortfall::message).toList()));
        }
        String title = r.title() != null && !r.title().isBlank() ? r.title().trim() : b.getName();
        QuestionPaper a = newPaper(title, b.getSubjectId(), b.getAcademicClassId(), b.getId(), r.examSubjectConfigId(), r.assessmentId(), "A", null,
                b.getTotalMarks(), b.getDurationMinutes(), b.getInstructions(), seed);
        writeItems(a, sections, first.sections());
        int sets = r.sets() != null ? r.sets() : 1;
        List<String> notes = new ArrayList<>();
        Set<UUID> used = new HashSet<>(exclude);
        first.sections().forEach(used::addAll);
        for (int i = 1; i < sets; i++) {
            String label = String.valueOf(SET_LABELS.charAt(i));
            PaperGenerator.Result next = PaperGenerator.generate(specs, pool, used, seed + i);
            List<List<UUID>> picks;
            if (next.complete()) {
                picks = next.sections();
                next.sections().forEach(used::addAll);
            } else {
                Random shuffle = new Random(seed + i);
                picks = first.sections().stream().map(s -> {
                    List<UUID> copy = new ArrayList<>(s);
                    Collections.shuffle(copy, shuffle);
                    return copy;
                }).toList();
                notes.add("Set " + label + " has the questions of set A in a different order (not enough questions for a separate set)");
            }
            QuestionPaper set = newPaper(title, b.getSubjectId(), b.getAcademicClassId(), b.getId(), r.examSubjectConfigId(), r.assessmentId(), label,
                    a.getId(), b.getTotalMarks(), b.getDurationMinutes(), b.getInstructions(), seed + i);
            writeItems(set, sections, picks);
        }
        return view(a, true, notes);
    }

    public PaperResponse createManual(ManualPaperRequest r) {
        access.requireTeaches(r.subjectId(), r.academicClassId(), "assemble papers");
        validateTarget(r.examSubjectConfigId(), r.assessmentId(), r.subjectId(), r.academicClassId());
        Set<UUID> all = new LinkedHashSet<>();
        r.sections().forEach(s -> s.questionIds().forEach(id -> {
            if (!all.add(id)) {
                throw new BusinessException("DUPLICATE_QUESTION", "A question appears twice in the paper");
            }
        }));
        Map<UUID, Question> questions = questionRepository.findByIdIn(all).stream().filter(q -> q.getDeletedAt() == null)
                .collect(Collectors.toMap(Question::getId, Function.identity()));
        for (UUID id : all) {
            Question q = questions.get(id);
            if (q == null) {
                throw new ResourceNotFoundException("Question", id);
            }
            if (!q.getSubjectId().equals(r.subjectId()) || q.getParentId() != null || q.getStatus() == QuestionStatus.RETIRED) {
                throw new BusinessException("INVALID_QUESTION", q.getQuestionCode() + " is not an active top-level question of the subject");
            }
        }
        BigDecimal total = BigDecimal.ZERO;
        List<BlueprintSection> pseudo = new ArrayList<>();
        List<List<UUID>> picks = new ArrayList<>();
        int pos = 0;
        for (ManualSection s : r.sections()) {
            BlueprintSection bs = new BlueprintSection();
            bs.setPosition(++pos);
            bs.setTitle(s.title().trim());
            bs.setQuestionCount(s.questionIds().size());
            bs.setAttemptCount(s.attemptCount() != null && s.attemptCount() < s.questionIds().size() ? s.attemptCount() : null);
            bs.setMarksPerQuestion(s.marksPerQuestion());
            pseudo.add(bs);
            picks.add(s.questionIds());
            List<BigDecimal> marks = s.questionIds().stream().map(id -> s.marksPerQuestion() != null ? s.marksPerQuestion() : questions.get(id).getMarks())
                    .sorted(Collections.reverseOrder()).toList();
            int counted = bs.getAttemptCount() != null ? bs.getAttemptCount() : marks.size();
            total = total.add(marks.subList(0, counted).stream().reduce(BigDecimal.ZERO, BigDecimal::add));
        }
        QuestionPaper p = newPaper(r.title().trim(), r.subjectId(), r.academicClassId(), null, r.examSubjectConfigId(), r.assessmentId(), "A", null,
                total, r.durationMinutes(), r.instructions(), null);
        writeItems(p, pseudo, picks);
        return view(p, true, List.of());
    }

    /** Swaps a question of a draft paper: the given one, or another approved question of the same type and difficulty. */
    public PaperResponse replaceItem(UUID paperId, UUID itemId, UUID questionId) {
        QuestionPaper p = draft(paperId);
        QuestionPaperItem item = itemRepository.findById(itemId).filter(i -> i.getPaperId().equals(paperId) && i.getParentItemId() == null)
                .orElseThrow(() -> new ResourceNotFoundException("QuestionPaperItem", itemId));
        Question current = questionRepository.findById(item.getQuestionId()).orElseThrow();
        List<QuestionPaperItem> items = itemRepository.findByPaperIdOrderByPosition(paperId);
        Set<UUID> inPaper = items.stream().map(QuestionPaperItem::getQuestionId).collect(Collectors.toSet());
        Question next;
        if (questionId != null) {
            next = questionRepository.findByIdAndDeletedAtIsNull(questionId).orElseThrow(() -> new ResourceNotFoundException("Question", questionId));
            if (!next.getSubjectId().equals(p.getSubjectId()) || next.getParentId() != null || next.getStatus() == QuestionStatus.RETIRED
                    || inPaper.contains(next.getId())) {
                throw new BusinessException("INVALID_QUESTION", "Choose an active top-level question of the subject that is not already in the paper");
            }
        } else {
            next = questionRepository.approvedPool(p.getSubjectId(), p.getAcademicClassId()).stream()
                    .filter(q -> q.getQuestionType() == current.getQuestionType() && q.getDifficulty() == current.getDifficulty()
                            && !inPaper.contains(q.getId()) && (q.getQuestionType() != QuestionType.CASE_BASED || q.getMarks().compareTo(current.getMarks()) == 0))
                    .min((x, y) -> x.getTimesUsed() != y.getTimesUsed() ? Integer.compare(x.getTimesUsed(), y.getTimesUsed())
                            : Objects.compare(x.getLastUsedAt(), y.getLastUsedAt(), (m, n) -> m == null ? -1 : n == null ? 1 : m.compareTo(n)))
                    .orElseThrow(() -> new BusinessException("BANK_TOO_SMALL", "No other approved " + current.getQuestionType() + " / "
                            + current.getDifficulty() + " question is available"));
        }
        items.stream().filter(i -> itemId.equals(i.getParentItemId())).forEach(itemRepository::delete);
        itemRepository.flush();
        item.setQuestionId(next.getId());
        if (next.getQuestionType() == QuestionType.CASE_BASED) {
            item.setMarks(next.getMarks());
        }
        item.setNegativeMarks(next.getNegativeMarks());
        itemRepository.save(item);
        renumberWithChildren(p, item, next);
        return view(p, true, List.of());
    }

    /** Freezes the paper and its other sets and records the use of their questions. */
    public PaperResponse finalise(UUID paperId) {
        QuestionPaper p = draft(paperId);
        if (p.getParentPaperId() != null) {
            throw new BusinessException("FINALISE_SET_A", "Finalise set A; its other sets are finalised with it");
        }
        Instant now = clock.instant();
        List<QuestionPaper> family = new ArrayList<>(List.of(p));
        family.addAll(paperRepository.findByParentPaperIdAndDeletedAtIsNullOrderBySetLabel(paperId));
        Set<UUID> questions = new HashSet<>();
        for (QuestionPaper f : family) {
            List<QuestionPaperItem> items = itemRepository.findByPaperIdOrderByPosition(f.getId());
            if (items.isEmpty()) {
                throw new BusinessException("EMPTY_PAPER", "Set " + f.getSetLabel() + " has no questions");
            }
            items.stream().filter(i -> i.getParentItemId() == null).forEach(i -> questions.add(i.getQuestionId()));
            f.setStatus(PaperStatus.FINALISED);
            f.setFinalisedAt(now);
            paperRepository.save(f);
        }
        queries.update("UPDATE question SET times_used = times_used + 1, last_used_at = :now WHERE id IN (:ids)", params("now", now, "ids", questions));
        return view(p, true, List.of());
    }

    public PaperResponse archive(UUID paperId) {
        QuestionPaper p = findPaper(paperId);
        access.requireTeaches(p.getSubjectId(), p.getAcademicClassId(), "archive papers");
        p.setStatus(PaperStatus.ARCHIVED);
        paperRepository.save(p);
        return view(p, false, List.of());
    }

    public void delete(UUID paperId) {
        QuestionPaper p = draft(paperId);
        Instant now = clock.instant();
        p.setDeletedAt(now);
        paperRepository.save(p);
        paperRepository.findByParentPaperIdAndDeletedAtIsNullOrderBySetLabel(paperId).forEach(s -> {
            s.setDeletedAt(now);
            paperRepository.save(s);
        });
    }

    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public List<PaperResponse> papers(UUID subjectId, UUID classId, PaperStatus status) {
        return paperRepository.search(subjectId, classId, status).stream().map(p -> view(p, false, List.of())).toList();
    }

    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public PaperResponse paper(UUID id, boolean withAnswers) {
        QuestionPaper p = findPaper(id);
        if (withAnswers) {
            access.requireTeaches(p.getSubjectId(), p.getAcademicClassId(), "see answer keys");
        }
        return view(p, withAnswers, List.of());
    }

    /** Answer key of the auto-marked questions in OMR order (question n on the sheet = entry n). */
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public AnswerKey answerKey(UUID id) {
        QuestionPaper p = findPaper(id);
        access.requireTeaches(p.getSubjectId(), p.getAcademicClassId(), "see answer keys");
        List<KeyEntry> entries = new ArrayList<>();
        for (Leaf l : leaves(p.getId())) {
            if (l.omrNumber() != null) {
                entries.add(new KeyEntry(l.omrNumber(), l.item().getPosition(), l.question().getQuestionType(),
                        QuestionFormats.choiceKeys(l.question().getOptions()), l.question().getAnswer(), l.item().getMarks()));
            }
        }
        return new AnswerKey(p.getId(), p.getSetLabel(), entries);
    }

    // ───────────────────────────── shared with online exams and OMR ─────────────────────────────

    /** An answerable question of a paper (case-based passages are represented by their child questions). */
    public record Leaf(QuestionPaperItem item, Question question, int sectionPosition, Integer sectionAttempt, Integer omrNumber) {
    }

    public List<Leaf> leaves(UUID paperId) {
        List<QuestionPaperItem> items = itemRepository.findByPaperIdOrderByPosition(paperId);
        Set<UUID> parents = items.stream().map(QuestionPaperItem::getParentItemId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, Question> questions = questionRepository.findByIdIn(items.stream().map(QuestionPaperItem::getQuestionId).toList()).stream()
                .collect(Collectors.toMap(Question::getId, Function.identity()));
        List<Leaf> out = new ArrayList<>();
        int omr = 0;
        for (QuestionPaperItem i : items) {
            if (parents.contains(i.getId())) {
                continue;
            }
            Question q = questions.get(i.getQuestionId());
            boolean bubble = QuestionFormats.OMR.contains(q.getQuestionType());
            out.add(new Leaf(i, q, i.getSectionPosition(), i.getSectionAttempt(), bubble ? ++omr : null));
        }
        return out;
    }

    public QuestionPaper findPaper(UUID id) {
        return paperRepository.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> new ResourceNotFoundException("QuestionPaper", id));
    }

    // ───────────────────────────── helpers ─────────────────────────────

    private QuestionPaper newPaper(String title, UUID subjectId, UUID classId, UUID blueprintId, UUID configId, UUID assessmentId, String set,
                                   UUID parentId, BigDecimal total, Integer duration, String instructions, Long seed) {
        QuestionPaper p = new QuestionPaper();
        p.setPaperCode(CodeGenerator.questionPaperCode());
        p.setTitle(title);
        p.setSubjectId(subjectId);
        p.setAcademicClassId(classId);
        p.setBlueprintId(blueprintId);
        p.setExamSubjectConfigId(configId);
        p.setAssessmentId(assessmentId);
        p.setSetLabel(set);
        p.setParentPaperId(parentId);
        p.setTotalMarks(total);
        p.setDurationMinutes(duration);
        p.setInstructions(instructions);
        p.setGenerationSeed(seed);
        p.setCreatedBy(access.actorId());
        return paperRepository.save(p);
    }

    /** Items in section order; a case-based passage is followed by its child questions. */
    private void writeItems(QuestionPaper p, List<BlueprintSection> sections, List<List<UUID>> picks) {
        Map<UUID, Question> questions = questionRepository.findByIdIn(picks.stream().flatMap(List::stream).toList()).stream()
                .collect(Collectors.toMap(Question::getId, Function.identity()));
        Map<UUID, List<Question>> children = new HashMap<>();
        questionRepository.findByParentIdInAndDeletedAtIsNullOrderByCreatedAt(questions.keySet())
                .forEach(c -> children.computeIfAbsent(c.getParentId(), k -> new ArrayList<>()).add(c));
        int position = 0;
        for (int s = 0; s < sections.size(); s++) {
            BlueprintSection sec = sections.get(s);
            for (UUID qid : picks.get(s)) {
                Question q = questions.get(qid);
                QuestionPaperItem item = item(p.getId(), sec, ++position, q, null,
                        q.getQuestionType() == QuestionType.CASE_BASED || sec.getMarksPerQuestion() == null ? q.getMarks() : sec.getMarksPerQuestion());
                for (Question c : children.getOrDefault(qid, List.of())) {
                    item(p.getId(), sec, ++position, c, item.getId(), c.getMarks());
                }
            }
        }
    }

    private QuestionPaperItem item(UUID paperId, BlueprintSection sec, int position, Question q, UUID parentItem, BigDecimal marks) {
        QuestionPaperItem i = new QuestionPaperItem();
        i.setPaperId(paperId);
        i.setSectionPosition(sec.getPosition());
        i.setSectionTitle(sec.getTitle());
        i.setSectionAttempt(sec.getAttemptCount());
        i.setPosition(position);
        i.setQuestionId(q.getId());
        i.setParentItemId(parentItem);
        i.setMarks(marks);
        i.setNegativeMarks(q.getNegativeMarks());
        return itemRepository.save(i);
    }

    /** After a swap: re-add child items for a case-based question and renumber positions. */
    private void renumberWithChildren(QuestionPaper p, QuestionPaperItem swapped, Question next) {
        List<QuestionPaperItem> items = new ArrayList<>(itemRepository.findByPaperIdOrderByPosition(p.getId()));
        int offset = 100000;
        for (QuestionPaperItem i : items) {
            i.setPosition(i.getPosition() + offset);
            itemRepository.save(i);
        }
        itemRepository.flush();
        int pos = 0;
        for (QuestionPaperItem i : items) {
            i.setPosition(++pos);
            itemRepository.save(i);
            if (i.getId().equals(swapped.getId()) && next.getQuestionType() == QuestionType.CASE_BASED) {
                for (Question c : questionRepository.findByParentIdAndDeletedAtIsNullOrderByCreatedAt(next.getId())) {
                    QuestionPaperItem child = new QuestionPaperItem();
                    child.setPaperId(p.getId());
                    child.setSectionPosition(i.getSectionPosition());
                    child.setSectionTitle(i.getSectionTitle());
                    child.setSectionAttempt(i.getSectionAttempt());
                    child.setPosition(++pos);
                    child.setQuestionId(c.getId());
                    child.setParentItemId(i.getId());
                    child.setMarks(c.getMarks());
                    child.setNegativeMarks(c.getNegativeMarks());
                    itemRepository.save(child);
                }
            }
        }
    }

    private void validateTarget(UUID configId, UUID assessmentId, UUID subjectId, UUID classId) {
        if (configId != null && assessmentId != null) {
            throw new BusinessException("INVALID_TARGET", "Link the paper to an exam paper or an assessment, not both");
        }
        if (configId != null) {
            var c = queries.config(configId).orElseThrow(() -> new ResourceNotFoundException("ExamSubjectConfig", configId));
            if (!c.subjectId().equals(subjectId) || !c.classId().equals(classId)) {
                throw new BusinessException("INVALID_TARGET", "The exam paper is of another subject or class");
            }
        }
        if (assessmentId != null) {
            Assessment a = assessmentRepository.findByIdAndDeletedAtIsNull(assessmentId).orElseThrow(() -> new ResourceNotFoundException("Assessment", assessmentId));
            if (!a.getSubjectId().equals(subjectId) || !a.getAcademicClassId().equals(classId)) {
                throw new BusinessException("INVALID_TARGET", "The assessment is of another subject or class");
            }
        }
    }

    private List<SectionSpec> specs(UUID blueprintId) {
        return sectionRepository.findByBlueprintIdOrderByPosition(blueprintId).stream().map(s -> new SectionSpec(s.getPosition(), s.getTitle(),
                s.getQuestionType(), s.getMarksPerQuestion(), s.getQuestionCount(), s.getEasyCount(), s.getMediumCount(), s.getHardCount(),
                blooms(s.getBloomLevels()), uuids(s.getUnitIds()), uuids(s.getOutcomeIds()))).toList();
    }

    private List<Candidate> pool(UUID subjectId, UUID classId) {
        List<Question> approved = questionRepository.approvedPool(subjectId, classId);
        Map<UUID, Set<UUID>> outcomes = new HashMap<>();
        if (!approved.isEmpty()) {
            queries.rows("SELECT q.id, qo.learning_outcome_id FROM question q JOIN question_outcome qo ON qo.question_id = q.id "
                            + "OR qo.question_id IN (SELECT c.id FROM question c WHERE c.parent_id = q.id) WHERE q.id IN (:ids)",
                    Map.of("ids", approved.stream().map(Question::getId).toList()))
                    .forEach(r -> outcomes.computeIfAbsent(uuid(r[0]), k -> new HashSet<>()).add(uuid(r[1])));
        }
        return approved.stream().map(q -> new Candidate(q.getId(), q.getQuestionType(), q.getDifficulty(), q.getBloomLevel(), q.getMarks(),
                q.getSyllabusUnitId(), outcomes.getOrDefault(q.getId(), Set.of()), q.getTimesUsed(), q.getLastUsedAt())).toList();
    }

    private static List<Shortfall> shortfalls(PaperGenerator.Result r) {
        return r.shortfalls().stream().map(s -> new Shortfall(s.sectionPosition(), s.sectionTitle(), s.difficulty(), s.wanted(), s.available(),
                s.message())).toList();
    }

    private Blueprint findBlueprint(UUID id) {
        return blueprintRepository.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> new ResourceNotFoundException("Blueprint", id));
    }

    private QuestionPaper draft(UUID id) {
        QuestionPaper p = findPaper(id);
        access.requireTeaches(p.getSubjectId(), p.getAcademicClassId(), "change papers");
        if (p.getStatus() != PaperStatus.DRAFT) {
            throw new BusinessException("PAPER_FINALISED", "The paper is " + p.getStatus() + " and cannot change");
        }
        return p;
    }

    private static Set<BloomLevel> blooms(String csv) {
        if (csv == null || csv.isBlank()) {
            return Set.of();
        }
        Set<BloomLevel> out = new HashSet<>();
        for (String s : csv.split(",")) {
            out.add(BloomLevel.valueOf(s.trim()));
        }
        return out;
    }

    private static Set<UUID> uuids(List<String> list) {
        return list == null ? Set.of() : list.stream().map(UUID::fromString).collect(Collectors.toSet());
    }

    private BlueprintResponse toResponse(Blueprint b) {
        Map<UUID, String> names = new HashMap<>();
        queries.rows("SELECT id, name FROM subject WHERE id = :s UNION ALL SELECT id, name FROM academic_class WHERE id = :c",
                params("s", b.getSubjectId(), "c", b.getAcademicClassId())).forEach(r -> names.put(uuid(r[0]), (String) r[1]));
        List<BlueprintSectionResponse> sections = sectionRepository.findByBlueprintIdOrderByPosition(b.getId()).stream()
                .map(s -> new BlueprintSectionResponse(s.getId(), s.getPosition(), s.getTitle(), s.getInstructions(), s.getQuestionType(),
                        s.getMarksPerQuestion(), s.getQuestionCount(), s.getAttemptCount(), s.getEasyCount(), s.getMediumCount(), s.getHardCount(),
                        blooms(s.getBloomLevels()).stream().sorted().toList(), List.copyOf(uuids(s.getUnitIds())), List.copyOf(uuids(s.getOutcomeIds())),
                        s.getMarksPerQuestion().multiply(BigDecimal.valueOf(s.getAttemptCount() != null ? s.getAttemptCount() : s.getQuestionCount()))))
                .toList();
        return new BlueprintResponse(b.getId(), b.getBlueprintCode(), b.getName(), b.getSubjectId(), names.get(b.getSubjectId()), b.getAcademicClassId(),
                names.get(b.getAcademicClassId()), b.getBoard(), b.getTotalMarks(), b.getDurationMinutes(), b.getInstructions(), b.getStatus(), sections,
                b.getUpdatedAt());
    }

    PaperResponse view(QuestionPaper p, boolean full, List<String> notes) {
        Map<UUID, String> names = new HashMap<>();
        queries.rows("SELECT id, name FROM subject WHERE id = :s UNION ALL SELECT id, name FROM academic_class WHERE id = :c",
                params("s", p.getSubjectId(), "c", p.getAcademicClassId())).forEach(r -> names.put(uuid(r[0]), (String) r[1]));
        List<PaperSectionView> sections = null;
        if (full) {
            List<QuestionPaperItem> items = itemRepository.findByPaperIdOrderByPosition(p.getId());
            Map<UUID, Question> questions = questionRepository.findByIdIn(items.stream().map(QuestionPaperItem::getQuestionId).toList()).stream()
                    .collect(Collectors.toMap(Question::getId, Function.identity()));
            Map<UUID, Integer> omr = new HashMap<>();
            leaves(p.getId()).forEach(l -> {
                if (l.omrNumber() != null) {
                    omr.put(l.item().getId(), l.omrNumber());
                }
            });
            boolean answers = access.teaches(p.getSubjectId(), p.getAcademicClassId());
            Map<Integer, List<QuestionPaperItem>> bySection = items.stream()
                    .collect(Collectors.groupingBy(QuestionPaperItem::getSectionPosition, LinkedHashMap::new, Collectors.toList()));
            sections = bySection.entrySet().stream().map(e -> {
                List<QuestionPaperItem> list = e.getValue();
                QuestionPaperItem first = list.get(0);
                List<BigDecimal> top = list.stream().filter(i -> i.getParentItemId() == null).map(QuestionPaperItem::getMarks)
                        .sorted(Collections.reverseOrder()).toList();
                int counted = first.getSectionAttempt() != null ? Math.min(first.getSectionAttempt(), top.size()) : top.size();
                return new PaperSectionView(e.getKey(), first.getSectionTitle(), first.getSectionAttempt(),
                        top.subList(0, counted).stream().reduce(BigDecimal.ZERO, BigDecimal::add),
                        list.stream().map(i -> {
                            Question q = questions.get(i.getQuestionId());
                            return new PaperItemView(i.getId(), i.getPosition(), i.getParentItemId(), q.getId(), q.getQuestionCode(), q.getQuestionType(),
                                    q.getDifficulty(), q.getBloomLevel(), i.getMarks(), i.getNegativeMarks(), q.getStem(), q.getOptions(),
                                    answers ? q.getAnswer() : null, answers ? q.getExplanation() : null, omr.get(i.getId()));
                        }).toList());
            }).toList();
        }
        UUID root = p.getParentPaperId() != null ? p.getParentPaperId() : p.getId();
        List<SetRef> sets = new ArrayList<>();
        if (!root.equals(p.getId())) {
            paperRepository.findById(root).ifPresent(r -> sets.add(new SetRef(r.getId(), r.getSetLabel(), r.getPaperCode())));
        }
        paperRepository.findByParentPaperIdAndDeletedAtIsNullOrderBySetLabel(root).stream().filter(s -> !s.getId().equals(p.getId()))
                .forEach(s -> sets.add(new SetRef(s.getId(), s.getSetLabel(), s.getPaperCode())));
        return new PaperResponse(p.getId(), p.getPaperCode(), p.getTitle(), p.getSubjectId(), names.get(p.getSubjectId()), p.getAcademicClassId(),
                names.get(p.getAcademicClassId()), p.getBlueprintId(), p.getExamSubjectConfigId(), p.getAssessmentId(), p.getSetLabel(),
                p.getParentPaperId(), p.getTotalMarks(), p.getDurationMinutes(), p.getInstructions(), p.getGenerationSeed(), p.getStatus(),
                p.getFinalisedAt(), sections, sets, notes);
    }
}
