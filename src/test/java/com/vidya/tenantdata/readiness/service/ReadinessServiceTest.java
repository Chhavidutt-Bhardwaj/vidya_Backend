package com.vidya.tenantdata.readiness.service;

import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.tenantdata.academic.entity.AcademicYear;
import com.vidya.tenantdata.academic.enums.AcademicYearStatus;
import com.vidya.tenantdata.academic.repository.AcademicYearRepository;
import com.vidya.tenantdata.academic.setup.dto.SetupStatusResponse;
import com.vidya.tenantdata.academic.setup.dto.SetupStatusResponse.Level;
import com.vidya.tenantdata.academic.setup.dto.SetupStatusResponse.Step;
import com.vidya.tenantdata.academic.setup.service.AcademicSetupService;
import com.vidya.tenantdata.operations.dto.OperationsDtos.Area;
import com.vidya.tenantdata.operations.dto.OperationsDtos.Issue;
import com.vidya.tenantdata.operations.dto.OperationsDtos.OperationsOverview;
import com.vidya.tenantdata.operations.service.OperationsOverviewService;
import com.vidya.tenantdata.readiness.dto.ReadinessDtos.Category;
import com.vidya.tenantdata.readiness.dto.ReadinessDtos.Check;
import com.vidya.tenantdata.readiness.dto.ReadinessDtos.ReadinessReport;
import com.vidya.tenantdata.readiness.dto.ReadinessDtos.Severity;
import com.vidya.tenantdata.readiness.dto.ReadinessDtos.Verdict;
import com.vidya.tenantdata.timetable.dto.ScheduleConflictResponse;
import com.vidya.tenantdata.timetable.dto.ScheduleValidationResponse;
import com.vidya.tenantdata.timetable.enums.ConflictSeverity;
import com.vidya.tenantdata.timetable.enums.ScheduleConflictType;
import com.vidya.tenantdata.timetable.service.TimetableConflictValidator;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Validation & readiness of an academic year")
class ReadinessServiceTest {

    @Mock private AcademicYearRepository yearRepository;
    @Mock private AcademicSetupService setupService;
    @Mock private OperationsOverviewService operationsOverview;
    @Mock private TimetableConflictValidator validator;
    @Mock private EntityManager em;

    private ReadinessService service;
    private AcademicYear year;
    /** sql fragment → rows that query returns; anything else returns no rows */
    private final Map<String, List<Object[]>> results = new java.util.HashMap<>();

    @BeforeEach
    void setUp() {
        service = new ReadinessService(yearRepository, setupService, operationsOverview, validator);
        ReflectionTestUtils.setField(service, "em", em);
        year = new AcademicYear("AY26", "2026-27", LocalDate.of(2026, 4, 1), LocalDate.of(2027, 3, 31), false, AcademicYearStatus.DRAFT);
        year.setId(UUID.randomUUID());
        when(yearRepository.findByIdAndNotDeleted(year.getId())).thenReturn(Optional.of(year));
        when(setupService.status(year.getId())).thenReturn(status(List.of()));
        when(operationsOverview.overview(year.getId())).thenReturn(new OperationsOverview(year.getId(), year.getName(), List.of()));
        when(em.createNativeQuery(anyString())).thenAnswer(inv -> {
            String sql = inv.getArgument(0);
            Query q = mock(Query.class);
            when(q.setParameter(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(q);
            when(q.setMaxResults(anyInt())).thenReturn(q);
            List<Object[]> rows = results.entrySet().stream().filter(e -> sql.contains(e.getKey())).map(Map.Entry::getValue).findFirst()
                    .orElse(List.of());
            when(q.getResultList()).thenReturn(new ArrayList<>(rows));
            return q;
        });
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static void signIn(String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("admin", null, authorities));
    }

    private static final String[] ADMIN = {"ACADEMIC_YEAR_READ", "EXAM_READ", "CALENDAR_READ", "FEE_READ", "TIMETABLE_READ",
            "CLASS_TEACHER_ASSIGNMENT_READ", "STUDENT_READ", "LESSON_PLAN_READ", "CURRICULUM_READ", "SYLLABUS_READ",
            "TEACHER_SUBJECT_ASSIGNMENT_READ", "MARKS_READ", "REPORT_CARD_READ", "OPERATIONS_CONFIG_READ"};

    private SetupStatusResponse status(List<Step> steps) {
        return new SetupStatusResponse(year.getId(), "AY26", "2026-27", year.getStartDate(), year.getEndDate(), year.getStatus(), false,
                0, 0, 60, false, steps);
    }

    private static Step step(String key, Level level, boolean done, long pending) {
        return new Step(key, key.toLowerCase(), level, done, 0, pending, done ? "" : "do it", "POST /x");
    }

    private static Check find(ReadinessReport r, String code) {
        return r.categories().stream().flatMap(c -> c.checks().stream()).filter(c -> c.code().equals(code)).findFirst().orElse(null);
    }

    @Test
    @DisplayName("Nothing wrong and year already active → READY")
    void cleanYear() {
        signIn(ADMIN);
        year.setStatus(AcademicYearStatus.ACTIVE);
        ReadinessReport r = service.check(year.getId());
        assertThat(r.verdict()).isEqualTo(Verdict.READY);
        assertThat(r.readyToGoLive()).isTrue();
        assertThat(r.notChecked()).isEmpty();
        assertThat(r.categories()).extracting(c -> c.key()).containsExactly(Category.values());
        assertThat(r.percentComplete()).isEqualTo(60);
    }

    @Test
    @DisplayName("Unknown year → not found")
    void unknownYear() {
        signIn(ADMIN);
        UUID other = UUID.randomUUID();
        assertThatThrownBy(() -> service.check(other)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Draft year gets a YEAR_NOT_STARTED note but is not blocked by it")
    void draftYear() {
        signIn(ADMIN);
        ReadinessReport r = service.check(year.getId());
        assertThat(find(r, "YEAR_NOT_STARTED").severity()).isEqualTo(Severity.INFO);
        assertThat(r.verdict()).isEqualTo(Verdict.READY);
    }

    @Test
    @DisplayName("Checklist: REQUIRED step blocks, RECOMMENDED warns, OPTIONAL and lesson plans are left out")
    void checklist() {
        signIn(ADMIN);
        when(setupService.status(year.getId())).thenReturn(status(List.of(
                step("SECTIONS", Level.REQUIRED, false, 2),
                step("CLASS_TEACHERS", Level.RECOMMENDED, false, 3),
                step("FEES", Level.OPTIONAL, false, 1),
                step("LESSON_PLANS", Level.OPTIONAL, false, 4),
                step("SUBJECTS", Level.REQUIRED, true, 0))));
        ReadinessReport r = service.check(year.getId());
        assertThat(find(r, "MISSING_SECTIONS").severity()).isEqualTo(Severity.BLOCKER);
        assertThat(find(r, "MISSING_SECTIONS").count()).isEqualTo(2);
        assertThat(find(r, "MISSING_CLASS_TEACHERS").severity()).isEqualTo(Severity.WARNING);
        assertThat(find(r, "MISSING_FEES")).isNull();
        assertThat(find(r, "MISSING_LESSON_PLANS")).isNull();
        assertThat(find(r, "MISSING_SUBJECTS")).isNull();
        assertThat(r.verdict()).isEqualTo(Verdict.BLOCKED);
        assertThat(r.readyToGoLive()).isFalse();
        assertThat(r.summary().blockers()).isEqualTo(1);
    }

    @Test
    @DisplayName("A rule's rows become a check with count, five samples and the message filled in")
    void ruleRows() {
        signIn(ADMIN);
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            rows.add(new Object[]{"id" + i, "Fractions " + i});
        }
        results.put("lp.status = 'SUBMITTED'", rows);
        ReadinessReport r = service.check(year.getId());
        Check c = find(r, "LESSON_PLANS_TO_REVIEW");
        assertThat(c.category()).isEqualTo(Category.PENDING_APPROVALS);
        assertThat(c.count()).isEqualTo(7);
        assertThat(c.samples()).hasSize(5);
        assertThat(c.message()).startsWith("7 lesson plan(s)");
        assertThat(r.summary().pendingApprovals()).isEqualTo(1);
        assertThat(r.verdict()).isEqualTo(Verdict.READY_WITH_WARNINGS);
    }

    @Test
    @DisplayName("Rules the user may not read are skipped and their areas listed in notChecked")
    void permissions() {
        signIn("ACADEMIC_YEAR_READ");
        results.put("sum(i.percent) <> 100", List.<Object[]>of(new Object[]{"y", "Instalments add up to 90%"}));
        ReadinessReport r = service.check(year.getId());
        assertThat(find(r, "INSTALLMENTS_NOT_100")).isNull();
        assertThat(r.notChecked()).contains("FEES", "TIMETABLE", "EXAMINATIONS", "STUDENTS", "OPERATIONS");
        verify(operationsOverview, never()).overview(year.getId());
        verify(em, never()).createNativeQuery(argThat((String s) -> s.contains("fee_installment")));
    }

    @Test
    @DisplayName("An area checked by one rule is not reported as unchecked because another rule was skipped")
    void partlyChecked() {
        signIn("ACADEMIC_YEAR_READ", "EXAM_READ");
        ReadinessReport r = service.check(year.getId());
        assertThat(r.notChecked()).doesNotContain("EXAMINATIONS");
    }

    @Test
    @DisplayName("Operations: ERROR blocks, codes already covered are dropped, untouched areas become a note")
    void operations() {
        signIn(ADMIN);
        when(operationsOverview.overview(year.getId())).thenReturn(new OperationsOverview(year.getId(), year.getName(), List.of(
                new Area("TRANSPORT", "Transport", true, "IN_PROGRESS", Map.of(), List.of(
                        new Issue("VEHICLE_PAPERS_EXPIRED", "ERROR", "1 vehicle(s) have expired papers", "GET /v"),
                        new Issue("STOPS_WITHOUT_FEE", "INFO", "2 stop(s) have no fee", "PUT /r"))),
                new Area("FEES", "Fees", true, "IN_PROGRESS", Map.of(), List.of(
                        new Issue("FEE_STRUCTURE_DRAFT", "WARNING", "draft", "PATCH /f"))),
                new Area("LIBRARY", "Library", true, "NOT_STARTED", Map.of(), List.of(
                        new Issue("NO_BOOK_CATEGORIES", "WARNING", "none", "POST /l"))),
                new Area("COMMUNICATION", "Communication", false, "NOT_AVAILABLE", Map.of(), List.of()))));
        ReadinessReport r = service.check(year.getId());
        assertThat(find(r, "VEHICLE_PAPERS_EXPIRED").severity()).isEqualTo(Severity.BLOCKER);
        assertThat(find(r, "VEHICLE_PAPERS_EXPIRED").category()).isEqualTo(Category.CONFIGURATION);
        assertThat(find(r, "STOPS_WITHOUT_FEE").severity()).isEqualTo(Severity.INFO);
        assertThat(find(r, "FEE_STRUCTURE_DRAFT")).isNull();
        assertThat(find(r, "NO_BOOK_CATEGORIES")).isNull();
        assertThat(find(r, "LIBRARY_NOT_SET_UP").severity()).isEqualTo(Severity.INFO);
        assertThat(find(r, "LIBRARY_NOT_SET_UP").api()).isEqualTo("POST /l");
        assertThat(r.notChecked()).contains("COMMUNICATION");
        assertThat(r.verdict()).isEqualTo(Verdict.BLOCKED);
    }

    @Test
    @DisplayName("Timetable clashes block a live timetable and only warn on a draft")
    void timetableClashes() {
        signIn(ADMIN);
        UUID live = UUID.randomUUID();
        UUID draft = UUID.randomUUID();
        results.put("FROM timetable WHERE academic_year_id = :y AND deleted_at IS NULL AND status IN",
                List.<Object[]>of(new Object[]{live, "Main", "ACTIVE"}, new Object[]{draft.toString(), "Next", "DRAFT"}));
        ScheduleConflictResponse teacher = new ScheduleConflictResponse(ScheduleConflictType.TEACHER_CONFLICT, ConflictSeverity.ERROR,
                "Asha is in two places", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), DayOfWeek.MONDAY, null);
        ScheduleConflictResponse room = new ScheduleConflictResponse(ScheduleConflictType.ROOM_CONFLICT, ConflictSeverity.ERROR,
                "Lab double-booked", null, null, null, DayOfWeek.TUESDAY, null);
        when(validator.validateTimetable(live)).thenReturn(new ScheduleValidationResponse(false, 2, List.of(teacher, room), 40));
        when(validator.validateTimetable(eq(draft))).thenReturn(new ScheduleValidationResponse(false, 1, List.of(teacher), 10));

        ReadinessReport r = service.check(year.getId());
        List<Check> clashes = r.categories().stream().flatMap(c -> c.checks().stream()).filter(c -> c.code().equals("TIMETABLE_CLASHES")).toList();
        assertThat(clashes).hasSize(2);
        assertThat(clashes.get(0).severity()).isEqualTo(Severity.BLOCKER);
        assertThat(clashes.get(0).message()).contains("'Main'", "1 teacher conflict", "1 room conflict");
        assertThat(clashes.get(0).samples()).extracting(s -> s.id()).contains(live.toString());
        assertThat(clashes.get(1).severity()).isEqualTo(Severity.WARNING);
    }

    @Test
    @DisplayName("Validator warnings only: live timetable warns, draft is a note; no conflicts → no check")
    void timetableWarningsOnly() {
        UUID id = UUID.randomUUID();
        ScheduleConflictResponse w = new ScheduleConflictResponse(ScheduleConflictType.NON_WORKING_DAY, ConflictSeverity.WARNING,
                "Sunday period", UUID.randomUUID(), null, null, DayOfWeek.SUNDAY, null);
        ScheduleValidationResponse result = new ScheduleValidationResponse(true, 1, List.of(w), 5);
        assertThat(ReadinessService.timetableCheck(id, "Main", "PUBLISHED", result).severity()).isEqualTo(Severity.WARNING);
        assertThat(ReadinessService.timetableCheck(id, "Main", "DRAFT", result).severity()).isEqualTo(Severity.INFO);
        assertThat(ReadinessService.timetableCheck(id, "Main", "ACTIVE", new ScheduleValidationResponse(true, 0, List.of(), 5))).isNull();
    }

    @Test
    @DisplayName("Checks are ordered by category, then severity")
    void ordering() {
        signIn(ADMIN);
        results.put("lp.status = 'SUBMITTED'", List.<Object[]>of(new Object[]{"1", "plan"}));
        results.put("sum(i.percent) <> 100", List.<Object[]>of(new Object[]{"y", "90%"}));
        results.put("t.start_date < y.start_date", List.<Object[]>of(new Object[]{"t", "Term 2"}));
        when(setupService.status(year.getId())).thenReturn(status(List.of(step("STUDENTS", Level.REQUIRED, false, 0))));
        ReadinessReport r = service.check(year.getId());
        assertThat(r.categories().get(0).checks()).extracting(Check::code).containsExactly("INSTALLMENTS_NOT_100", "TERM_OUTSIDE_YEAR");
        assertThat(r.categories().get(0).blockers()).isEqualTo(1);
        assertThat(r.categories().get(2).checks()).extracting(Check::code).containsExactly("MISSING_STUDENTS");
        assertThat(find(r, "MISSING_STUDENTS").area()).isEqualTo("STUDENTS");
        assertThat(r.categories().get(3).checks()).extracting(Check::code).containsExactly("LESSON_PLANS_TO_REVIEW", "YEAR_NOT_STARTED");
    }
}
