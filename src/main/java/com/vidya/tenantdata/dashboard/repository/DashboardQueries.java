package com.vidya.tenantdata.dashboard.repository;

import com.vidya.tenantdata.dashboard.dto.DashboardDtos.AcademicCounts;
import com.vidya.tenantdata.dashboard.dto.DashboardDtos.ActionItemType;
import com.vidya.tenantdata.dashboard.dto.DashboardDtos.AutoDebit;
import com.vidya.tenantdata.dashboard.dto.DashboardDtos.ClassEnrollment;
import com.vidya.tenantdata.dashboard.dto.DashboardDtos.StaffCounts;
import com.vidya.tenantdata.dashboard.dto.DashboardDtos.StudentCounts;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Read-only aggregate SQL behind the overview dashboard (plus the one dismissal write). Callers own the transaction.
 * Every query is a single aggregate over indexed columns, so the dashboard costs a fixed number of queries whatever the school's size.
 */
@Repository
public class DashboardQueries {

    /** Enrolment states that count as "on the rolls" for the year. */
    static final String LIVE = "('ADMITTED', 'ACTIVE')";
    /** Enrolment states that count for a past year (a closed year's students are PROMOTED or COMPLETED). */
    static final String COUNTED_PAST = "('ADMITTED', 'ACTIVE', 'PROMOTED', 'COMPLETED')";
    static final int ACTION_ITEM_LIMIT = 500;
    /** A pending mandate becomes an action item after this many days. */
    static final int MANDATE_STALLED_DAYS = 3;

    private static final String STAFF_NAME = "COALESCE(NULLIF(trim(p.display_name), ''), NULLIF(trim(concat_ws(' ', p.first_name, p.last_name)), ''), st.staff_code)";
    private static final String STUDENT_NAME = "COALESCE(NULLIF(trim(sp.display_name), ''), NULLIF(trim(concat_ws(' ', sp.first_name, sp.last_name)), ''), "
            + "s.admission_number)";
    private static final String NOT_DISMISSED = " AND NOT EXISTS (SELECT 1 FROM dashboard_action_dismissal d WHERE d.user_id = :u "
            + "AND d.item_type = '%s' AND d.item_key = %s) ";

    @PersistenceContext(unitName = "tenant")
    private EntityManager em;

    // ── Year ─────────────────────────────────────────────────────────────────────────

    /** The academic year that ended most recently before {@code start}. */
    public Optional<UUID> previousYearId(LocalDate start) {
        List<?> rows = em.createNativeQuery("SELECT id FROM academic_year WHERE deleted_at IS NULL AND status NOT IN ('CANCELLED', 'DELETED') "
                        + "AND end_date < :s ORDER BY end_date DESC LIMIT 1")
                .setParameter("s", start).getResultList();
        return rows.isEmpty() ? Optional.empty() : Optional.of(uuid(rows.get(0)));
    }

    // ── Students ─────────────────────────────────────────────────────────────────────

    /** Students with a live enrolment in the year. */
    public long liveEnrollment(UUID y) {
        return enrollment(y, LIVE);
    }

    /** Students enrolled in a past year (including those since promoted or passed out). */
    public long pastEnrollment(UUID y) {
        return enrollment(y, COUNTED_PAST);
    }

    private long enrollment(UUID y, String statuses) {
        return count(em.createNativeQuery("SELECT count(DISTINCT e.student_id) FROM student_academic_enrollment e "
                        + "JOIN student s ON s.id = e.student_id AND s.deleted_at IS NULL "
                        + "WHERE e.academic_year_id = :y AND e.deleted_at IS NULL AND e.enrollment_status IN " + statuses)
                .setParameter("y", y));
    }

    /** Sum of the capacities of the year's active sections; null when no section has a capacity. */
    public Long sectionCapacity(UUID y) {
        Object v = em.createNativeQuery("SELECT sum(capacity) FROM section WHERE academic_year_id = :y AND deleted_at IS NULL AND status = 'ACTIVE'")
                .setParameter("y", y).getSingleResult();
        return v == null ? null : ((Number) v).longValue();
    }

    /** Students whose admission date falls in the window. */
    public long admittedBetween(LocalDate from, LocalDate to) {
        return count(em.createNativeQuery("SELECT count(*) FROM student WHERE deleted_at IS NULL AND status <> 'DELETED' "
                        + "AND admission_date BETWEEN :f AND :t")
                .setParameter("f", from).setParameter("t", to));
    }

    /** Head counts of the year's live roll; each student counted once (latest enrolment). */
    public StudentCounts studentCounts(UUID y) {
        Object[] r = (Object[]) em.createNativeQuery("""
                        SELECT count(*),
                               count(*) FILTER (WHERE e.enrollment_status = 'ADMITTED'),
                               count(*) FILTER (WHERE e.enrollment_status = 'ACTIVE'),
                               count(*) FILTER (WHERE e.section_id IS NULL),
                               count(*) FILTER (WHERE upper(p.gender) IN ('MALE', 'M', 'BOY')),
                               count(*) FILTER (WHERE upper(p.gender) IN ('FEMALE', 'F', 'GIRL')),
                               count(*) FILTER (WHERE EXISTS (SELECT 1 FROM student_guardian g WHERE g.student_id = e.student_id
                                                              AND g.deleted_at IS NULL AND g.status = 'ACTIVE'))
                        FROM (SELECT DISTINCT ON (e.student_id) e.student_id, e.enrollment_status, e.section_id
                              FROM student_academic_enrollment e
                              WHERE e.academic_year_id = :y AND e.deleted_at IS NULL AND e.enrollment_status IN %s
                              ORDER BY e.student_id, e.enrollment_date DESC) e
                        JOIN student s ON s.id = e.student_id AND s.deleted_at IS NULL
                        LEFT JOIN tenant_user_profile p ON p.user_id = s.tenant_user_id""".formatted(LIVE))
                .setParameter("y", y).getSingleResult();
        long total = num(r[0]);
        long boys = num(r[4]);
        long girls = num(r[5]);
        return new StudentCounts(total, num(r[1]), num(r[2]), num(r[3]), boys, girls, total - boys - girls, num(r[6]));
    }

    /** Live roll per class, for classes that have sections or students in the year. */
    public List<ClassEnrollment> enrollmentByClass(UUID y) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                        WITH sec AS (SELECT academic_class_id, sum(capacity) AS cap, count(*) AS n FROM section
                                     WHERE academic_year_id = :y AND deleted_at IS NULL AND status = 'ACTIVE' GROUP BY academic_class_id),
                             enr AS (SELECT e.academic_class_id, count(DISTINCT e.student_id) AS n FROM student_academic_enrollment e
                                     JOIN student s ON s.id = e.student_id AND s.deleted_at IS NULL
                                     WHERE e.academic_year_id = :y AND e.deleted_at IS NULL AND e.enrollment_status IN %s
                                     GROUP BY e.academic_class_id)
                        SELECT c.id, COALESCE(c.display_name, c.name), c.sort_order, COALESCE(enr.n, 0), sec.cap, COALESCE(sec.n, 0)
                        FROM academic_class c
                        LEFT JOIN sec ON sec.academic_class_id = c.id
                        LEFT JOIN enr ON enr.academic_class_id = c.id
                        WHERE sec.n IS NOT NULL OR enr.n IS NOT NULL
                        ORDER BY c.sort_order, c.name""".formatted(LIVE))
                .setParameter("y", y).getResultList();
        List<ClassEnrollment> out = new ArrayList<>();
        for (Object[] r : rows) {
            long students = num(r[3]);
            Long cap = r[4] == null ? null : num(r[4]);
            out.add(new ClassEnrollment(uuid(r[0]), (String) r[1], ((Number) r[2]).intValue(), students, cap, percent(students, cap),
                    ((Number) r[5]).intValue()));
        }
        return out;
    }

    // ── Attendance ───────────────────────────────────────────────────────────────────

    /** Marks of one day by status (DELETED marks are left out). */
    public Map<String, Long> attendanceByStatus(LocalDate date) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("SELECT status, count(*) FROM student_attendance WHERE attendance_date = :d "
                        + "AND deleted_at IS NULL AND status <> 'DELETED' GROUP BY status")
                .setParameter("d", date).getResultList();
        Map<String, Long> out = new LinkedHashMap<>();
        rows.forEach(r -> out.put((String) r[0], num(r[1])));
        return out;
    }

    /** Marks per day and status, for the days in the window that have marks. */
    public Map<LocalDate, Map<String, Long>> attendanceByDay(LocalDate from, LocalDate to) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("SELECT attendance_date, status, count(*) FROM student_attendance "
                        + "WHERE attendance_date BETWEEN :f AND :t AND deleted_at IS NULL AND status <> 'DELETED' "
                        + "GROUP BY attendance_date, status ORDER BY attendance_date")
                .setParameter("f", from).setParameter("t", to).getResultList();
        Map<LocalDate, Map<String, Long>> out = new TreeMap<>();
        for (Object[] r : rows) {
            out.computeIfAbsent(date(r[0]), d -> new LinkedHashMap<>()).put((String) r[1], num(r[2]));
        }
        return out;
    }

    /** [sections with live students in the year, of which have at least one mark on the date]. */
    public long[] sectionsMarked(UUID y, LocalDate date) {
        Object[] r = (Object[]) em.createNativeQuery("""
                        SELECT count(*), count(*) FILTER (WHERE EXISTS (SELECT 1 FROM student_attendance a WHERE a.section_id = x.section_id
                                                          AND a.attendance_date = :d AND a.deleted_at IS NULL AND a.status <> 'DELETED'))
                        FROM (SELECT DISTINCT e.section_id FROM student_academic_enrollment e
                              JOIN section s ON s.id = e.section_id AND s.deleted_at IS NULL AND s.status = 'ACTIVE'
                              WHERE e.academic_year_id = :y AND e.deleted_at IS NULL AND e.enrollment_status IN %s) x""".formatted(LIVE))
                .setParameter("y", y).setParameter("d", date).getSingleResult();
        return new long[]{num(r[0]), num(r[1])};
    }

    // ── Staff and academics ──────────────────────────────────────────────────────────

    public StaffCounts staffCounts() {
        Object[] r = (Object[]) em.createNativeQuery("""
                        SELECT count(*) FILTER (WHERE employment_status = 'ACTIVE'),
                               count(*) FILTER (WHERE employment_status = 'ACTIVE' AND staff_type = 'TEACHER'),
                               count(*) FILTER (WHERE employment_status = 'ACTIVE' AND staff_type <> 'TEACHER'),
                               count(*) FILTER (WHERE employment_status IN ('INACTIVE', 'SUSPENDED'))
                        FROM staff WHERE deleted_at IS NULL""")
                .getSingleResult();
        return new StaffCounts(num(r[0]), num(r[1]), num(r[2]), num(r[3]));
    }

    public AcademicCounts academicCounts(UUID y) {
        Object[] r = (Object[]) em.createNativeQuery("""
                        SELECT (SELECT count(DISTINCT academic_class_id) FROM section WHERE academic_year_id = :y AND deleted_at IS NULL AND status = 'ACTIVE'),
                               (SELECT count(*) FROM section WHERE academic_year_id = :y AND deleted_at IS NULL AND status = 'ACTIVE'),
                               (SELECT count(*) FROM subject WHERE deleted_at IS NULL AND status = 'ACTIVE'),
                               (SELECT count(*) FROM section s WHERE s.academic_year_id = :y AND s.deleted_at IS NULL AND s.status = 'ACTIVE'
                                  AND NOT EXISTS (SELECT 1 FROM class_teacher_assignment a WHERE a.section_id = s.id AND a.academic_year_id = :y
                                                  AND a.deleted_at IS NULL AND a.status = 'ACTIVE')),
                               (SELECT CASE WHEN bool_or(status = 'ACTIVE') THEN 'ACTIVE' WHEN bool_or(status = 'PUBLISHED') THEN 'PUBLISHED'
                                            WHEN bool_or(status = 'DRAFT') THEN 'DRAFT' ELSE 'NONE' END
                                  FROM timetable WHERE academic_year_id = :y AND deleted_at IS NULL)""")
                .setParameter("y", y).getSingleResult();
        return new AcademicCounts(num(r[0]), num(r[1]), num(r[2]), num(r[3]), r[4] == null ? "NONE" : (String) r[4]);
    }

    // ── Operations ───────────────────────────────────────────────────────────────────

    /** Active substitutions of the day by type (TEACHER_SUBSTITUTE, ROOM_CHANGE, CLASS_CANCELLED). */
    public Map<String, Long> substitutionsByType(LocalDate date) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("SELECT substitution_type, count(*) FROM timetable_substitution WHERE substitution_date = :d "
                        + "AND deleted_at IS NULL AND status = 'ACTIVE' GROUP BY substitution_type")
                .setParameter("d", date).getResultList();
        Map<String, Long> out = new LinkedHashMap<>();
        rows.forEach(r -> out.put((String) r[0], num(r[1])));
        return out;
    }

    /** Papers of the year's published or running exams ({@code :y}); joined as x / es / t. */
    private static final String PUBLISHED_PAPERS = """
            FROM exam_subject_config x
            JOIN exam_schedule es ON es.id = x.exam_schedule_id AND es.deleted_at IS NULL AND es.status IN ('PUBLISHED', 'IN_PROGRESS')
            JOIN exam_term t ON t.id = es.exam_term_id AND t.deleted_at IS NULL AND t.academic_year_id = :y""";

    public record NextExam(String termName, String scheduleName, String className, String subjectName, LocalDate examDate, long papersNext7Days) {
    }

    /** The next paper of a published exam of the year on or after the date, and how many papers fall in the following week. */
    public Optional<NextExam> nextExam(UUID y, LocalDate date) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("SELECT t.name, es.name, COALESCE(c.display_name, c.name), sub.name, x.exam_date "
                        + PUBLISHED_PAPERS
                        + " JOIN academic_class c ON c.id = es.academic_class_id JOIN subject sub ON sub.id = x.subject_id"
                        + " WHERE x.deleted_at IS NULL AND x.exam_date >= :d ORDER BY x.exam_date, x.start_time LIMIT 1")
                .setParameter("y", y).setParameter("d", date).getResultList();
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        long nextWeek = count(em.createNativeQuery("SELECT count(*) " + PUBLISHED_PAPERS
                        + " WHERE x.deleted_at IS NULL AND x.exam_date >= :d AND x.exam_date < CAST(:d AS date) + 7")
                .setParameter("y", y).setParameter("d", date));
        Object[] r = rows.get(0);
        return Optional.of(new NextExam((String) r[0], (String) r[1], (String) r[2], (String) r[3], date(r[4]), nextWeek));
    }

    public record Transport(long activeVehicles, long inMaintenance, long activeRoutes, long documentsExpiring) {
    }

    /** Fleet set-up, and vehicles whose insurance / fitness / permit / PUC expires within 30 days (or already has). */
    public Transport transport(LocalDate date) {
        Object[] r = (Object[]) em.createNativeQuery("""
                        SELECT (SELECT count(*) FROM transport_vehicle WHERE deleted_at IS NULL AND status = 'ACTIVE'),
                               (SELECT count(*) FROM transport_vehicle WHERE deleted_at IS NULL AND status = 'MAINTENANCE'),
                               (SELECT count(*) FROM transport_route WHERE deleted_at IS NULL AND status = 'ACTIVE'),
                               (SELECT count(*) FROM transport_vehicle WHERE deleted_at IS NULL AND status <> 'INACTIVE'
                                  AND LEAST(insurance_valid_till, fitness_valid_till, permit_valid_till, puc_valid_till) < CAST(:d AS date) + 30)""")
                .setParameter("d", date).getSingleResult();
        return new Transport(num(r[0]), num(r[1]), num(r[2]), num(r[3]));
    }

    /** Holidays starting within the next 30 days. */
    public long upcomingHolidays(LocalDate date) {
        return count(em.createNativeQuery("SELECT count(*) FROM school_holiday WHERE deleted_at IS NULL "
                        + "AND start_date >= :d AND start_date < CAST(:d AS date) + 30")
                .setParameter("d", date));
    }

    /** [ACTIVE, DRAFT] fee structures of the year. */
    public long[] feeStructures(UUID y) {
        Object[] r = (Object[]) em.createNativeQuery("SELECT count(*) FILTER (WHERE status = 'ACTIVE'), count(*) FILTER (WHERE status = 'DRAFT') "
                        + "FROM fee_structure WHERE academic_year_id = :y AND deleted_at IS NULL")
                .setParameter("y", y).getSingleResult();
        return new long[]{num(r[0]), num(r[1])};
    }

    // ── Action items ─────────────────────────────────────────────────────────────────

    /** One pending item, before the service turns it into a response. {@code key} identifies it within its type. */
    public record PendingRow(ActionItemType type, String key, String title, String description, String raisedBy, Instant raisedAt) {
    }

    private static final Map<ActionItemType, String> PENDING_SQL = new EnumMap<>(ActionItemType.class);

    static {
        PENDING_SQL.put(ActionItemType.LESSON_PLAN_REVIEW, """
                SELECT lp.id::text, 'Lesson plan to review: ' || lp.title,
                       COALESCE(c.display_name, c.name) || COALESCE(' ' || sec.name, '') || ' · ' || sub.name || ' · ' || lp.plan_date,
                       %s, COALESCE(lp.submitted_at, lp.updated_at)
                FROM lesson_plan lp
                JOIN academic_class c ON c.id = lp.academic_class_id
                LEFT JOIN section sec ON sec.id = lp.section_id
                JOIN subject sub ON sub.id = lp.subject_id
                JOIN staff st ON st.id = lp.teacher_id
                LEFT JOIN tenant_user_profile p ON p.user_id = st.tenant_user_id
                WHERE lp.academic_year_id = :y AND lp.deleted_at IS NULL AND lp.status = 'SUBMITTED'""".formatted(STAFF_NAME)
                + NOT_DISMISSED.formatted("LESSON_PLAN_REVIEW", "lp.id::text"));
        PENDING_SQL.put(ActionItemType.MARKS_LOCK, """
                SELECT x.id::text, 'Marks to lock: ' || COALESCE(c.display_name, c.name) || ' · ' || sub.name,
                       t.name || ' · ' || es.name || ' · ' || count(m.id) || ' student(s) submitted',
                       NULL, max(m.updated_at)
                FROM exam_subject_config x
                JOIN exam_schedule es ON es.id = x.exam_schedule_id AND es.deleted_at IS NULL AND es.status <> 'CANCELLED'
                JOIN exam_term t ON t.id = es.exam_term_id AND t.deleted_at IS NULL AND t.academic_year_id = :y
                JOIN academic_class c ON c.id = es.academic_class_id
                JOIN subject sub ON sub.id = x.subject_id
                JOIN student_mark_entry m ON m.exam_subject_config_id = x.id AND m.deleted_at IS NULL AND m.status = 'SUBMITTED'
                WHERE x.deleted_at IS NULL"""
                + NOT_DISMISSED.formatted("MARKS_LOCK", "x.id::text")
                + " GROUP BY x.id, c.display_name, c.name, sub.name, t.name, es.name");
        PENDING_SQL.put(ActionItemType.REPORT_CARD_PUBLISH, """
                SELECT t.id::text || ':' || c.id::text, 'Report cards to publish: ' || COALESCE(c.display_name, c.name),
                       t.name || ' · ' || count(*) || ' card(s) generated', NULL, max(r.updated_at)
                FROM student_report_card r
                JOIN exam_term t ON t.id = r.exam_term_id
                JOIN academic_class c ON c.id = r.academic_class_id
                WHERE r.academic_year_id = :y AND r.deleted_at IS NULL AND r.status = 'GENERATED'"""
                + NOT_DISMISSED.formatted("REPORT_CARD_PUBLISH", "t.id::text || ':' || c.id::text")
                + " GROUP BY t.id, t.name, c.id, c.display_name, c.name");
        PENDING_SQL.put(ActionItemType.REEVALUATION_REQUEST, """
                SELECT rr.id::text, 'Re-evaluation request: ' || %s,
                       COALESCE(c.display_name, c.name) || ' · ' || sub.name || ' · ' || t.name || ' · ' || rr.status,
                       NULL, rr.created_at
                FROM reevaluation_request rr
                JOIN exam_subject_config x ON x.id = rr.exam_subject_config_id
                JOIN exam_schedule es ON es.id = x.exam_schedule_id
                JOIN exam_term t ON t.id = es.exam_term_id AND t.academic_year_id = :y
                JOIN academic_class c ON c.id = es.academic_class_id
                JOIN subject sub ON sub.id = x.subject_id
                JOIN student s ON s.id = rr.student_id
                LEFT JOIN tenant_user_profile sp ON sp.user_id = s.tenant_user_id
                WHERE rr.status IN ('REQUESTED', 'UNDER_REVIEW')""".formatted(STUDENT_NAME)
                + NOT_DISMISSED.formatted("REEVALUATION_REQUEST", "rr.id::text"));
        // The maker cannot approve their own request, so it is not on their list
        PENDING_SQL.put(ActionItemType.FEE_CONCESSION, """
                SELECT cr.id::text, 'Concession to approve: ' || %s,
                       ct.name || ' · ' || CASE WHEN cr.kind = 'PERCENT' THEN rtrim(to_char(cr.percent, 'FM990.99'), '.') || '%%'
                                                ELSE '₹' || trim(to_char(cr.amount_paise / 100.0, 'FM99999999990.00')) END
                       || COALESCE(' · ' || COALESCE(c.display_name, c.name), ''),
                       cr.requested_by_name, cr.created_at
                FROM fee_concession_request cr
                JOIN fee_concession_type ct ON ct.id = cr.concession_type_id
                JOIN student s ON s.id = cr.student_id
                LEFT JOIN tenant_user_profile sp ON sp.user_id = s.tenant_user_id
                LEFT JOIN LATERAL (SELECT e.academic_class_id FROM student_academic_enrollment e WHERE e.student_id = s.id
                                   AND e.academic_year_id = cr.academic_year_id AND e.deleted_at IS NULL ORDER BY e.enrollment_date DESC LIMIT 1) en ON true
                LEFT JOIN academic_class c ON c.id = en.academic_class_id
                WHERE cr.academic_year_id = :y AND cr.status = 'PENDING' AND cr.requested_by IS DISTINCT FROM :u""".formatted(STUDENT_NAME)
                + NOT_DISMISSED.formatted("FEE_CONCESSION", "cr.id::text"));
        PENDING_SQL.put(ActionItemType.FEE_DEBIT_ERROR, """
                SELECT d.id::text, 'Auto-debit refused by the gateway: ' || %s,
                       '₹' || trim(to_char(d.amount_paise / 100.0, 'FM99999999990.00')) || ' · ' || m.payer_name || ' · ' || d.scheduled_on
                       || COALESCE(' · ' || d.failure_reason, ''),
                       NULL, d.updated_at
                FROM fee_debit d
                JOIN fee_ledger l ON l.id = d.ledger_id AND l.academic_year_id = :y
                JOIN fee_mandate m ON m.id = d.mandate_id
                JOIN student s ON s.id = d.student_id
                LEFT JOIN tenant_user_profile sp ON sp.user_id = s.tenant_user_id
                WHERE d.status = 'ERROR'
                  AND NOT EXISTS (SELECT 1 FROM fee_debit n WHERE n.mandate_id = d.mandate_id AND n.student_id = d.student_id
                                  AND n.cycle_month = d.cycle_month AND n.attempt_no > d.attempt_no)""".formatted(STUDENT_NAME)
                + NOT_DISMISSED.formatted("FEE_DEBIT_ERROR", "d.id::text"));
        PENDING_SQL.put(ActionItemType.FEE_UNALLOCATED, """
                SELECT c.id::text, 'Online payment to refund: ₹' || trim(to_char(c.unallocated_paise / 100.0, 'FM99999999990.00')),
                       c.payer_name || ' paid ₹' || trim(to_char(c.paid_paise / 100.0, 'FM99999999990.00')) || ' on ' || c.reference
                       || ' · more than was due',
                       NULL, c.updated_at
                FROM fee_checkout c
                WHERE c.unallocated_paise > 0
                  AND EXISTS (SELECT 1 FROM fee_checkout_student cs JOIN fee_ledger l ON l.id = cs.ledger_id
                              WHERE cs.checkout_id = c.id AND l.academic_year_id = :y)"""
                + NOT_DISMISSED.formatted("FEE_UNALLOCATED", "c.id::text"));
        PENDING_SQL.put(ActionItemType.FEE_MANDATE_STALLED, """
                SELECT m.id::text, 'Mandate not approved yet: ' || m.payer_name,
                       m.method || ' via ' || m.provider || ' · ' || count(ms.student_id) || ' child(ren) · waiting since '
                       || to_char(m.created_at AT TIME ZONE 'Asia/Kolkata', 'DD Mon YYYY'),
                       NULL, m.created_at
                FROM fee_mandate m
                LEFT JOIN fee_mandate_student ms ON ms.mandate_id = m.id AND ms.live
                WHERE m.status = 'PENDING' AND m.created_at < now() - make_interval(days => %d) AND CAST(:y AS uuid) IS NOT NULL""".formatted(MANDATE_STALLED_DAYS)
                + NOT_DISMISSED.formatted("FEE_MANDATE_STALLED", "m.id::text")
                + " GROUP BY m.id, m.payer_name, m.method, m.provider, m.created_at");
    }

    /** Pending items of one type in the year that {@code userId} has not dismissed, oldest first (at most {@value #ACTION_ITEM_LIMIT}). */
    public List<PendingRow> pending(ActionItemType type, UUID y, UUID userId) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(PENDING_SQL.get(type) + " ORDER BY 5 NULLS LAST LIMIT " + ACTION_ITEM_LIMIT)
                .setParameter("y", y).setParameter("u", userId).getResultList();
        List<PendingRow> out = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            out.add(new PendingRow(type, (String) r[0], (String) r[1], (String) r[2], (String) r[3], instant(r[4])));
        }
        return out;
    }

    // ── Auto-debit ───────────────────────────────────────────────────────────────────

    /** The month's auto-debits per state, and mandates live now. {@code cycleMonth} is the first of the month. */
    public AutoDebit autoDebit(LocalDate cycleMonth, String href) {
        @SuppressWarnings("unchecked")
        List<Object[]> settings = em.createNativeQuery("SELECT enabled, debit_day FROM fee_autodebit_settings WHERE id = 1").getResultList();
        boolean enabled = !settings.isEmpty() && Boolean.TRUE.equals(settings.getFirst()[0]);
        int day = settings.isEmpty() ? 10 : (int) num(settings.getFirst()[1]);
        Object[] m = (Object[]) em.createNativeQuery("SELECT count(*) FILTER (WHERE status = 'ACTIVE'), count(*) FILTER (WHERE status = 'PENDING') "
                + "FROM fee_mandate").getSingleResult();
        Map<String, long[]> by = new LinkedHashMap<>();
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("SELECT status, count(*), COALESCE(sum(amount_paise), 0), "
                        + "COALESCE(sum(bounce_charge_paise + bounce_gst_paise), 0) FROM fee_debit WHERE cycle_month = :m GROUP BY status")
                .setParameter("m", cycleMonth).getResultList();
        for (Object[] r : rows) {
            by.put((String) r[0], new long[]{num(r[1]), num(r[2]), num(r[3])});
        }
        long[] zero = new long[3];
        long[] scheduled = by.getOrDefault("SCHEDULED", zero);
        long[] submitting = by.getOrDefault("SUBMITTING", zero);
        long[] submitted = by.getOrDefault("SUBMITTED", zero);
        long[] success = by.getOrDefault("SUCCESS", zero);
        long[] failed = by.getOrDefault("FAILED", zero);
        long charges = by.values().stream().mapToLong(v -> v[2]).sum();
        return new AutoDebit(enabled, cycleMonth, cycleMonth.withDayOfMonth(Math.min(day, cycleMonth.lengthOfMonth())), num(m[0]), num(m[1]),
                scheduled[0], scheduled[1], submitting[0] + submitted[0], submitting[1] + submitted[1], success[0], success[1],
                failed[0], failed[1], charges, by.getOrDefault("SKIPPED", zero)[0], by.getOrDefault("ERROR", zero)[0], href);
    }

    /** Hides an item from one user's dashboard. Returns false when it was already dismissed. */
    public boolean dismiss(UUID userId, ActionItemType type, String key) {
        return em.createNativeQuery("INSERT INTO dashboard_action_dismissal (user_id, item_type, item_key) VALUES (:u, :t, :k) "
                        + "ON CONFLICT (user_id, item_type, item_key) DO NOTHING")
                .setParameter("u", userId).setParameter("t", type.name()).setParameter("k", key).executeUpdate() > 0;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────────

    private static long count(Query q) {
        return num(q.getSingleResult());
    }

    static long num(Object o) {
        return o == null ? 0L : ((Number) o).longValue();
    }

    static UUID uuid(Object o) {
        return o instanceof UUID u ? u : UUID.fromString(o.toString());
    }

    static LocalDate date(Object o) {
        if (o instanceof LocalDate d) {
            return d;
        }
        return ((java.sql.Date) o).toLocalDate();
    }

    static Instant instant(Object o) {
        return switch (o) {
            case null -> null;
            case Instant i -> i;
            case OffsetDateTime odt -> odt.toInstant();
            case Timestamp ts -> ts.toInstant();
            default -> Instant.parse(o.toString());
        };
    }

    /** {@code part} of {@code whole} in %, 1 decimal; null when there is no whole. */
    public static BigDecimal percent(long part, Long whole) {
        if (whole == null || whole == 0) {
            return null;
        }
        return BigDecimal.valueOf(part * 100.0 / whole).setScale(1, java.math.RoundingMode.HALF_UP);
    }
}
