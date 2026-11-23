package com.vidya.integration;

import com.vidya.tenantdata.dashboard.dto.DashboardDtos.AcademicCounts;
import com.vidya.tenantdata.dashboard.dto.DashboardDtos.ActionItemType;
import com.vidya.tenantdata.dashboard.dto.DashboardDtos.ClassEnrollment;
import com.vidya.tenantdata.dashboard.dto.DashboardDtos.StaffCounts;
import com.vidya.tenantdata.dashboard.dto.DashboardDtos.StudentCounts;
import com.vidya.tenantdata.dashboard.repository.DashboardQueries;
import com.vidya.tenantdata.dashboard.repository.DashboardQueries.NextExam;
import com.vidya.tenantdata.dashboard.repository.DashboardQueries.PendingRow;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs every dashboard query against the real tenant schema (all Flyway migrations). The container is shared with other
 * integration tests, so data is isolated by a fresh academic year and by dates in 2091 that no other test uses.
 */
@DisplayName("Dashboard queries on PostgreSQL")
class DashboardQueriesPostgresIntegrationTest extends AbstractTenantPostgresIntegrationTest {

    private static final LocalDate DAY = LocalDate.of(2091, 6, 12);

    private static EntityManagerFactory emf;
    private static EntityManager em;
    private static DashboardQueries queries;

    private static UUID year;
    private static UUID previousYear;
    private static UUID class6;
    private static UUID class7;
    private static UUID sectionA;
    private static UUID sectionB;
    private static UUID subject;
    private static UUID teacher;
    private static UUID lessonPlan;
    private static UUID paper;
    private static final UUID USER = UUID.randomUUID();
    private static final String S = UUID.randomUUID().toString().substring(0, 8);

    @BeforeAll
    static void init() throws Exception {
        LocalContainerEntityManagerFactoryBean emfb = new LocalContainerEntityManagerFactoryBean();
        emfb.setDataSource(dataSource);
        emfb.setPackagesToScan("com.vidya.tenantdata", "com.vidya.master");
        emfb.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        Properties props = new Properties();
        props.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        emfb.setJpaProperties(props);
        emfb.afterPropertiesSet();
        emf = emfb.getObject();
        em = emf.createEntityManager();

        queries = new DashboardQueries();
        Field f = DashboardQueries.class.getDeclaredField("em");
        f.setAccessible(true);
        f.set(queries, em);

        seed();
    }

    @AfterAll
    static void close() {
        if (em != null && em.isOpen()) em.close();
        if (emf != null && emf.isOpen()) emf.close();
    }

    private static void seed() {
        previousYear = id();
        jdbcTemplate.update("INSERT INTO academic_year (id, code, name, start_date, end_date, is_current, status) VALUES (?, ?, ?, ?, ?, false, 'CLOSED')",
                previousYear, "AY-P-" + S, "Prev " + S, LocalDate.of(2090, 4, 1), LocalDate.of(2091, 3, 31));
        year = id();
        jdbcTemplate.update("INSERT INTO academic_year (id, code, name, start_date, end_date, is_current, status) VALUES (?, ?, ?, ?, ?, false, 'ACTIVE')",
                year, "AY-D-" + S, "Dash " + S, LocalDate.of(2091, 4, 1), LocalDate.of(2092, 3, 31));

        class6 = id();
        class7 = id();
        jdbcTemplate.update("INSERT INTO academic_class (id, code, name, sort_order) VALUES (?, ?, ?, 6), (?, ?, ?, 7)",
                class6, "C6-" + S, "Class 6 " + S, class7, "C7-" + S, "Class 7 " + S);
        sectionA = id();
        sectionB = id();
        UUID sectionC = id();
        jdbcTemplate.update("INSERT INTO section (id, academic_year_id, academic_class_id, code, name, capacity) VALUES "
                        + "(?, ?, ?, ?, 'A', 3), (?, ?, ?, ?, 'B', 2), (?, ?, ?, ?, 'A', NULL)",
                sectionA, year, class6, "A-" + S, sectionB, year, class6, "B-" + S, sectionC, year, class7, "C-" + S);

        // 4 live students in class 6 (3 in A, 1 in B), 1 withdrawn; 2 students enrolled last year (promoted)
        UUID[] students = new UUID[5];
        String[] genders = {"MALE", "FEMALE", "F", null, "MALE"};
        for (int i = 0; i < 5; i++) {
            students[i] = student("st" + i, genders[i], DAY.minusDays(i));
        }
        enroll(students[0], year, class6, sectionA, "ACTIVE");
        enroll(students[1], year, class6, sectionA, "ACTIVE");
        enroll(students[2], year, class6, sectionA, "ADMITTED");
        enroll(students[3], year, class6, sectionB, "ACTIVE");
        enroll(students[4], year, class6, sectionB, "WITHDRAWN");
        enroll(students[0], previousYear, class6, null, "PROMOTED");
        enroll(students[1], previousYear, class6, null, "PROMOTED");
        jdbcTemplate.update("INSERT INTO student_guardian (student_id, guardian_code, relationship_type, guardian_name, status) VALUES (?, ?, 'FATHER', 'Dad', 'ACTIVE')",
                students[0], "G-" + S);

        // Attendance on DAY in section A only: PRESENT, LATE, ABSENT; and one PRESENT the day before
        mark(students[0], sectionA, DAY, "PRESENT");
        mark(students[1], sectionA, DAY, "LATE");
        mark(students[2], sectionA, DAY, "ABSENT");
        mark(students[0], sectionA, DAY.minusDays(1), "PRESENT");

        subject = id();
        jdbcTemplate.update("INSERT INTO subject (id, subject_code, name, subject_type) VALUES (?, ?, 'Maths', 'CORE')", subject, "SUB-" + S);
        UUID tu = id();
        jdbcTemplate.update("INSERT INTO tenant_user (id, user_code, email) VALUES (?, ?, ?)", tu, "TU-T-" + S, "t-" + S + "@x.test");
        jdbcTemplate.update("INSERT INTO tenant_user_profile (user_id, first_name, last_name) VALUES (?, 'Asha', 'Rao')", tu);
        teacher = id();
        jdbcTemplate.update("INSERT INTO staff (id, staff_code, tenant_user_id, staff_type) VALUES (?, ?, ?, 'TEACHER')", teacher, "STF-" + S, tu);

        lessonPlan = id();
        jdbcTemplate.update("INSERT INTO lesson_plan (id, plan_code, academic_year_id, academic_class_id, section_id, subject_id, teacher_id, title, plan_date, status, submitted_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, 'Fractions', ?, 'SUBMITTED', now())", lessonPlan, "LP-" + S, year, class6, sectionA, subject, teacher, DAY);

        UUID term = id();
        jdbcTemplate.update("INSERT INTO exam_term (id, term_code, academic_year_id, name, start_date, end_date) VALUES (?, ?, ?, 'Term 1', ?, ?)",
                term, "T-" + S, year, DAY, DAY.plusDays(30));
        UUID schedule = id();
        jdbcTemplate.update("INSERT INTO exam_schedule (id, schedule_code, exam_term_id, academic_class_id, name, status) VALUES (?, ?, ?, ?, 'Mid-term', 'PUBLISHED')",
                schedule, "ES-" + S, term, class6);
        paper = id();
        jdbcTemplate.update("INSERT INTO exam_subject_config (id, config_code, exam_schedule_id, subject_id, exam_date, start_time, end_time, max_marks, pass_marks) "
                + "VALUES (?, ?, ?, ?, ?, '09:00', '12:00', 100, 33)", paper, "X-" + S, schedule, subject, DAY.plusDays(3));

        jdbcTemplate.update("INSERT INTO school_holiday (holiday_code, name, holiday_type, start_date, end_date, academic_year_id) VALUES (?, 'Founders Day', 'SCHOOL_HOLIDAY', ?, ?, ?)",
                "H-" + S, DAY.plusDays(10), DAY.plusDays(10), year);
    }

    @Test
    @DisplayName("enrolment counts only live students, capacity sums sections, previous year counts promoted students")
    void enrollment() {
        assertThat(queries.liveEnrollment(year)).isEqualTo(4);
        assertThat(queries.sectionCapacity(year)).isEqualTo(5);
        assertThat(queries.previousYearId(LocalDate.of(2091, 4, 1))).contains(previousYear);
        assertThat(queries.pastEnrollment(previousYear)).isEqualTo(2);
        assertThat(queries.admittedBetween(DAY.minusDays(1), DAY)).isEqualTo(2);
    }

    @Test
    @DisplayName("student snapshot splits the live roll by status, gender and guardian")
    void studentCounts() {
        StudentCounts c = queries.studentCounts(year);
        assertThat(c.enrolled()).isEqualTo(4);
        assertThat(c.admitted()).isEqualTo(1);
        assertThat(c.active()).isEqualTo(3);
        assertThat(c.withoutSection()).isZero();
        assertThat(c.boys()).isEqualTo(1);
        assertThat(c.girls()).isEqualTo(2);
        assertThat(c.otherOrUnknown()).isEqualTo(1);
        assertThat(c.guardiansLinked()).isEqualTo(1);
    }

    @Test
    @DisplayName("enrolment by class lists classes with sections, with occupancy")
    void byClass() {
        List<ClassEnrollment> rows = queries.enrollmentByClass(year);
        assertThat(rows).extracting(ClassEnrollment::classId).containsExactly(class6, class7);
        ClassEnrollment six = rows.get(0);
        assertThat(six.students()).isEqualTo(4);
        assertThat(six.capacity()).isEqualTo(5);
        assertThat(six.occupancyPercent()).isEqualByComparingTo("80.0");
        assertThat(six.sections()).isEqualTo(2);
        assertThat(rows.get(1).students()).isZero();
        assertThat(rows.get(1).capacity()).isNull();
    }

    @Test
    @DisplayName("attendance counts by status and day; sections marked out of sections with students")
    void attendance() {
        assertThat(queries.attendanceByStatus(DAY)).containsOnly(Map.entry("PRESENT", 1L), Map.entry("LATE", 1L), Map.entry("ABSENT", 1L));
        Map<LocalDate, Map<String, Long>> byDay = queries.attendanceByDay(DAY.minusDays(5), DAY);
        assertThat(byDay).containsOnlyKeys(DAY.minusDays(1), DAY);
        assertThat(queries.sectionsMarked(year, DAY)).containsExactly(2, 1);
    }

    @Test
    @DisplayName("academic counts and staff counts")
    void academicsAndStaff() {
        AcademicCounts a = queries.academicCounts(year);
        assertThat(a.classes()).isEqualTo(2);
        assertThat(a.sections()).isEqualTo(3);
        assertThat(a.sectionsWithoutClassTeacher()).isEqualTo(3);
        assertThat(a.timetableStatus()).isEqualTo("NONE");
        StaffCounts s = queries.staffCounts();
        assertThat(s.teaching()).isGreaterThanOrEqualTo(1);
        assertThat(s.active()).isEqualTo(s.teaching() + s.nonTeaching());
    }

    @Test
    @DisplayName("operations: substitutions, next exam, holidays, transport and fee structures")
    void operations() {
        assertThat(queries.substitutionsByType(DAY)).isEmpty(); // a substitution needs a full timetable; the SQL is what is checked here
        NextExam next = queries.nextExam(year, DAY).orElseThrow();
        assertThat(next.examDate()).isEqualTo(DAY.plusDays(3));
        assertThat(next.subjectName()).isEqualTo("Maths");
        assertThat(next.papersNext7Days()).isEqualTo(1);
        assertThat(queries.nextExam(year, DAY.plusDays(4))).isEmpty();
        assertThat(queries.upcomingHolidays(DAY)).isEqualTo(1);
        assertThat(queries.transport(DAY).activeVehicles()).isGreaterThanOrEqualTo(0);
        assertThat(queries.feeStructures(year)).containsExactly(0, 0);
    }

    @Test
    @DisplayName("every action item query runs; a dismissed lesson plan disappears for that user only")
    void actionItemsAndDismissal() {
        for (ActionItemType type : ActionItemType.values()) {
            assertThat(queries.pending(type, year, USER)).isNotNull();
        }
        List<PendingRow> plans = queries.pending(ActionItemType.LESSON_PLAN_REVIEW, year, USER);
        assertThat(plans).singleElement().satisfies(r -> {
            assertThat(r.key()).isEqualTo(lessonPlan.toString());
            assertThat(r.raisedBy()).isEqualTo("Asha Rao");
            assertThat(r.description()).contains("Maths");
        });

        em.getTransaction().begin();
        assertThat(queries.dismiss(USER, ActionItemType.LESSON_PLAN_REVIEW, lessonPlan.toString())).isTrue();
        assertThat(queries.dismiss(USER, ActionItemType.LESSON_PLAN_REVIEW, lessonPlan.toString())).isFalse();
        em.getTransaction().commit();

        assertThat(queries.pending(ActionItemType.LESSON_PLAN_REVIEW, year, USER)).isEmpty();
        assertThat(queries.pending(ActionItemType.LESSON_PLAN_REVIEW, year, UUID.randomUUID())).hasSize(1);
    }

    @Test
    @DisplayName("submitted marks show up as a paper to lock")
    void marksToLock() {
        UUID student = jdbcTemplate.queryForObject("SELECT student_id FROM student_academic_enrollment WHERE academic_year_id = ? AND enrollment_status = 'ACTIVE' LIMIT 1",
                UUID.class, year);
        jdbcTemplate.update("INSERT INTO student_mark_entry (entry_code, exam_subject_config_id, student_id, marks_obtained, status) VALUES (?, ?, ?, 70, 'SUBMITTED')",
                "M-" + S, paper, student);
        List<PendingRow> rows = queries.pending(ActionItemType.MARKS_LOCK, year, USER);
        assertThat(rows).singleElement().satisfies(r -> {
            assertThat(r.key()).isEqualTo(paper.toString());
            assertThat(r.description()).contains("1 student(s) submitted");
        });
    }

    @Test
    @DisplayName("auto-debit totals of the month; refused debit, money to refund and a stalled mandate show up as action items")
    void onlineFees() {
        UUID student = jdbcTemplate.queryForObject("SELECT student_id FROM student_academic_enrollment WHERE academic_year_id = ? AND enrollment_status = 'ACTIVE' LIMIT 1",
                UUID.class, year);
        UUID ledger = id();
        jdbcTemplate.update("INSERT INTO fee_ledger (id, invoice_number, student_id, academic_year_id, academic_class_id) VALUES (?, ?, ?, ?, ?)",
                ledger, "INV-D" + S, student, year, class6);
        UUID mandate = id();
        jdbcTemplate.update("INSERT INTO fee_mandate (id, reference, provider, method, status, payer_name, payer_phone, max_amount_paise, expires_on, created_at) "
                + "VALUES (?, ?, 'RAZORPAY', 'ENACH', 'PENDING', 'Payer D', '9800000000', 1000000, ?, now() - interval '10 days')", mandate, "MD-D" + S, DAY.plusYears(10));
        jdbcTemplate.update("INSERT INTO fee_mandate_student (mandate_id, student_id) VALUES (?, ?)", mandate, student);
        LocalDate cycle = DAY.withDayOfMonth(1);
        debit(mandate, student, ledger, cycle, 1, "SUCCESS", 300_000, 0);
        debit(mandate, student, ledger, cycle, 2, "FAILED", 200_000, 59_000);
        UUID refused = debit(mandate, student, ledger, cycle, 3, "ERROR", 100_000, 0);
        debit(mandate, student, ledger, cycle.minusMonths(1), 1, "SCHEDULED", 999_000, 0);
        UUID checkout = id();
        jdbcTemplate.update("INSERT INTO fee_checkout (id, reference, kind, provider, status, amount_paise, paid_paise, unallocated_paise, payer_name, "
                + "payer_phone, purpose) VALUES (?, ?, 'LINK', 'CASHFREE', 'PAID', 100000, 105000, 5000, 'Payer D', '9800000000', 'Fees')", checkout, "PL-D" + S);
        jdbcTemplate.update("INSERT INTO fee_checkout_student (checkout_id, student_id, ledger_id, sibling_rank, due_paise, paid_paise) VALUES (?, ?, ?, 0, 100000, 100000)",
                checkout, student, ledger);

        var a = queries.autoDebit(cycle, "/x");
        assertThat(a.debitDate().getMonth()).isEqualTo(DAY.getMonth());
        assertThat(a.collected()).isEqualTo(1);
        assertThat(a.collectedPaise()).isEqualTo(300_000);
        assertThat(a.bounced()).isEqualTo(1);
        assertThat(a.bouncedPaise()).isEqualTo(200_000);
        assertThat(a.bounceChargesPaise()).isEqualTo(59_000);
        assertThat(a.errors()).isEqualTo(1);
        assertThat(a.scheduled()).isZero();
        assertThat(a.pendingMandates()).isGreaterThanOrEqualTo(1);

        assertThat(queries.pending(ActionItemType.FEE_DEBIT_ERROR, year, USER)).singleElement().satisfies(r -> {
            assertThat(r.key()).isEqualTo(refused.toString());
            assertThat(r.description()).contains("₹1000.00").contains("Payer D");
        });
        assertThat(queries.pending(ActionItemType.FEE_UNALLOCATED, year, USER)).singleElement().satisfies(r -> {
            assertThat(r.key()).isEqualTo(checkout.toString());
            assertThat(r.title()).contains("₹50.00");
        });
        assertThat(queries.pending(ActionItemType.FEE_MANDATE_STALLED, year, USER)).anySatisfy(r -> {
            assertThat(r.key()).isEqualTo(mandate.toString());
            assertThat(r.description()).contains("1 child(ren)");
        });

        em.getTransaction().begin();
        assertThat(queries.dismiss(USER, ActionItemType.FEE_UNALLOCATED, checkout.toString())).isTrue();
        em.getTransaction().commit();
        assertThat(queries.pending(ActionItemType.FEE_UNALLOCATED, year, USER)).isEmpty();
    }

    private static UUID debit(UUID mandate, UUID student, UUID ledger, LocalDate cycle, int attempt, String status, long amount, long bounce) {
        UUID d = id();
        jdbcTemplate.update("INSERT INTO fee_debit (id, reference, mandate_id, student_id, ledger_id, cycle_month, attempt_no, scheduled_on, amount_paise, status, "
                + "bounce_charge_paise, bounce_gst_paise) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)", d, "DB-" + UUID.randomUUID(), mandate, student, ledger, cycle,
                attempt, cycle.plusDays(9), amount, status, bounce);
        return d;
    }

    // ── seed helpers ─────────────────────────────────────────────────────────────────

    private static UUID id() {
        return UUID.randomUUID();
    }

    private static UUID student(String tag, String gender, LocalDate admitted) {
        UUID tu = id();
        jdbcTemplate.update("INSERT INTO tenant_user (id, user_code, email, user_type) VALUES (?, ?, ?, 'STUDENT')", tu, "TU-" + tag + S, tag + S + "@x.test");
        jdbcTemplate.update("INSERT INTO tenant_user_profile (user_id, first_name, gender) VALUES (?, ?, ?)", tu, tag, gender);
        UUID st = id();
        jdbcTemplate.update("INSERT INTO student (id, student_code, tenant_user_id, admission_number, admission_date) VALUES (?, ?, ?, ?, ?)",
                st, "STU-" + tag + S, tu, "ADM-" + tag + S, admitted);
        return st;
    }

    private static void enroll(UUID student, UUID y, UUID cls, UUID section, String status) {
        jdbcTemplate.update("INSERT INTO student_academic_enrollment (enrollment_code, student_id, academic_year_id, academic_class_id, section_id, enrollment_status) "
                + "VALUES (?, ?, ?, ?, ?, ?)", "E-" + UUID.randomUUID(), student, y, cls, section, status);
    }

    private static void mark(UUID student, UUID section, LocalDate date, String status) {
        jdbcTemplate.update("INSERT INTO student_attendance (attendance_code, student_id, academic_year_id, academic_class_id, section_id, attendance_date, status) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)", "A-" + UUID.randomUUID(), student, year, class6, section, date, status);
    }
}
