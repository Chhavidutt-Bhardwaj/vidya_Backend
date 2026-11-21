package com.vidya.tenantdata.assessment.analytics;

import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.tenantdata.assessment.AssessmentAccess;
import com.vidya.tenantdata.assessment.repository.AssessmentQueries;
import com.vidya.tenantdata.assessment.repository.AssessmentQueries.Enrolled;
import com.vidya.tenantdata.assessment.repository.AssessmentQueries.TermInfo;
import com.vidya.tenantdata.assessment.analytics.AnalyticsDtos.ClassPerformance;
import com.vidya.tenantdata.assessment.analytics.AnalyticsDtos.Distribution;
import com.vidya.tenantdata.assessment.analytics.AnalyticsDtos.ItemAnalysis;
import com.vidya.tenantdata.assessment.analytics.AnalyticsDtos.ItemStat;
import com.vidya.tenantdata.assessment.analytics.AnalyticsDtos.OutcomeAttainment;
import com.vidya.tenantdata.assessment.analytics.AnalyticsDtos.RatingPoint;
import com.vidya.tenantdata.assessment.analytics.AnalyticsDtos.SectionAverage;
import com.vidya.tenantdata.assessment.analytics.AnalyticsDtos.StudentProgress;
import com.vidya.tenantdata.assessment.analytics.AnalyticsDtos.SubjectPerformance;
import com.vidya.tenantdata.assessment.analytics.AnalyticsDtos.SubjectTrend;
import com.vidya.tenantdata.assessment.analytics.AnalyticsDtos.TermPoint;
import com.vidya.tenantdata.assessment.analytics.AnalyticsDtos.YearComparison;
import com.vidya.tenantdata.assessment.analytics.AnalyticsDtos.YearPoint;
import com.vidya.tenantdata.assessment.entity.QuestionPaper;
import com.vidya.tenantdata.assessment.repository.AssessmentSchemeRepository;
import com.vidya.tenantdata.assessment.repository.QuestionPaperRepository;
import com.vidya.tenantdata.assessment.scheme.SchemeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.vidya.tenantdata.assessment.repository.AssessmentQueries.date;
import static com.vidya.tenantdata.assessment.repository.AssessmentQueries.params;
import static com.vidya.tenantdata.assessment.repository.AssessmentQueries.uuid;

/**
 * Performance analytics from generated report cards (subject-wise, year-over-year, student progress) and from
 * question-level scores (learning-outcome attainment, item analysis of question papers).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
public class AssessmentAnalyticsService {

    static final List<String> DEFAULT_LEVELS = List.of("BEGINNING", "DEVELOPING", "PROFICIENT", "ADVANCED");
    private static final String CARD_OK = "rc.deleted_at IS NULL AND rc.status IN ('GENERATED', 'PUBLISHED')";

    private final AssessmentQueries queries;
    private final AssessmentAccess access;
    private final AssessmentSchemeRepository schemeRepository;
    private final QuestionPaperRepository paperRepository;

    // ───────────────────────────── subject-wise performance ─────────────────────────────

    public ClassPerformance classPerformance(UUID termId, UUID classId, UUID sectionId) {
        access.requireClass(classId, sectionId, "see class analytics");
        Map<String, Object> p = params("t", termId, "c", classId, "sec", sectionId);
        String where = "rc.exam_term_id = :t AND rc.academic_class_id = :c AND " + CARD_OK + " AND (CAST(:sec AS uuid) IS NULL OR rc.section_id = :sec)";
        List<Object[]> cards = queries.rows("SELECT rc.student_id, rc.percentage, rc.result_status, rc.overall_grade FROM student_report_card rc WHERE "
                + where, p);
        List<BigDecimal> overall = new ArrayList<>();
        Map<String, Integer> results = new TreeMap<>();
        Map<String, Integer> grades = new TreeMap<>();
        for (Object[] r : cards) {
            results.merge((String) r[2], 1, Integer::sum);
            if (!"WITHHELD".equals(r[2])) {
                overall.add((BigDecimal) r[1]);
            }
            if (r[3] != null) {
                grades.merge((String) r[3], 1, Integer::sum);
            }
        }
        List<Object[]> rows = queries.rows("SELECT sr.subject_id, sr.subject_name, sr.category, sr.percentage, sr.grade, sr.is_passing, sr.is_absent, "
                + "sr.is_exempt, rc.student_id, rc.section_id, sec.name FROM report_card_subject_result sr "
                + "JOIN student_report_card rc ON rc.id = sr.report_card_id LEFT JOIN section sec ON sec.id = rc.section_id WHERE " + where, p);
        Map<UUID, List<Object[]>> bySubject = rows.stream().collect(Collectors.groupingBy(r -> uuid(r[0]), LinkedHashMap::new, Collectors.toList()));
        Map<UUID, String> names = queries.studentNames(cards.stream().map(r -> uuid(r[0])).toList());
        List<SubjectPerformance> subjects = new ArrayList<>();
        bySubject.forEach((subjectId, list) -> {
            List<Object[]> taken = list.stream().filter(r -> !Boolean.TRUE.equals(r[7])).toList();
            List<BigDecimal> pcts = taken.stream().map(r -> (BigDecimal) r[3]).toList();
            int passed = (int) taken.stream().filter(r -> Boolean.TRUE.equals(r[5])).count();
            int absent = (int) taken.stream().filter(r -> Boolean.TRUE.equals(r[6])).count();
            Map<String, Integer> g = new TreeMap<>();
            taken.forEach(r -> {
                if (r[4] != null) {
                    g.merge((String) r[4], 1, Integer::sum);
                }
            });
            String top = taken.stream().max(Comparator.comparing(r -> (BigDecimal) r[3])).map(r -> names.get(uuid(r[8]))).orElse(null);
            Map<UUID, List<Object[]>> bySection = taken.stream().filter(r -> r[9] != null)
                    .collect(Collectors.groupingBy(r -> uuid(r[9]), LinkedHashMap::new, Collectors.toList()));
            List<SectionAverage> sections = bySection.entrySet().stream().map(e -> new SectionAverage(e.getKey(), (String) e.getValue().get(0)[10],
                    e.getValue().size(), Stats.mean(e.getValue().stream().map(r -> (BigDecimal) r[3]).toList()),
                    rate((int) e.getValue().stream().filter(r -> Boolean.TRUE.equals(r[5])).count(), e.getValue().size())))
                    .sorted(Comparator.comparing(SectionAverage::sectionName, Comparator.nullsLast(String::compareTo))).toList();
            subjects.add(new SubjectPerformance(subjectId, (String) list.get(0)[1], (String) list.get(0)[2], taken.size(), absent, passed,
                    rate(passed, taken.size()), distribution(pcts), g, top, sections));
        });
        subjects.sort(Comparator.comparing(SubjectPerformance::subjectName));
        return new ClassPerformance(termId, classId, sectionId, cards.size(), distribution(overall), results, grades, subjects);
    }

    // ───────────────────────────── year over year ─────────────────────────────

    /**
     * The class's result in each academic year: the term with the given name, or else each year's latest term with
     * report cards. Classes are shared across years, so "Class 10" of 2024-25 and 2025-26 compare directly.
     */
    public YearComparison yearComparison(UUID classId, UUID subjectId, String termName) {
        access.requireClass(classId, null, "compare years");
        List<Object[]> cards = queries.rows("SELECT rc.academic_year_id, y.name, y.start_date, rc.exam_term_id, t.name, t.end_date, rc.percentage, "
                + "rc.result_status, rc.id FROM student_report_card rc JOIN academic_year y ON y.id = rc.academic_year_id "
                + "JOIN exam_term t ON t.id = rc.exam_term_id WHERE rc.academic_class_id = :c AND " + CARD_OK, Map.of("c", classId));
        Map<UUID, List<Object[]>> byYear = cards.stream().collect(Collectors.groupingBy(r -> uuid(r[0])));
        List<YearPoint> years = new ArrayList<>();
        Map<UUID, LocalDate> yearStart = new HashMap<>();
        for (Map.Entry<UUID, List<Object[]>> e : byYear.entrySet()) {
            List<Object[]> rows = e.getValue();
            Object[] chosenTerm = rows.stream().filter(r -> termName == null || termName.equalsIgnoreCase((String) r[4]))
                    .max(Comparator.comparing(r -> date(r[5]))).orElse(null);
            if (chosenTerm == null) {
                continue;
            }
            UUID termId = uuid(chosenTerm[3]);
            List<Object[]> termCards = rows.stream().filter(r -> uuid(r[3]).equals(termId)).toList();
            List<Object[]> ranked = termCards.stream().filter(r -> !"WITHHELD".equals(r[7])).toList();
            int passed = (int) ranked.stream().filter(r -> "PASSED".equals(r[7])).count();
            Map<String, BigDecimal> subjectAverages = new TreeMap<>();
            queries.rows("SELECT sr.subject_name, avg(sr.percentage) FROM report_card_subject_result sr WHERE sr.report_card_id IN (:ids) "
                            + "AND NOT sr.is_exempt AND (CAST(:s AS uuid) IS NULL OR sr.subject_id = :s) GROUP BY sr.subject_name",
                    params("ids", termCards.stream().map(r -> uuid(r[8])).toList(), "s", subjectId))
                    .forEach(r -> subjectAverages.put((String) r[0], ((BigDecimal) r[1]).setScale(2, RoundingMode.HALF_UP)));
            BigDecimal avg = subjectId != null ? subjectAverages.values().stream().findFirst().orElse(null)
                    : Stats.mean(ranked.stream().map(r -> (BigDecimal) r[6]).toList());
            yearStart.put(e.getKey(), date(chosenTerm[2]));
            years.add(new YearPoint(e.getKey(), (String) chosenTerm[1], termId, (String) chosenTerm[4], termCards.size(), avg,
                    rate(passed, ranked.size()), subjectAverages));
        }
        years.sort(Comparator.comparing(y -> yearStart.get(y.academicYearId())));
        BigDecimal change = years.size() < 2 || years.get(0).average() == null || years.get(years.size() - 1).average() == null ? null
                : years.get(years.size() - 1).average().subtract(years.get(0).average());
        String className = queries.rows("SELECT name FROM academic_class WHERE id = :c", Map.of("c", classId)).stream()
                .map(r -> (String) r[0]).findFirst().orElse(null);
        return new YearComparison(classId, className, subjectId, years, change);
    }

    // ───────────────────────────── student progress ─────────────────────────────

    public StudentProgress studentProgress(UUID studentId) {
        access.requireStudent(studentId, "see progress");
        boolean familyOnly = !access.all() && access.own().staffIds().isEmpty();
        List<Object[]> cards = queries.rows("SELECT rc.id, rc.exam_term_id, t.name, y.name, c.name, rc.percentage, rc.overall_grade, rc.class_rank, "
                + "rc.section_rank, rc.result_status, rc.attendance_percentage FROM student_report_card rc JOIN exam_term t ON t.id = rc.exam_term_id "
                + "JOIN academic_year y ON y.id = rc.academic_year_id JOIN academic_class c ON c.id = rc.academic_class_id "
                + "WHERE rc.student_id = :s AND " + CARD_OK + (familyOnly ? " AND rc.status = 'PUBLISHED'" : "")
                + " ORDER BY y.start_date, t.start_date", Map.of("s", studentId));
        List<TermPoint> terms = cards.stream().map(r -> new TermPoint(uuid(r[1]), (String) r[2], (String) r[3], (String) r[4], (BigDecimal) r[5],
                (String) r[6], (Integer) r[7], (Integer) r[8], (String) r[9], (BigDecimal) r[10])).toList();
        List<SubjectTrend> subjects = new ArrayList<>();
        if (!cards.isEmpty()) {
            List<UUID> cardIds = cards.stream().map(r -> uuid(r[0])).toList();
            Map<UUID, Integer> order = new HashMap<>();
            for (int i = 0; i < cardIds.size(); i++) {
                order.put(cardIds.get(i), i);
            }
            List<Object[]> rows = queries.rows("SELECT sr.subject_id, sr.subject_name, sr.report_card_id, sr.percentage FROM report_card_subject_result sr "
                    + "WHERE sr.report_card_id IN (:ids) AND NOT sr.is_exempt", Map.of("ids", cardIds));
            rows.stream().collect(Collectors.groupingBy(r -> uuid(r[0]), LinkedHashMap::new, Collectors.toList())).forEach((sid, list) -> {
                List<BigDecimal> series = list.stream().sorted(Comparator.comparing(r -> order.get(uuid(r[2])))).map(r -> (BigDecimal) r[3]).toList();
                subjects.add(new SubjectTrend(sid, (String) list.get(0)[1], series, Stats.slope(series), Stats.trend(series), series.get(series.size() - 1)));
            });
            subjects.sort(Comparator.comparing(SubjectTrend::subjectName));
        }
        List<String> strengths = subjects.stream().filter(s -> s.latest().compareTo(BigDecimal.valueOf(60)) >= 0)
                .sorted(Comparator.comparing(SubjectTrend::latest).reversed()).limit(3).map(s -> s.subjectName() + " (" + plain(s.latest()) + "%)").toList();
        List<String> attention = subjects.stream().filter(s -> s.latest().compareTo(BigDecimal.valueOf(40)) < 0 || s.trend() == Stats.Trend.DECLINING)
                .map(s -> s.subjectName() + " (" + plain(s.latest()) + "%" + (s.trend() == Stats.Trend.DECLINING ? ", declining" : "") + ")").toList();
        List<RatingPoint> ratings = queries.rows("SELECT t.name, COALESCE(c.name, o.code || ': ' || o.statement), r.level FROM competency_rating r "
                + "JOIN exam_term t ON t.id = r.exam_term_id LEFT JOIN competency c ON c.id = r.competency_id "
                + "LEFT JOIN learning_outcome o ON o.id = r.learning_outcome_id WHERE r.student_id = :s ORDER BY t.start_date, 2", Map.of("s", studentId))
                .stream().map(r -> new RatingPoint((String) r[0], (String) r[1], (String) r[2])).toList();
        String name = queries.studentNames(List.of(studentId)).get(studentId);
        return new StudentProgress(studentId, name, terms, Stats.trend(terms.stream().map(TermPoint::percentage).filter(Objects::nonNull).toList()),
                subjects, strengths, attention, ratings, attainment(List.of(studentId), null, levelsFor(studentId)));
    }

    // ───────────────────────────── learning outcomes ─────────────────────────────

    public List<OutcomeAttainment> classOutcomes(UUID termId, UUID classId, UUID sectionId) {
        access.requireClass(classId, sectionId, "see outcome attainment");
        TermInfo term = queries.term(termId).orElseThrow(() -> new ResourceNotFoundException("ExamTerm", termId));
        List<UUID> students = queries.enrolled(term.yearId(), classId, sectionId).stream().map(Enrolled::studentId).toList();
        List<String> levels = schemeRepository.findActiveFor(term.yearId(), classId).map(s -> SchemeService.list(s.getCompetencyLevels()))
                .filter(l -> !l.isEmpty()).orElse(DEFAULT_LEVELS);
        return attainment(students, termId, levels);
    }

    public List<OutcomeAttainment> studentOutcomes(UUID studentId, UUID termId) {
        access.requireStudent(studentId, "see outcome attainment");
        return attainment(List.of(studentId), termId, levelsFor(studentId));
    }

    /**
     * Attainment of each learning outcome from question-level marks (online exams, OMR, question-wise entry): a
     * student's attainment is marks gained / marks available on questions tagged with the outcome; the outcome's is the
     * mean over students. The level splits 0–100 evenly across the scheme's competency levels.
     */
    List<OutcomeAttainment> attainment(List<UUID> students, UUID termId, List<String> levels) {
        if (students.isEmpty()) {
            return List.of();
        }
        List<Object[]> rows = queries.rows("SELECT qo.learning_outcome_id, lo.code, lo.statement, lo.subject_id, sub.name, qs.student_id, "
                + "SUM(qs.marks_awarded), SUM(qs.max_marks), COUNT(DISTINCT qs.question_id) FROM question_score qs "
                + "JOIN question_outcome qo ON qo.question_id = qs.question_id JOIN learning_outcome lo ON lo.id = qo.learning_outcome_id "
                + "JOIN subject sub ON sub.id = lo.subject_id WHERE qs.student_id IN (:s) AND (CAST(:t AS uuid) IS NULL OR qs.exam_term_id = :t) "
                + "GROUP BY qo.learning_outcome_id, lo.code, lo.statement, lo.subject_id, sub.name, qs.student_id", params("s", students, "t", termId));
        List<OutcomeAttainment> out = new ArrayList<>();
        rows.stream().collect(Collectors.groupingBy(r -> uuid(r[0]), LinkedHashMap::new, Collectors.toList())).forEach((id, list) -> {
            List<BigDecimal> pcts = list.stream().filter(r -> ((BigDecimal) r[7]).signum() > 0)
                    .map(r -> ((BigDecimal) r[6]).multiply(BigDecimal.valueOf(100)).divide((BigDecimal) r[7], 2, RoundingMode.HALF_UP)).toList();
            BigDecimal mean = Stats.mean(pcts);
            int questions = list.stream().mapToInt(r -> ((Number) r[8]).intValue()).max().orElse(0);
            int below = (int) pcts.stream().filter(p -> p.compareTo(BigDecimal.valueOf(50)) < 0).count();
            Object[] f = list.get(0);
            out.add(new OutcomeAttainment(id, (String) f[1], (String) f[2], uuid(f[3]), (String) f[4], questions, pcts.size(), mean,
                    Stats.level(mean, levels), below));
        });
        out.sort(Comparator.comparing(OutcomeAttainment::attainment, Comparator.nullsLast(Comparator.naturalOrder())));
        return out;
    }

    private List<String> levelsFor(UUID studentId) {
        return queries.currentEnrolment(studentId).flatMap(e -> queries.rows("SELECT id FROM academic_year WHERE is_current", Map.of()).stream()
                        .findFirst().flatMap(y -> schemeRepository.findActiveFor(uuid(y[0]), e.classId())))
                .map(s -> SchemeService.list(s.getCompetencyLevels())).filter(l -> !l.isEmpty()).orElse(DEFAULT_LEVELS);
    }

    // ───────────────────────────── item analysis ─────────────────────────────

    /**
     * Facility (share of marks gained) and discrimination (top 27% minus bottom 27% of students by paper total) for each
     * question of a paper and its other sets; the question bank keeps the latest values to guide future papers.
     */
    @Transactional(transactionManager = "tenantTransactionManager")
    public ItemAnalysis itemAnalysis(UUID paperId) {
        QuestionPaper paper = paperRepository.findByIdAndDeletedAtIsNull(paperId).orElseThrow(() -> new ResourceNotFoundException("QuestionPaper", paperId));
        access.requireTeaches(paper.getSubjectId(), paper.getAcademicClassId(), "analyse papers");
        List<UUID> paperIds = new ArrayList<>(List.of(paperId));
        paperRepository.findByParentPaperIdAndDeletedAtIsNullOrderBySetLabel(paperId).forEach(p -> paperIds.add(p.getId()));
        List<Object[]> scores = queries.rows("SELECT student_id, question_id, marks_awarded, max_marks FROM question_score WHERE question_paper_id IN (:p)",
                Map.of("p", paperIds));
        Map<UUID, BigDecimal> totals = new HashMap<>();
        Map<UUID, BigDecimal> maxTotals = new HashMap<>();
        scores.forEach(r -> {
            totals.merge(uuid(r[0]), (BigDecimal) r[2], BigDecimal::add);
            maxTotals.merge(uuid(r[0]), (BigDecimal) r[3], BigDecimal::add);
        });
        List<UUID> rankedStudents = totals.keySet().stream().sorted(Comparator.comparing(totals::get)).toList();
        int n = rankedStudents.size();
        int group = Math.max(1, (int) Math.round(n * 0.27));
        Set<UUID> lower = Set.copyOf(rankedStudents.subList(0, Math.min(group, n)));
        Set<UUID> upper = Set.copyOf(rankedStudents.subList(Math.max(0, n - group), n));
        Map<UUID, List<Object[]>> byQuestion = scores.stream().collect(Collectors.groupingBy(r -> uuid(r[1])));
        List<Object[]> items = queries.rows("SELECT i.position, q.id, q.question_code, q.question_type, q.difficulty, i.marks FROM question_paper_item i "
                + "JOIN question q ON q.id = i.question_id WHERE i.paper_id = :p ORDER BY i.position", Map.of("p", paperId));
        List<ItemStat> stats = new ArrayList<>();
        for (Object[] it : items) {
            UUID qid = uuid(it[1]);
            List<Object[]> qs = byQuestion.getOrDefault(qid, List.of());
            BigDecimal facility = fraction(qs);
            BigDecimal discrimination = n < 4 || qs.isEmpty() ? null : nullSafeSub(fraction(qs.stream().filter(r -> upper.contains(uuid(r[0]))).toList()),
                    fraction(qs.stream().filter(r -> lower.contains(uuid(r[0]))).toList()));
            stats.add(new ItemStat((Integer) it[0], qid, (String) it[2], (String) it[3], (String) it[4], (BigDecimal) it[5], qs.size(), facility,
                    discrimination, flag(facility, discrimination)));
            if (!qs.isEmpty()) {
                queries.update("UPDATE question SET facility_index = (SELECT CASE WHEN SUM(max_marks) > 0 THEN round(SUM(marks_awarded) / SUM(max_marks), 4) END "
                        + "FROM question_score WHERE question_id = :q), discrimination_index = :d, responses_analysed = "
                        + "(SELECT count(*) FROM question_score WHERE question_id = :q) WHERE id = :q", params("q", qid, "d", discrimination));
            }
        }
        BigDecimal avg = Stats.mean(totals.keySet().stream().filter(s -> maxTotals.get(s).signum() > 0)
                .map(s -> totals.get(s).multiply(BigDecimal.valueOf(100)).divide(maxTotals.get(s), 2, RoundingMode.HALF_UP)).toList());
        return new ItemAnalysis(paperId, n, avg, stats);
    }

    private static BigDecimal fraction(List<Object[]> rows) {
        BigDecimal got = rows.stream().map(r -> (BigDecimal) r[2]).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal max = rows.stream().map(r -> (BigDecimal) r[3]).reduce(BigDecimal.ZERO, BigDecimal::add);
        return max.signum() == 0 ? null : got.divide(max, 4, RoundingMode.HALF_UP);
    }

    private static BigDecimal nullSafeSub(BigDecimal a, BigDecimal b) {
        return a == null || b == null ? null : a.subtract(b).setScale(4, RoundingMode.HALF_UP);
    }

    static String flag(BigDecimal facility, BigDecimal discrimination) {
        if (discrimination != null && discrimination.signum() < 0) {
            return "Negative discrimination: weaker students do better; check the answer key";
        }
        if (facility != null && facility.compareTo(new BigDecimal("0.20")) < 0) {
            return "Very hard";
        }
        if (facility != null && facility.compareTo(new BigDecimal("0.90")) > 0) {
            return "Very easy";
        }
        if (discrimination != null && discrimination.compareTo(new BigDecimal("0.20")) < 0) {
            return "Poor discrimination: review the question";
        }
        return null;
    }

    // ───────────────────────────── helpers ─────────────────────────────

    private static Distribution distribution(List<BigDecimal> v) {
        if (v.isEmpty()) {
            return new Distribution(null, null, null, null, null);
        }
        return new Distribution(Stats.mean(v), Stats.median(v), v.stream().max(BigDecimal::compareTo).orElse(null),
                v.stream().min(BigDecimal::compareTo).orElse(null), Stats.stdDev(v));
    }

    private static BigDecimal rate(int part, int whole) {
        return whole == 0 ? null : BigDecimal.valueOf(part * 100L).divide(BigDecimal.valueOf(whole), 2, RoundingMode.HALF_UP);
    }

    private static String plain(BigDecimal d) {
        return d.stripTrailingZeros().toPlainString();
    }
}
