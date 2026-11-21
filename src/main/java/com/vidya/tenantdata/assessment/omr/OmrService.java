package com.vidya.tenantdata.assessment.omr;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.common.util.CodeGenerator;
import com.vidya.tenantdata.assessment.AssessmentAccess;
import com.vidya.tenantdata.assessment.repository.AssessmentQueries;
import com.vidya.tenantdata.assessment.repository.AssessmentQueries.Enrolled;
import com.vidya.tenantdata.assessment.bank.PaperScoring;
import com.vidya.tenantdata.assessment.bank.PaperService;
import com.vidya.tenantdata.assessment.bank.PaperService.Leaf;
import com.vidya.tenantdata.assessment.bank.QuestionFormats;
import com.vidya.tenantdata.assessment.bank.ScorePosting;
import com.vidya.tenantdata.assessment.bank.ScorePosting.QuestionMark;
import com.vidya.tenantdata.assessment.entity.OmrBatch;
import com.vidya.tenantdata.assessment.entity.OmrSheet;
import com.vidya.tenantdata.assessment.entity.Question;
import com.vidya.tenantdata.assessment.entity.QuestionPaper;
import com.vidya.tenantdata.assessment.enums.OmrBatchStatus;
import com.vidya.tenantdata.assessment.enums.PaperStatus;
import com.vidya.tenantdata.assessment.enums.ScoreSource;
import com.vidya.tenantdata.assessment.omr.OmrParser.Row;
import com.vidya.tenantdata.assessment.omr.OmrParser.StudentKey;
import com.vidya.tenantdata.assessment.omr.OmrParser.Token;
import com.vidya.tenantdata.assessment.repository.OmrBatchRepository;
import com.vidya.tenantdata.assessment.repository.OmrSheetRepository;
import com.vidya.tenantdata.assessment.repository.QuestionPaperRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * OMR evaluation: scanner output (CSV, or JSON rows) is matched to students, scored against the answer key of the
 * sheet's set, and kept as a batch to review; posting sends the scores to the linked exam paper or assessment.
 * Bubble n is the n-th auto-marked question of the set (see the paper's answer key). Only papers made entirely of
 * bubble-able questions can be posted; for mixed papers the OMR score is the objective part only.
 */
@Service
@RequiredArgsConstructor
@Transactional(transactionManager = "tenantTransactionManager")
public class OmrService {

    private final OmrBatchRepository batchRepository;
    private final OmrSheetRepository sheetRepository;
    private final QuestionPaperRepository paperRepository;
    private final PaperService papers;
    private final ScorePosting posting;
    private final AssessmentQueries queries;
    private final AssessmentAccess access;
    private final Clock clock;

    public record SheetInput(String studentCode, String admissionNumber, String rollNumber, @Size(max = 5) String setLabel,
                             @NotBlank @Size(max = 2000) String answers) {
    }

    /** Either {@code csv} (scanner export) or {@code sheets}. Marks go to the paper's exam paper / assessment unless given here. */
    public record ImportRequest(@NotNull UUID questionPaperId, UUID sectionId, UUID examSubjectConfigId, UUID assessmentId, boolean negativeMarking,
                                @Size(max = 100) String source, @Size(max = 2_000_000) String csv, @Size(max = 5000) List<@Valid SheetInput> sheets) {
    }

    public record SheetView(UUID id, int rowNo, String studentKey, UUID studentId, String studentName, String setLabel, BigDecimal score,
                            int correct, int wrong, int blank, int invalid, String error) {
    }

    public record BatchView(UUID id, String batchCode, UUID questionPaperId, UUID examSubjectConfigId, UUID assessmentId, UUID sectionId,
                            String source, boolean negativeMarking, OmrBatchStatus status, int sheetsTotal, int sheetsMatched, int sheetsError,
                            BigDecimal maxScore, boolean postable, String notPostableReason, Instant postedAt, Instant createdAt,
                            List<SheetView> sheets) {
    }

    public record PostView(UUID batchId, ScorePosting.PostResult result) {
    }

    public BatchView evaluate(ImportRequest r) {
        QuestionPaper root = papers.findPaper(r.questionPaperId());
        if (root.getParentPaperId() != null) {
            root = papers.findPaper(root.getParentPaperId());
        }
        access.requireTeaches(root.getSubjectId(), root.getAcademicClassId(), "evaluate OMR sheets");
        if (root.getStatus() != PaperStatus.FINALISED) {
            throw new BusinessException("PAPER_NOT_FINAL", "Finalise the question paper first");
        }
        UUID config = r.examSubjectConfigId() != null || r.assessmentId() != null ? r.examSubjectConfigId() : root.getExamSubjectConfigId();
        UUID assessment = r.examSubjectConfigId() != null || r.assessmentId() != null ? r.assessmentId() : root.getAssessmentId();
        if (config != null && assessment != null) {
            throw new BusinessException("INVALID_TARGET", "Post to an exam paper or an assessment, not both");
        }
        UUID yearId;
        if (config != null || assessment != null) {
            ScorePosting.Target t = posting.target(config, assessment);
            if (!t.subjectId().equals(root.getSubjectId()) || !t.classId().equals(root.getAcademicClassId())) {
                throw new BusinessException("INVALID_TARGET", "The exam paper / assessment is of another subject or class");
            }
            yearId = t.yearId();
        } else {
            yearId = queries.rows("SELECT id FROM academic_year WHERE is_current AND deleted_at IS NULL", Map.of()).stream()
                    .map(x -> AssessmentQueries.uuid(x[0])).findFirst()
                    .orElseThrow(() -> new BusinessException("NO_CURRENT_YEAR", "No current academic year"));
        }
        List<Row> rows = rows(r);
        Map<String, Family> sets = family(root);
        List<Enrolled> enrolled = queries.enrolled(yearId, root.getAcademicClassId(), r.sectionId());

        OmrBatch batch = new OmrBatch();
        batch.setBatchCode(CodeGenerator.omrBatchCode());
        batch.setQuestionPaperId(root.getId());
        batch.setExamSubjectConfigId(config);
        batch.setAssessmentId(assessment);
        batch.setSectionId(r.sectionId());
        batch.setSource(r.source() == null || r.source().isBlank() ? (r.csv() != null ? "CSV" : "JSON") : r.source().trim());
        batch.setNegativeMarking(r.negativeMarking());
        batch.setCreatedBy(access.actorId());
        batch = batchRepository.save(batch);

        Set<UUID> seen = new HashSet<>();
        int matched = 0;
        int errors = 0;
        for (Row row : rows) {
            OmrSheet s = new OmrSheet();
            s.setBatchId(batch.getId());
            s.setRowNo(row.rowNo());
            if (row.keyType() == StudentKey.ROLL_NUMBER) {
                s.setRollNumber(row.key());
            } else {
                s.setStudentCode(row.key());
            }
            String label = row.setLabel() == null ? "A" : row.setLabel();
            s.setSetLabel(label);
            s.setRawAnswers(String.join("|", row.tokens()));
            String error = null;
            UUID student = null;
            try {
                student = match(row, enrolled);
                if (!seen.add(student)) {
                    throw new BusinessException("DUPLICATE", "Second sheet of the same student");
                }
            } catch (BusinessException e) {
                error = e.getMessage();
            }
            Family f = sets.get(label);
            if (error == null && f == null) {
                error = "Unknown set " + label + " (paper sets: " + String.join(", ", sets.keySet()) + ")";
            }
            if (error == null && row.tokens().size() > f.bubbles().size()) {
                error = row.tokens().size() + " answers for " + f.bubbles().size() + " bubbled questions";
            }
            s.setStudentId(student);
            if (error == null) {
                Scored sc = score(f, row.tokens(), r.negativeMarking());
                s.setScore(sc.total());
                s.setCorrect(sc.correct());
                s.setWrong(sc.wrong());
                s.setBlank(sc.blank());
                s.setInvalid(sc.invalid());
                matched++;
            } else {
                s.setError(error.length() > 500 ? error.substring(0, 500) : error);
                errors++;
            }
            sheetRepository.save(s);
        }
        batch.setSheetsTotal(rows.size());
        batch.setSheetsMatched(matched);
        batch.setSheetsError(errors);
        return view(batchRepository.save(batch), true);
    }

    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public List<BatchView> batches(UUID paperId) {
        QuestionPaper p = papers.findPaper(paperId);
        access.requireTeaches(p.getSubjectId(), p.getAcademicClassId(), "see OMR batches");
        return batchRepository.findByQuestionPaperIdOrderByCreatedAtDesc(p.getParentPaperId() != null ? p.getParentPaperId() : p.getId()).stream()
                .map(b -> view(b, false)).toList();
    }

    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public BatchView batch(UUID id) {
        OmrBatch b = find(id);
        QuestionPaper p = papers.findPaper(b.getQuestionPaperId());
        access.requireTeaches(p.getSubjectId(), p.getAcademicClassId(), "see OMR batches");
        return view(b, true);
    }

    /** Posts the matched sheets; enrolled students without a sheet are marked absent. Posting again overwrites. */
    public PostView post(UUID id) {
        OmrBatch b = find(id);
        QuestionPaper root = papers.findPaper(b.getQuestionPaperId());
        access.requireTeaches(root.getSubjectId(), root.getAcademicClassId(), "post OMR marks");
        Map<String, Family> sets = family(root);
        String reason = notPostable(b, sets);
        if (reason != null) {
            throw new BusinessException("OMR_NOT_POSTABLE", reason);
        }
        List<OmrSheet> sheets = sheetRepository.findByBatchIdOrderByRowNo(id).stream().filter(s -> s.getError() == null && s.getStudentId() != null).toList();
        Map<UUID, BigDecimal> totals = new LinkedHashMap<>();
        sheets.forEach(s -> totals.put(s.getStudentId(), s.getScore()));
        ScorePosting.PostResult result = posting.post(b.getExamSubjectConfigId(), b.getAssessmentId(), b.getSectionId(), root.getTotalMarks(), totals,
                "OMR " + b.getBatchCode());
        ScorePosting.Target target = posting.target(b.getExamSubjectConfigId(), b.getAssessmentId());
        LocalDate on = LocalDate.ofInstant(b.getCreatedAt() != null ? b.getCreatedAt() : clock.instant(), clock.getZone());
        List<QuestionMark> marks = new ArrayList<>();
        for (OmrSheet s : sheets) {
            Family f = sets.get(s.getSetLabel());
            Map<UUID, BigDecimal> byItem = itemMarks(f, OmrParser.tokens(s.getRawAnswers()), b.isNegativeMarking());
            for (Leaf l : f.bubbles()) {
                marks.add(new QuestionMark(s.getStudentId(), l.question().getId(), f.paper().getId(), byItem.get(l.item().getId()), l.item().getMarks(), on));
            }
        }
        posting.questionScores(target.termId(), ScoreSource.OMR, marks);
        b.setStatus(OmrBatchStatus.POSTED);
        b.setPostedAt(clock.instant());
        batchRepository.save(b);
        return new PostView(id, result);
    }

    // ───────────────────────────── scoring ─────────────────────────────

    record Family(QuestionPaper paper, List<Leaf> leaves, List<Leaf> bubbles) {
    }

    record Scored(BigDecimal total, int correct, int wrong, int blank, int invalid) {
    }

    private Map<String, Family> family(QuestionPaper root) {
        Map<String, Family> out = new LinkedHashMap<>();
        List<QuestionPaper> all = new ArrayList<>(List.of(root));
        all.addAll(paperRepository.findByParentPaperIdAndDeletedAtIsNullOrderBySetLabel(root.getId()));
        for (QuestionPaper p : all) {
            List<Leaf> leaves = papers.leaves(p.getId());
            out.put(p.getSetLabel().toUpperCase(Locale.ROOT), new Family(p, leaves, leaves.stream().filter(l -> l.omrNumber() != null).toList()));
        }
        return out;
    }

    static Map<UUID, BigDecimal> itemMarks(Family f, List<String> tokens, boolean negative) {
        Map<UUID, BigDecimal> out = new HashMap<>();
        for (Leaf l : f.bubbles()) {
            out.put(l.item().getId(), grade(l, tokens, negative).marks());
        }
        return out;
    }

    static Scored score(Family f, List<String> tokens, boolean negative) {
        Map<UUID, BigDecimal> byItem = new HashMap<>();
        int correct = 0;
        int wrong = 0;
        int blank = 0;
        int invalid = 0;
        for (Leaf l : f.bubbles()) {
            Graded g = grade(l, tokens, negative);
            byItem.put(l.item().getId(), g.marks());
            if (g.invalid()) {
                invalid++;
            } else if (g.blank()) {
                blank++;
            } else if (g.correct()) {
                correct++;
            } else {
                wrong++;
            }
        }
        return new Scored(PaperScoring.total(PaperScoring.of(f.bubbles(), byItem)), correct, wrong, blank, invalid);
    }

    record Graded(BigDecimal marks, boolean correct, boolean blank, boolean invalid) {
    }

    /** Several bubbles where one was allowed, or an unreadable mark, scores zero (no negative marks). */
    static Graded grade(Leaf l, List<String> tokens, boolean negative) {
        Question q = l.question();
        int n = l.omrNumber();
        String token = n <= tokens.size() ? tokens.get(n - 1) : null;
        Token t = OmrParser.toResponse(q.getQuestionType(), QuestionFormats.choiceKeys(q.getOptions()), token);
        if (t.invalid()) {
            return new Graded(BigDecimal.ZERO, false, false, true);
        }
        if (t.blank()) {
            return new Graded(BigDecimal.ZERO, false, true, false);
        }
        QuestionFormats.Grade g = QuestionFormats.grade(q.getQuestionType(), q.getAnswer(), t.response(), l.item().getMarks(),
                negative ? l.item().getNegativeMarks() : BigDecimal.ZERO);
        return new Graded(g.marks() == null ? BigDecimal.ZERO : g.marks(), Boolean.TRUE.equals(g.correct()), g.blank(), false);
    }

    // ───────────────────────────── helpers ─────────────────────────────

    private static List<Row> rows(ImportRequest r) {
        boolean csv = r.csv() != null && !r.csv().isBlank();
        boolean json = r.sheets() != null && !r.sheets().isEmpty();
        if (csv == json) {
            throw new BusinessException("OMR_INPUT", "Send either csv or sheets");
        }
        if (csv) {
            return OmrParser.parseCsv(r.csv());
        }
        List<Row> rows = new ArrayList<>();
        int i = 0;
        for (SheetInput s : r.sheets()) {
            i++;
            StudentKey type;
            String key;
            if (s.studentCode() != null && !s.studentCode().isBlank()) {
                type = StudentKey.STUDENT_CODE;
                key = s.studentCode();
            } else if (s.admissionNumber() != null && !s.admissionNumber().isBlank()) {
                type = StudentKey.ADMISSION_NUMBER;
                key = s.admissionNumber();
            } else {
                type = StudentKey.ROLL_NUMBER;
                key = s.rollNumber();
            }
            rows.add(new Row(i, type, key == null ? null : key.trim(),
                    s.setLabel() == null || s.setLabel().isBlank() ? null : s.setLabel().trim().toUpperCase(Locale.ROOT), OmrParser.tokens(s.answers()),
                    s.answers()));
        }
        return rows;
    }

    /** Student codes and admission numbers also match students outside the section (they are unique); roll numbers only within it. */
    static UUID match(Row row, List<Enrolled> enrolled) {
        if (row.key() == null || row.key().isBlank()) {
            throw new BusinessException("NO_STUDENT", "No student identifier");
        }
        String key = row.key().trim();
        List<Enrolled> hits = enrolled.stream().filter(e -> switch (row.keyType()) {
            case STUDENT_CODE -> key.equalsIgnoreCase(e.code()) || key.equalsIgnoreCase(e.admissionNumber());
            case ADMISSION_NUMBER -> key.equalsIgnoreCase(e.admissionNumber());
            case ROLL_NUMBER -> key.equalsIgnoreCase(e.rollNumber());
        }).toList();
        if (hits.isEmpty()) {
            throw new BusinessException("NO_STUDENT", "No enrolled student with " + row.keyType().name().toLowerCase(Locale.ROOT).replace('_', ' ') + " " + key);
        }
        if (hits.size() > 1) {
            throw new BusinessException("AMBIGUOUS", "Several students have roll number " + key + "; import one section at a time");
        }
        return hits.get(0).studentId();
    }

    private static String notPostable(OmrBatch b, Map<String, Family> sets) {
        if (b.getExamSubjectConfigId() == null && b.getAssessmentId() == null) {
            return "The batch is not linked to an exam paper or assessment";
        }
        for (Family f : sets.values()) {
            if (f.bubbles().size() != f.leaves().size()) {
                return "Set " + f.paper().getSetLabel() + " has written questions; OMR covers only part of the paper";
            }
        }
        return null;
    }

    private OmrBatch find(UUID id) {
        return batchRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("OmrBatch", id));
    }

    private BatchView view(OmrBatch b, boolean withSheets) {
        QuestionPaper root = papers.findPaper(b.getQuestionPaperId());
        String reason = notPostable(b, family(root));
        List<SheetView> sheets = null;
        if (withSheets) {
            List<OmrSheet> list = sheetRepository.findByBatchIdOrderByRowNo(b.getId());
            Map<UUID, String> names = queries.studentNames(list.stream().map(OmrSheet::getStudentId).filter(java.util.Objects::nonNull).distinct().toList());
            sheets = list.stream().map(s -> new SheetView(s.getId(), s.getRowNo(), s.getStudentCode() != null ? s.getStudentCode() : s.getRollNumber(),
                    s.getStudentId(), s.getStudentId() == null ? null : names.get(s.getStudentId()), s.getSetLabel(), s.getScore(), s.getCorrect(),
                    s.getWrong(), s.getBlank(), s.getInvalid(), s.getError())).toList();
        }
        return new BatchView(b.getId(), b.getBatchCode(), b.getQuestionPaperId(), b.getExamSubjectConfigId(), b.getAssessmentId(), b.getSectionId(),
                b.getSource(), b.isNegativeMarking(), b.getStatus(), b.getSheetsTotal(), b.getSheetsMatched(), b.getSheetsError(), root.getTotalMarks(),
                reason == null, reason, b.getPostedAt(), b.getCreatedAt(), sheets);
    }
}
