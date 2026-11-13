package com.vidya.tenantdata.readiness.service;

import com.vidya.tenantdata.readiness.dto.ReadinessDtos.Category;
import com.vidya.tenantdata.readiness.dto.ReadinessDtos.Severity;

import java.util.List;

import static com.vidya.tenantdata.readiness.dto.ReadinessDtos.Category.CONFIGURATION;
import static com.vidya.tenantdata.readiness.dto.ReadinessDtos.Category.CONFLICTS;
import static com.vidya.tenantdata.readiness.dto.ReadinessDtos.Category.MISSING_DATA;
import static com.vidya.tenantdata.readiness.dto.ReadinessDtos.Category.PENDING_APPROVALS;
import static com.vidya.tenantdata.readiness.dto.ReadinessDtos.Severity.BLOCKER;
import static com.vidya.tenantdata.readiness.dto.ReadinessDtos.Severity.INFO;
import static com.vidya.tenantdata.readiness.dto.ReadinessDtos.Severity.WARNING;

/**
 * Readiness rules that are a single query. Each query returns the offending rows as {@code (id text, label text)},
 * already in display order; no rows means the rule passes. Anything a unique index already prevents (two current years,
 * two class teachers per section, two live enrolments per student) is not checked here. {@code :y} is the academic year being checked.
 */
final class ReadinessRules {

    private ReadinessRules() {
    }

    /**
     * @param authority the user must hold it for the rule to run (otherwise the area is reported as not checked)
     * @param message   %d is replaced by the number of rows
     */
    record Rule(String code, Category category, String area, Severity severity, String authority, String message, String api, String sql) {
    }

    private static final String STAFF_NAME = "COALESCE(NULLIF(trim(p.display_name), ''), NULLIF(trim(concat_ws(' ', p.first_name, p.last_name)), ''), st.staff_code)";
    private static final String STUDENT_NAME = "COALESCE(NULLIF(trim(p.display_name), ''), NULLIF(trim(concat_ws(' ', p.first_name, p.last_name)), ''), "
            + "st.admission_number, st.student_code)";
    private static final String LIVE_ENROLMENT = "e.academic_year_id = :y AND e.deleted_at IS NULL AND e.enrollment_status IN ('ADMITTED', 'ACTIVE')";
    private static final String PAPER = """
            FROM exam_subject_config x
            JOIN exam_schedule es ON es.id = x.exam_schedule_id AND es.deleted_at IS NULL AND es.status <> 'CANCELLED'
            JOIN exam_term t ON t.id = es.exam_term_id AND t.deleted_at IS NULL AND t.academic_year_id = :y
            JOIN academic_class c ON c.id = es.academic_class_id
            JOIN subject sub ON sub.id = x.subject_id
            """;

    static final List<Rule> RULES = List.of(

            // ── Configuration: settings that are saved but do not add up ──────────────────────────────
            new Rule("TERM_OUTSIDE_YEAR", CONFIGURATION, "EXAMINATIONS", WARNING, "EXAM_READ",
                    "%d term(s) start or end outside the academic year.", "PUT /api/v1/exams/terms/{id}", """
                    SELECT t.id::text, t.name || ' (' || t.start_date || ' – ' || t.end_date || ')'
                    FROM exam_term t JOIN academic_year y ON y.id = t.academic_year_id
                    WHERE t.academic_year_id = :y AND t.deleted_at IS NULL AND t.status <> 'CANCELLED'
                      AND (t.start_date < y.start_date OR t.end_date > y.end_date)
                    ORDER BY t.start_date"""),
            new Rule("TERM_WEIGHTAGE_NOT_100", CONFIGURATION, "EXAMINATIONS", WARNING, "EXAM_READ",
                    "Term weightages do not add up to 100%%; report-card totals will be off.", "PUT /api/v1/exams/terms/{id}", """
                    SELECT t.academic_year_id::text, 'Terms add up to ' || sum(t.weightage_percentage) || '%'
                    FROM exam_term t
                    WHERE t.academic_year_id = :y AND t.deleted_at IS NULL AND t.status <> 'CANCELLED'
                    GROUP BY t.academic_year_id
                    HAVING sum(t.weightage_percentage) > 0 AND sum(t.weightage_percentage) <> 100"""),
            new Rule("HOLIDAY_OUTSIDE_YEAR", CONFIGURATION, "CALENDAR", WARNING, "CALENDAR_READ",
                    "%d holiday(s) fall outside the academic year.", "PUT /api/v1/holidays/{id}", """
                    SELECT h.id::text, h.name || ' (' || h.start_date || ')'
                    FROM school_holiday h JOIN academic_year y ON y.id = h.academic_year_id
                    WHERE h.academic_year_id = :y AND h.deleted_at IS NULL
                      AND (h.start_date < y.start_date OR h.end_date > y.end_date)
                    ORDER BY h.start_date"""),
            new Rule("INSTALLMENTS_NOT_100", CONFIGURATION, "FEES", BLOCKER, "FEE_READ",
                    "Instalment percentages do not add up to 100%%; fees cannot be split.", "PUT /api/v1/operations-config/fees/installments", """
                    SELECT i.academic_year_id::text, 'Instalments add up to ' || sum(i.percent) || '%'
                    FROM fee_installment i
                    WHERE i.academic_year_id = :y
                    GROUP BY i.academic_year_id
                    HAVING sum(i.percent) <> 100"""),
            new Rule("INSTALLMENT_AFTER_YEAR_END", CONFIGURATION, "FEES", WARNING, "FEE_READ",
                    "%d instalment(s) fall due after the academic year ends.", "PUT /api/v1/operations-config/fees/installments", """
                    SELECT i.id::text, i.name || ' (' || i.due_date || ')'
                    FROM fee_installment i JOIN academic_year y ON y.id = i.academic_year_id
                    WHERE i.academic_year_id = :y AND i.due_date > y.end_date
                    ORDER BY i.installment_no"""),
            new Rule("FEE_STRUCTURE_INACTIVE_HEAD", CONFIGURATION, "FEES", WARNING, "FEE_READ",
                    "%d active fee structure(s) charge a fee head that is inactive or deleted.", "PUT /api/v1/operations-config/fees/structures", """
                    SELECT DISTINCT f.id::text, c.name || ' – ' || h.name
                    FROM fee_structure f
                    JOIN fee_structure_item it ON it.fee_structure_id = f.id
                    JOIN fee_head h ON h.id = it.fee_head_id
                    JOIN academic_class c ON c.id = f.academic_class_id
                    WHERE f.academic_year_id = :y AND f.deleted_at IS NULL AND f.status = 'ACTIVE'
                      AND (h.status <> 'ACTIVE' OR h.deleted_at IS NOT NULL)
                    ORDER BY 2"""),
            new Rule("TIMETABLE_BELL_SCHEDULE_INACTIVE", CONFIGURATION, "TIMETABLE", WARNING, "TIMETABLE_READ",
                    "%d timetable(s) use a bell schedule that is inactive or deleted.", "GET /api/v1/bell-schedules", """
                    SELECT t.id::text, t.name || ' → ' || b.name
                    FROM timetable t JOIN bell_schedule b ON b.id = t.bell_schedule_id
                    WHERE t.academic_year_id = :y AND t.deleted_at IS NULL AND t.status IN ('DRAFT', 'PUBLISHED', 'ACTIVE')
                      AND (b.status <> 'ACTIVE' OR b.deleted_at IS NOT NULL)
                    ORDER BY t.name"""),

            // ── Conflicts: two pieces of data that contradict each other ──────────────────────────────
            new Rule("INACTIVE_TEACHER_ASSIGNED", CONFLICTS, "STAFF", BLOCKER, "CLASS_TEACHER_ASSIGNMENT_READ",
                    "%d teacher(s) who have left or are suspended are still class or subject teachers.", "PUT /api/v1/academic-config/subject-teachers", """
                    SELECT st.id::text, %s || ' (' || st.employment_status || ')'
                    FROM staff st LEFT JOIN tenant_user_profile p ON p.user_id = st.tenant_user_id
                    WHERE (st.employment_status <> 'ACTIVE' OR st.deleted_at IS NOT NULL)
                      AND (EXISTS (SELECT 1 FROM class_teacher_assignment a WHERE a.staff_id = st.id AND a.academic_year_id = :y
                                   AND a.deleted_at IS NULL AND a.status = 'ACTIVE')
                        OR EXISTS (SELECT 1 FROM teacher_subject_assignment a WHERE a.teacher_id = st.id AND a.academic_year_id = :y
                                   AND a.deleted_at IS NULL AND a.status = 'ACTIVE'))
                    ORDER BY 2""".formatted(STAFF_NAME)),
            new Rule("TEACHER_OVERLOADED", CONFLICTS, "TIMETABLE", WARNING, "TIMETABLE_READ",
                    "%d teacher(s) have more periods in the timetable than their weekly limit.", "GET /api/v1/academic-config/workload", """
                    SELECT st.id::text, %s || ' (' || w.periods || ' of max ' || st.max_periods_per_week || ')'
                    FROM (SELECT teacher_id, max(n) AS periods
                          FROM (SELECT e.timetable_id, e.teacher_id, count(*) AS n
                                FROM timetable_entry e JOIN timetable t ON t.id = e.timetable_id
                                WHERE t.academic_year_id = :y AND t.deleted_at IS NULL AND t.status IN ('PUBLISHED', 'ACTIVE')
                                  AND e.deleted_at IS NULL AND e.teacher_id IS NOT NULL
                                GROUP BY e.timetable_id, e.teacher_id) per_timetable
                          GROUP BY teacher_id) w
                    JOIN staff st ON st.id = w.teacher_id
                    LEFT JOIN tenant_user_profile p ON p.user_id = st.tenant_user_id
                    WHERE st.max_periods_per_week IS NOT NULL AND w.periods > st.max_periods_per_week
                    ORDER BY w.periods - st.max_periods_per_week DESC""".formatted(STAFF_NAME)),
            new Rule("SECTION_CLASS_MISMATCH", CONFLICTS, "STUDENTS", BLOCKER, "STUDENT_READ",
                    "%d student(s) are placed in a section of another class or year.", "PUT /api/v1/students/{studentId}/academic-assignments/{assignmentId}", """
                    SELECT st.id::text, %s || ': ' || c.name || ', section ' || sc.name || ' ' || s.name
                    FROM student_academic_enrollment e
                    JOIN student st ON st.id = e.student_id
                    LEFT JOIN tenant_user_profile p ON p.user_id = st.tenant_user_id
                    JOIN academic_class c ON c.id = e.academic_class_id
                    JOIN section s ON s.id = e.section_id
                    JOIN academic_class sc ON sc.id = s.academic_class_id
                    WHERE %s AND (s.academic_class_id <> e.academic_class_id OR s.academic_year_id <> e.academic_year_id)
                    ORDER BY 2""".formatted(STUDENT_NAME, LIVE_ENROLMENT)),
            new Rule("SECTION_OVER_CAPACITY", CONFLICTS, "STUDENTS", WARNING, "STUDENT_READ",
                    "%d section(s) have more students than their capacity.", "PUT /api/v1/sections/{id}", """
                    SELECT s.id::text, c.name || ' ' || s.name || ' (' || n.students || ' / ' || s.capacity || ')'
                    FROM section s
                    JOIN academic_class c ON c.id = s.academic_class_id
                    JOIN (SELECT e.section_id, count(*) AS students FROM student_academic_enrollment e
                          WHERE %s AND e.section_id IS NOT NULL GROUP BY e.section_id) n ON n.section_id = s.id
                    WHERE s.deleted_at IS NULL AND s.capacity IS NOT NULL AND n.students > s.capacity
                    ORDER BY c.sort_order, s.name""".formatted(LIVE_ENROLMENT)),
            new Rule("FEE_STRUCTURE_MIXED", CONFLICTS, "FEES", BLOCKER, "FEE_READ",
                    "%d class(es) have a fee structure for ALL students and also one for new or existing students.",
                    "DELETE /api/v1/operations-config/fees/structures/{id}", """
                    SELECT c.id::text, c.name
                    FROM academic_class c
                    WHERE EXISTS (SELECT 1 FROM fee_structure f WHERE f.academic_class_id = c.id AND f.academic_year_id = :y
                                  AND f.deleted_at IS NULL AND f.status <> 'ARCHIVED' AND f.applies_to = 'ALL')
                      AND EXISTS (SELECT 1 FROM fee_structure f WHERE f.academic_class_id = c.id AND f.academic_year_id = :y
                                  AND f.deleted_at IS NULL AND f.status <> 'ARCHIVED' AND f.applies_to <> 'ALL')
                    ORDER BY c.sort_order"""),
            new Rule("TERMS_OVERLAP", CONFLICTS, "EXAMINATIONS", WARNING, "EXAM_READ",
                    "%d pair(s) of terms overlap.", "PUT /api/v1/exams/terms/{id}", """
                    SELECT a.id::text, a.name || ' and ' || b.name
                    FROM exam_term a JOIN exam_term b ON b.academic_year_id = a.academic_year_id AND a.id < b.id
                         AND b.deleted_at IS NULL AND b.status <> 'CANCELLED'
                         AND a.start_date <= b.end_date AND b.start_date <= a.end_date
                    WHERE a.academic_year_id = :y AND a.deleted_at IS NULL AND a.status <> 'CANCELLED'
                    ORDER BY a.start_date"""),
            new Rule("EXAM_OUTSIDE_TERM", CONFLICTS, "EXAMINATIONS", WARNING, "EXAM_READ",
                    "%d exam paper(s) are dated outside their term.", "GET /api/v1/exams/schedules/{id}/subjects", """
                    SELECT x.id::text, c.name || ' – ' || sub.name || ' on ' || x.exam_date
                    %s
                    WHERE x.deleted_at IS NULL AND (x.exam_date < t.start_date OR x.exam_date > t.end_date)
                    ORDER BY x.exam_date""".formatted(PAPER)),
            new Rule("EXAM_ON_HOLIDAY", CONFLICTS, "EXAMINATIONS", WARNING, "EXAM_READ",
                    "%d exam paper(s) fall on a holiday.", "GET /api/v1/exams/schedules/{id}/subjects", """
                    SELECT x.id::text, c.name || ' – ' || sub.name || ' on ' || x.exam_date || ' (' || h.name || ')'
                    %s
                    JOIN school_holiday h ON h.academic_year_id = t.academic_year_id AND h.deleted_at IS NULL
                         AND h.holiday_type <> 'EXAM_BREAK' AND x.exam_date BETWEEN h.start_date AND h.end_date
                    WHERE x.deleted_at IS NULL
                    ORDER BY x.exam_date""".formatted(PAPER)),
            new Rule("EXAM_PAPERS_CLASH", CONFLICTS, "EXAMINATIONS", WARNING, "EXAM_READ",
                    "%d pair(s) of exam papers for the same class overlap in time.", "GET /api/v1/exams/schedules/{id}/subjects", """
                    SELECT x.id::text, c.name || ': ' || sub.name || ' and ' || sub2.name || ' on ' || x.exam_date
                    %s
                    JOIN exam_subject_config x2 ON x2.exam_schedule_id = x.exam_schedule_id AND x.id < x2.id AND x2.deleted_at IS NULL
                         AND x2.exam_date = x.exam_date AND x.start_time < x2.end_time AND x2.start_time < x.end_time
                    JOIN subject sub2 ON sub2.id = x2.subject_id
                    WHERE x.deleted_at IS NULL
                    ORDER BY x.exam_date""".formatted(PAPER)),

            // ── Missing data the setup checklist does not count ──────────────────────────────────────
            new Rule("STUDENTS_WITHOUT_ROLL_NUMBER", MISSING_DATA, "STUDENTS", INFO, "STUDENT_READ",
                    "%d section(s) have students without a roll number.", "PUT /api/v1/students/{studentId}/academic-assignments/{assignmentId}", """
                    SELECT s.id::text, c.name || ' ' || s.name || ' (' || count(*) || ')'
                    FROM student_academic_enrollment e
                    JOIN section s ON s.id = e.section_id
                    JOIN academic_class c ON c.id = s.academic_class_id
                    WHERE %s AND COALESCE(trim(e.roll_number), '') = ''
                    GROUP BY s.id, c.name, s.name, c.sort_order
                    ORDER BY c.sort_order, s.name""".formatted(LIVE_ENROLMENT)),

            // ── Pending approvals: work that waits for someone to review, publish or activate it ───────
            new Rule("LESSON_PLANS_TO_REVIEW", PENDING_APPROVALS, "ACADEMICS", WARNING, "LESSON_PLAN_READ",
                    "%d lesson plan(s) are submitted and waiting for review.", "POST /api/v1/academic-config/lesson-plans/{id}/review", """
                    SELECT lp.id::text, lp.title || ' – ' || %s || ' (' || lp.plan_date || ')'
                    FROM lesson_plan lp
                    JOIN staff st ON st.id = lp.teacher_id
                    LEFT JOIN tenant_user_profile p ON p.user_id = st.tenant_user_id
                    WHERE lp.academic_year_id = :y AND lp.deleted_at IS NULL AND lp.status = 'SUBMITTED'
                    ORDER BY lp.submitted_at NULLS LAST, lp.plan_date""".formatted(STAFF_NAME)),
            new Rule("CURRICULUM_DRAFT", PENDING_APPROVALS, "ACADEMICS", WARNING, "CURRICULUM_READ",
                    "%d curriculum(s) are still drafts.", "PATCH /api/v1/curricula/{id}/status", """
                    SELECT cu.id::text, cu.name
                    FROM curriculum cu
                    WHERE cu.academic_year_id = :y AND cu.deleted_at IS NULL AND cu.status = 'DRAFT'
                    ORDER BY cu.name"""),
            new Rule("SYLLABUS_DRAFT", PENDING_APPROVALS, "ACADEMICS", WARNING, "SYLLABUS_READ",
                    "%d syllabus(es) are written but not published; teachers do not follow them yet.", "PATCH /api/v1/academic-config/syllabi/{id}/status", """
                    SELECT sy.id::text, c.name || ' – ' || sub.name
                    FROM syllabus sy
                    JOIN academic_class c ON c.id = sy.academic_class_id
                    JOIN subject sub ON sub.id = sy.subject_id
                    WHERE sy.academic_year_id = :y AND sy.deleted_at IS NULL AND sy.status = 'DRAFT'
                    ORDER BY c.sort_order, sub.name"""),
            new Rule("TEACHER_ASSIGNMENT_DRAFT", PENDING_APPROVALS, "ACADEMICS", WARNING, "TEACHER_SUBJECT_ASSIGNMENT_READ",
                    "%d subject-teacher assignment(s) are drafts and not in effect.", "PATCH /api/v1/teacher-assignments/{id}/status", """
                    SELECT a.id::text, c.name || COALESCE(' ' || s.name, '') || ' – ' || sub.name || ' – ' || %s
                    FROM teacher_subject_assignment a
                    JOIN academic_class c ON c.id = a.academic_class_id
                    LEFT JOIN section s ON s.id = a.section_id
                    JOIN subject sub ON sub.id = a.subject_id
                    JOIN staff st ON st.id = a.teacher_id
                    LEFT JOIN tenant_user_profile p ON p.user_id = st.tenant_user_id
                    WHERE a.academic_year_id = :y AND a.deleted_at IS NULL AND a.status = 'DRAFT'
                    ORDER BY c.sort_order, sub.name""".formatted(STAFF_NAME)),
            new Rule("FEE_STRUCTURE_DRAFT", PENDING_APPROVALS, "FEES", WARNING, "FEE_READ",
                    "%d fee structure(s) are DRAFT; activate them once checked.", "PATCH /api/v1/operations-config/fees/structures/{id}/status", """
                    SELECT f.id::text, c.name || ' – ' || f.applies_to
                    FROM fee_structure f JOIN academic_class c ON c.id = f.academic_class_id
                    WHERE f.academic_year_id = :y AND f.deleted_at IS NULL AND f.status = 'DRAFT'
                    ORDER BY c.sort_order"""),
            new Rule("TIMETABLE_NOT_PUBLISHED", PENDING_APPROVALS, "TIMETABLE", WARNING, "TIMETABLE_READ",
                    "The timetable is still a draft (%d draft(s), none published).", "POST /api/v1/timetables/{id}/publish", """
                    SELECT t.id::text, t.name
                    FROM timetable t
                    WHERE t.academic_year_id = :y AND t.deleted_at IS NULL AND t.status = 'DRAFT'
                      AND NOT EXISTS (SELECT 1 FROM timetable l WHERE l.academic_year_id = :y AND l.deleted_at IS NULL
                                      AND l.status IN ('PUBLISHED', 'ACTIVE'))
                    ORDER BY t.updated_at DESC"""),
            new Rule("EXAM_SCHEDULE_DRAFT", PENDING_APPROVALS, "EXAMINATIONS", INFO, "EXAM_READ",
                    "%d exam schedule(s) are drafts; students and parents do not see them.", "POST /api/v1/exams/schedules/{id}/publish", """
                    SELECT es.id::text, t.name || ' – ' || c.name
                    FROM exam_schedule es
                    JOIN exam_term t ON t.id = es.exam_term_id AND t.deleted_at IS NULL
                    JOIN academic_class c ON c.id = es.academic_class_id
                    WHERE t.academic_year_id = :y AND es.deleted_at IS NULL AND es.status = 'DRAFT'
                    ORDER BY t.start_date, c.sort_order"""),
            new Rule("MARKS_NOT_LOCKED", PENDING_APPROVALS, "EXAMINATIONS", WARNING, "MARKS_READ",
                    "%d exam paper(s) have marks submitted but not locked; results cannot be finalised.", "POST /api/v1/exams/marks/subject-config/{configId}/lock", """
                    SELECT x.id::text, c.name || ' – ' || sub.name || ' (' || count(m.id) || ' student(s))'
                    %s
                    JOIN student_mark_entry m ON m.exam_subject_config_id = x.id AND m.deleted_at IS NULL AND m.status = 'SUBMITTED'
                    WHERE x.deleted_at IS NULL
                    GROUP BY x.id, c.name, sub.name, x.exam_date
                    ORDER BY x.exam_date""".formatted(PAPER)),
            new Rule("REPORT_CARDS_NOT_PUBLISHED", PENDING_APPROVALS, "EXAMINATIONS", INFO, "REPORT_CARD_READ",
                    "Report cards are generated but not published for %d class(es).", null, """
                    SELECT t.id::text || ':' || c.id::text, t.name || ' – ' || c.name || ' (' || count(*) || ')'
                    FROM student_report_card r
                    JOIN exam_term t ON t.id = r.exam_term_id
                    JOIN academic_class c ON c.id = r.academic_class_id
                    WHERE r.academic_year_id = :y AND r.deleted_at IS NULL AND r.status = 'GENERATED'
                    GROUP BY t.id, t.name, c.id, c.name, t.start_date, c.sort_order
                    ORDER BY t.start_date, c.sort_order""")
    );
}
