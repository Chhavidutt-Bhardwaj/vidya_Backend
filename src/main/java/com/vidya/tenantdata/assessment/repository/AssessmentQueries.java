package com.vidya.tenantdata.assessment.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Read queries shared by the assessment services (native SQL on the tenant database). */
@Repository
public class AssessmentQueries {

    static final String STUDENT_NAME = "COALESCE(NULLIF(trim(concat_ws(' ', p.first_name, p.last_name)), ''), s.student_code)";
    static final String ACTIVE_ENROLMENT = "e.deleted_at IS NULL AND e.enrollment_status IN ('ADMITTED', 'ACTIVE')";

    @PersistenceContext(unitName = "tenant")
    private EntityManager em;

    public record TermInfo(UUID id, UUID yearId, String name, LocalDate start, LocalDate end, BigDecimal weight) {
    }

    public record Enrolled(UUID studentId, UUID classId, UUID sectionId, String name, String code, String admissionNumber, String rollNumber) {
    }

    public record ExamPaper(UUID configId, UUID scheduleId, UUID subjectId, UUID gradingScaleId, BigDecimal maxMarks, BigDecimal passMarks,
                            LocalDate examDate) {
    }

    public record ConfigInfo(UUID configId, UUID termId, UUID yearId, UUID classId, UUID subjectId, BigDecimal maxMarks, BigDecimal passMarks,
                             LocalDate examDate, String scheduleStatus) {
    }

    public record EffectiveMark(UUID configId, UUID studentId, BigDecimal raw, BigDecimal adjustment, BigDecimal marks, boolean absent,
                                boolean exempt, String status) {
    }

    public record Score(UUID assessmentId, UUID studentId, BigDecimal raw, BigDecimal marks, boolean absent, boolean exempt) {
    }

    public record SubjectInfo(UUID id, String name, String code) {
    }

    public record Attendance(int working, int present) {
    }

    // ── terms ──

    public Optional<TermInfo> term(UUID termId) {
        return rows("SELECT id, academic_year_id, name, start_date, end_date, weightage_percentage FROM exam_term "
                + "WHERE id = :id AND deleted_at IS NULL", Map.of("id", termId)).stream().map(AssessmentQueries::term).findFirst();
    }

    /** Terms of the year, oldest first. */
    public List<TermInfo> termsOfYear(UUID yearId) {
        return rows("SELECT id, academic_year_id, name, start_date, end_date, weightage_percentage FROM exam_term "
                + "WHERE academic_year_id = :y AND deleted_at IS NULL AND status <> 'CANCELLED' ORDER BY start_date, name", Map.of("y", yearId))
                .stream().map(AssessmentQueries::term).toList();
    }

    private static TermInfo term(Object[] r) {
        return new TermInfo(uuid(r[0]), uuid(r[1]), (String) r[2], date(r[3]), date(r[4]), (BigDecimal) r[5]);
    }

    // ── students ──

    public List<Enrolled> enrolled(UUID yearId, UUID classId, UUID sectionId) {
        return rows("SELECT e.student_id, e.academic_class_id, e.section_id, " + STUDENT_NAME + ", s.student_code, s.admission_number, e.roll_number "
                + "FROM student_academic_enrollment e JOIN student s ON s.id = e.student_id AND s.deleted_at IS NULL "
                + "JOIN tenant_user u ON u.id = s.tenant_user_id LEFT JOIN tenant_user_profile p ON p.user_id = u.id "
                + "WHERE " + ACTIVE_ENROLMENT + " AND e.academic_year_id = :y AND e.academic_class_id = :c "
                + "AND (CAST(:sec AS uuid) IS NULL OR e.section_id = :sec) ORDER BY e.section_id, e.roll_number, 4",
                params("y", yearId, "c", classId, "sec", sectionId)).stream().map(AssessmentQueries::enrolled).toList();
    }

    /** Current enrolment of each student in the year. */
    public Map<UUID, Enrolled> enrolments(UUID yearId, Collection<UUID> studentIds) {
        Map<UUID, Enrolled> out = new HashMap<>();
        if (studentIds.isEmpty()) {
            return out;
        }
        rows("SELECT e.student_id, e.academic_class_id, e.section_id, " + STUDENT_NAME + ", s.student_code, s.admission_number, e.roll_number "
                + "FROM student_academic_enrollment e JOIN student s ON s.id = e.student_id "
                + "JOIN tenant_user u ON u.id = s.tenant_user_id LEFT JOIN tenant_user_profile p ON p.user_id = u.id "
                + "WHERE " + ACTIVE_ENROLMENT + " AND e.academic_year_id = :y AND e.student_id IN (:ids)",
                params("y", yearId, "ids", studentIds)).forEach(r -> out.put(uuid(r[0]), enrolled(r)));
        return out;
    }

    /** Current enrolment in the current academic year (for students taking online exams). */
    public Optional<Enrolled> currentEnrolment(UUID studentId) {
        return rows("SELECT e.student_id, e.academic_class_id, e.section_id, " + STUDENT_NAME + ", s.student_code, s.admission_number, e.roll_number "
                + "FROM student_academic_enrollment e JOIN academic_year y ON y.id = e.academic_year_id AND y.is_current "
                + "JOIN student s ON s.id = e.student_id JOIN tenant_user u ON u.id = s.tenant_user_id "
                + "LEFT JOIN tenant_user_profile p ON p.user_id = u.id WHERE " + ACTIVE_ENROLMENT + " AND e.student_id = :s",
                Map.of("s", studentId)).stream().map(AssessmentQueries::enrolled).findFirst();
    }

    private static Enrolled enrolled(Object[] r) {
        return new Enrolled(uuid(r[0]), uuid(r[1]), uuid(r[2]), (String) r[3], (String) r[4], (String) r[5], (String) r[6]);
    }

    public Map<UUID, String> studentNames(Collection<UUID> ids) {
        Map<UUID, String> out = new HashMap<>();
        if (ids.isEmpty()) {
            return out;
        }
        rows("SELECT s.id, " + STUDENT_NAME + " FROM student s JOIN tenant_user u ON u.id = s.tenant_user_id "
                + "LEFT JOIN tenant_user_profile p ON p.user_id = u.id WHERE s.id IN (:ids)", Map.of("ids", ids))
                .forEach(r -> out.put(uuid(r[0]), (String) r[1]));
        return out;
    }

    // ── exam papers and marks ──

    /** Exam papers (subject configs) of the term's schedules for the class. */
    public List<ExamPaper> examPapers(UUID termId, UUID classId) {
        return rows("SELECT c.id, sc.id, c.subject_id, sc.grading_scale_id, c.max_marks, c.pass_marks, c.exam_date "
                + "FROM exam_subject_config c JOIN exam_schedule sc ON sc.id = c.exam_schedule_id AND sc.deleted_at IS NULL "
                + "WHERE sc.exam_term_id = :t AND sc.academic_class_id = :c AND sc.status <> 'CANCELLED' AND c.deleted_at IS NULL "
                + "ORDER BY c.exam_date", params("t", termId, "c", classId)).stream()
                .map(r -> new ExamPaper(uuid(r[0]), uuid(r[1]), uuid(r[2]), uuid(r[3]), (BigDecimal) r[4], (BigDecimal) r[5], date(r[6]))).toList();
    }

    public Optional<ConfigInfo> config(UUID configId) {
        return rows("SELECT c.id, sc.exam_term_id, t.academic_year_id, sc.academic_class_id, c.subject_id, c.max_marks, c.pass_marks, "
                + "c.exam_date, sc.status FROM exam_subject_config c JOIN exam_schedule sc ON sc.id = c.exam_schedule_id "
                + "JOIN exam_term t ON t.id = sc.exam_term_id WHERE c.id = :id AND c.deleted_at IS NULL", Map.of("id", configId))
                .stream().map(r -> new ConfigInfo(uuid(r[0]), uuid(r[1]), uuid(r[2]), uuid(r[3]), uuid(r[4]), (BigDecimal) r[5],
                        (BigDecimal) r[6], date(r[7]), (String) r[8])).findFirst();
    }

    /** Marks after approved grace / moderation / re-evaluation, kept within 0..max. */
    public List<EffectiveMark> effectiveMarks(Collection<UUID> configIds) {
        if (configIds.isEmpty()) {
            return List.of();
        }
        return rows("SELECT exam_subject_config_id, student_id, raw_marks, adjustment, marks, is_absent, is_exempt, status "
                + "FROM effective_exam_mark WHERE exam_subject_config_id IN (:ids)", Map.of("ids", configIds)).stream()
                .map(r -> new EffectiveMark(uuid(r[0]), uuid(r[1]), (BigDecimal) r[2], (BigDecimal) r[3], (BigDecimal) r[4],
                        Boolean.TRUE.equals(r[5]), Boolean.TRUE.equals(r[6]), (String) r[7])).toList();
    }

    public Optional<EffectiveMark> effectiveMark(UUID configId, UUID studentId) {
        return effectiveMarks(List.of(configId)).stream().filter(m -> m.studentId().equals(studentId)).findFirst();
    }

    /** Assessment scores with approved adjustments, kept within 0..max. */
    public List<Score> scores(Collection<UUID> assessmentIds) {
        if (assessmentIds.isEmpty()) {
            return List.of();
        }
        return rows("SELECT sc.assessment_id, sc.student_id, sc.marks, "
                + "CASE WHEN sc.marks IS NULL THEN NULL ELSE LEAST(a.max_marks, GREATEST(0, sc.marks + COALESCE(adj.delta, 0))) END, "
                + "sc.is_absent, sc.is_exempt FROM assessment_score sc JOIN assessment a ON a.id = sc.assessment_id "
                + "LEFT JOIN (SELECT assessment_id, student_id, SUM(delta) AS delta FROM mark_adjustment WHERE status = 'APPROVED' "
                + "AND assessment_id IS NOT NULL GROUP BY assessment_id, student_id) adj "
                + "ON adj.assessment_id = sc.assessment_id AND adj.student_id = sc.student_id WHERE sc.assessment_id IN (:ids)",
                Map.of("ids", assessmentIds)).stream()
                .map(r -> new Score(uuid(r[0]), uuid(r[1]), (BigDecimal) r[2], (BigDecimal) r[3], Boolean.TRUE.equals(r[4]), Boolean.TRUE.equals(r[5])))
                .toList();
    }

    // ── reference data ──

    public Map<UUID, SubjectInfo> subjects(Collection<UUID> ids) {
        Map<UUID, SubjectInfo> out = new HashMap<>();
        if (ids.isEmpty()) {
            return out;
        }
        rows("SELECT id, name, subject_code FROM subject WHERE id IN (:ids)", Map.of("ids", ids))
                .forEach(r -> out.put(uuid(r[0]), new SubjectInfo(uuid(r[0]), (String) r[1], (String) r[2])));
        return out;
    }

    /** Marked school days and days present (present, late or half day) per student between the dates. */
    public Map<UUID, Attendance> attendance(Collection<UUID> studentIds, LocalDate from, LocalDate to) {
        Map<UUID, Attendance> out = new HashMap<>();
        if (studentIds.isEmpty() || from == null || to == null) {
            return out;
        }
        rows("SELECT student_id, count(*), count(*) FILTER (WHERE status IN ('PRESENT', 'LATE', 'HALF_DAY')) FROM student_attendance "
                + "WHERE student_id IN (:ids) AND attendance_date BETWEEN :f AND :t AND status <> 'DELETED' GROUP BY student_id",
                params("ids", studentIds, "f", from, "t", to))
                .forEach(r -> out.put(uuid(r[0]), new Attendance(((Number) r[1]).intValue(), ((Number) r[2]).intValue())));
        return out;
    }

    // ── plumbing ──

    @SuppressWarnings("unchecked")
    public List<Object[]> rows(String sql, Map<String, ?> params) {
        var q = em.createNativeQuery(sql);
        params.forEach(q::setParameter);
        List<?> raw = q.getResultList();
        return raw.stream().map(o -> o instanceof Object[] a ? a : new Object[]{o}).toList();
    }

    public int update(String sql, Map<String, ?> params) {
        var q = em.createNativeQuery(sql);
        params.forEach(q::setParameter);
        return q.executeUpdate();
    }

    public static Map<String, Object> params(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    public static UUID uuid(Object o) {
        return o == null ? null : o instanceof UUID u ? u : UUID.fromString(o.toString());
    }

    public static LocalDate date(Object o) {
        return o == null ? null : o instanceof LocalDate d ? d : o instanceof Date d ? d.toLocalDate() : LocalDate.parse(o.toString());
    }
}
