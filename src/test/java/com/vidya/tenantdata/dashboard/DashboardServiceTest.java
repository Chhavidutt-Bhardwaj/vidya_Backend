package com.vidya.tenantdata.dashboard;

import com.vidya.common.exception.BusinessException;
import com.vidya.tenantdata.academic.entity.AcademicYear;
import com.vidya.tenantdata.academic.repository.AcademicYearRepository;
import com.vidya.tenantdata.calendar.service.CalendarService;
import com.vidya.tenantdata.dashboard.dto.DashboardDtos.ActionItem;
import com.vidya.tenantdata.dashboard.dto.DashboardDtos.ActionItemType;
import com.vidya.tenantdata.dashboard.dto.DashboardDtos.Kpis;
import com.vidya.tenantdata.dashboard.dto.DashboardDtos.QuickCounts;
import com.vidya.tenantdata.dashboard.dto.DashboardDtos.Unavailable;
import com.vidya.tenantdata.dashboard.dto.DashboardDtos.UnavailableReason;
import com.vidya.tenantdata.dashboard.repository.DashboardQueries;
import com.vidya.tenantdata.dashboard.repository.DashboardQueries.PendingRow;
import com.vidya.tenantdata.dashboard.service.DashboardService;
import com.vidya.tenantdata.fee.dto.FeeDtos.DashboardFees;
import com.vidya.tenantdata.fee.service.FeeReportService;
import com.vidya.tenantdata.operations.enums.LateCountsAs;
import com.vidya.tenantdata.operations.service.AttendancePolicyService;
import com.vidya.tenantdata.operations.service.AttendancePolicyService.Policy;
import com.vidya.tenantdata.security.DataAccessService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Dashboard service")
class DashboardServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 21);
    private static final UUID USER = UUID.randomUUID();
    private static final UUID YEAR = UUID.randomUUID();
    private static final UUID PREVIOUS = UUID.randomUUID();

    @Mock private DashboardQueries queries;
    @Mock private AcademicYearRepository years;
    @Mock private CalendarService calendar;
    @Mock private AttendancePolicyService policy;
    @Mock private DataAccessService dataAccess;
    @Mock private FeeReportService feeReports;

    private DashboardService service;
    private AcademicYear year;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(TODAY.atStartOfDay(ZoneId.of("Asia/Kolkata")).toInstant(), ZoneId.of("Asia/Kolkata"));
        service = new DashboardService(queries, years, calendar, policy, dataAccess, clock, feeReports);
        year = mock(AcademicYear.class);
        when(year.getId()).thenReturn(YEAR);
        when(year.getName()).thenReturn("2026-27");
        when(year.getStartDate()).thenReturn(LocalDate.of(2026, 4, 1));
        when(year.getEndDate()).thenReturn(LocalDate.of(2027, 3, 31));
        when(years.findCurrentYear()).thenReturn(Optional.of(year));
        when(policy.policy()).thenReturn(new Policy(null, LateCountsAs.ABSENT, null));
        when(calendar.isWorkingDay(any())).thenReturn(true);
        when(queries.sectionsMarked(any(), any())).thenReturn(new long[]{10, 7});
        when(queries.feeStructures(any())).thenReturn(new long[]{3, 1});
        when(feeReports.dashboard(any(), any(), any())).thenReturn(new DashboardFees(50_000, 100_000, new BigDecimal("62.5"), 40_000, 30_000, 4, 120,
                5_000, 2, 1));
        when(queries.attendanceByStatus(any())).thenReturn(Map.of());
        when(queries.attendanceByDay(any(), any())).thenReturn(new TreeMap<>());
        when(queries.autoDebit(any(), any())).thenReturn(new com.vidya.tenantdata.dashboard.dto.DashboardDtos.AutoDebit(false, TODAY.withDayOfMonth(1),
                TODAY.withDayOfMonth(10), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, "/fees?tab=auto-debit"));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void signIn(boolean fullScope, String... authorities) {
        var granted = Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(USER, null, granted));
        when(dataAccess.hasFullScope(any(), anyString())).thenReturn(fullScope);
    }

    private static Set<String> blocks(List<Unavailable> unavailable, UnavailableReason reason) {
        return unavailable.stream().filter(u -> u.reason() == reason).map(Unavailable::block).collect(java.util.stream.Collectors.toSet());
    }

    @Nested
    @DisplayName("access")
    class Access {

        @Test
        @DisplayName("DASHBOARD_READ alone shows no numbers and runs no module query")
        void dashboardOnly() {
            signIn(true, "DASHBOARD_READ");
            Kpis k = service.kpis(null, null, null);
            assertThat(k.enrollment()).isNull();
            assertThat(k.attendance()).isNull();
            assertThat(k.admissions()).isNull();
            assertThat(k.fees()).isNull();
            assertThat(blocks(k.unavailable(), UnavailableReason.NO_PERMISSION)).containsExactlyInAnyOrder("enrollment", "attendance", "admissions", "fees");
            verify(queries, never()).liveEnrollment(any());
            verify(queries, never()).attendanceByStatus(any());
        }

        @Test
        @DisplayName("a read permission without school-wide scope is not enough (teacher with OWN scope)")
        void ownScope() {
            signIn(false, "DASHBOARD_READ", "STUDENT_READ", "ATTENDANCE_READ");
            Kpis k = service.kpis(null, null, null);
            assertThat(k.enrollment()).isNull();
            assertThat(k.attendance()).isNull();
            verify(queries, never()).liveEnrollment(any());
        }

        @Test
        @DisplayName("no academic year: enrolment reports NO_ACADEMIC_YEAR instead of failing")
        void noYear() {
            signIn(true, "DASHBOARD_READ", "STUDENT_READ");
            when(years.findCurrentYear()).thenReturn(Optional.empty());
            Kpis k = service.kpis(null, null, null);
            assertThat(k.enrollment()).isNull();
            assertThat(blocks(k.unavailable(), UnavailableReason.NO_ACADEMIC_YEAR)).contains("enrollment");
            assertThat(k.window().academicYearId()).isNull();
        }
    }

    @Nested
    @DisplayName("KPIs")
    class KpiValues {

        @Test
        @DisplayName("enrolment: capacity, occupancy and change against last year")
        void enrollment() {
            signIn(true, "DASHBOARD_READ", "STUDENT_READ");
            when(queries.liveEnrollment(YEAR)).thenReturn(420L);
            when(queries.sectionCapacity(YEAR)).thenReturn(480L);
            when(queries.previousYearId(LocalDate.of(2026, 4, 1))).thenReturn(Optional.of(PREVIOUS));
            when(queries.pastEnrollment(PREVIOUS)).thenReturn(400L);
            var e = service.kpis(null, null, null).enrollment();
            assertThat(e.value()).isEqualTo(420);
            assertThat(e.occupancyPercent()).isEqualByComparingTo("87.5");
            assertThat(e.delta()).isEqualByComparingTo("5.0");
        }

        @Test
        @DisplayName("window defaults to month to date; admissions compare with the window before")
        void admissionsWindow() {
            signIn(true, "DASHBOARD_READ", "STUDENT_READ");
            when(queries.admittedBetween(LocalDate.of(2026, 11, 1), TODAY)).thenReturn(30L);
            when(queries.admittedBetween(LocalDate.of(2026, 10, 11), LocalDate.of(2026, 10, 31))).thenReturn(20L);
            Kpis k = service.kpis(null, null, null);
            assertThat(k.window().from()).isEqualTo(LocalDate.of(2026, 11, 1));
            assertThat(k.admissions().admittedInWindow()).isEqualTo(30);
            assertThat(k.admissions().delta()).isEqualByComparingTo("50.0");
            assertThat(k.admissions().activeLeads()).isNull();
            assertThat(blocks(k.unavailable(), UnavailableReason.NOT_BUILT)).contains("admissions.leads");
        }

        @Test
        @DisplayName("attendance: today's rate against the 30-day average, sections marked")
        void attendance() {
            signIn(true, "DASHBOARD_READ", "ATTENDANCE_READ");
            when(queries.attendanceByStatus(TODAY)).thenReturn(Map.of("PRESENT", 9L, "ABSENT", 1L));
            TreeMap<LocalDate, Map<String, Long>> before = new TreeMap<>();
            before.put(TODAY.minusDays(2), Map.of("PRESENT", 8L, "ABSENT", 2L));
            before.put(TODAY.minusDays(1), Map.of("PRESENT", 10L));
            when(queries.attendanceByDay(TODAY.minusDays(30), TODAY.minusDays(1))).thenReturn(before);
            var a = service.kpis(null, null, null).attendance();
            assertThat(a.presentRate()).isEqualByComparingTo("90.0");
            assertThat(a.delta()).isEqualByComparingTo("0.0");
            assertThat(a.unexcusedAbsences()).isEqualTo(1);
            assertThat(a.sectionsMarked()).isEqualTo(7);
            assertThat(a.sectionsTotal()).isEqualTo(10);
        }

        @Test
        @DisplayName("fees show collection in the window, the rate and the change against the previous window")
        void fees() {
            signIn(true, "DASHBOARD_READ", "FEE_READ");
            Kpis k = service.kpis(null, null, null);
            assertThat(k.fees().collectionAvailable()).isTrue();
            assertThat(k.fees().collectedPaise()).isEqualTo(50_000);
            assertThat(k.fees().targetPaise()).isEqualTo(100_000);
            assertThat(k.fees().collectionRate()).isEqualByComparingTo("62.5");
            assertThat(k.fees().delta()).isEqualByComparingTo("25.0");
            assertThat(k.fees().overdueLedgers()).isEqualTo(4);
            assertThat(k.fees().activeFeeStructures()).isEqualTo(3);
            assertThat(blocks(k.unavailable(), UnavailableReason.NOT_BUILT)).doesNotContain("fees.collection");
        }

        @Test
        @DisplayName("fee concessions are action items only for approvers with school-wide fee scope")
        void feeConcessionActionItems() {
            when(queries.pending(eq(ActionItemType.FEE_CONCESSION), any(), any())).thenReturn(List.of(new PendingRow(ActionItemType.FEE_CONCESSION,
                    UUID.randomUUID().toString(), "Concession to approve: Ravi", "Sibling · 10%", "Accounts", Instant.parse("2026-08-01T00:00:00Z"))));
            signIn(true, "DASHBOARD_READ", "FEE_APPROVE");
            List<ActionItem> items = service.actionItems(null, ActionItemType.FEE_CONCESSION, 0, 20).getContent();
            assertThat(items).singleElement().satisfies(i -> assertThat(i.api()).startsWith("POST /api/v1/fees/concession-requests/"));
            signIn(false, "DASHBOARD_READ", "FEE_APPROVE");
            assertThat(service.actionItems(null, ActionItemType.FEE_CONCESSION, 0, 20).getContent()).isEmpty();
        }

        @Test
        @DisplayName("auto-debit tile and block for fee readers; refused debits, refunds and stalled mandates for fee managers")
        void onlineFees() {
            var a = new com.vidya.tenantdata.dashboard.dto.DashboardDtos.AutoDebit(true, TODAY.withDayOfMonth(1), TODAY.withDayOfMonth(10), 40, 3,
                    0, 0, 0, 0, 35, 9_000_000, 2, 400_000, 118_000, 1, 1, "/fees?tab=auto-debit&month=2026-11");
            when(queries.autoDebit(any(), any())).thenReturn(a);
            UUID debit = UUID.randomUUID();
            when(queries.pending(eq(ActionItemType.FEE_DEBIT_ERROR), any(), any())).thenReturn(List.of(new PendingRow(ActionItemType.FEE_DEBIT_ERROR,
                    debit.toString(), "Auto-debit refused by the gateway: Ravi", "₹2000.00", null, Instant.parse("2026-11-10T00:00:00Z"))));

            signIn(true, "DASHBOARD_READ", "FEE_READ");
            var home = service.home(null, null, null);
            assertThat(home.autoDebit()).isEqualTo(a);
            assertThat(home.operationsPulse()).anySatisfy(t -> {
                assertThat(t.key()).isEqualTo("AUTO_DEBIT");
                assertThat(t.status()).isEqualTo("ATTENTION");
                assertThat(t.count()).isEqualTo(9_000_000);
            });
            assertThat(service.actionItems(null, ActionItemType.FEE_DEBIT_ERROR, 0, 20).getContent()).isEmpty();

            signIn(true, "DASHBOARD_READ", "FEE_READ", "FEE_MANAGE");
            assertThat(service.actionItems(null, ActionItemType.FEE_DEBIT_ERROR, 0, 20).getContent()).singleElement()
                    .satisfies(i -> assertThat(i.id()).isEqualTo("FEE_DEBIT_ERROR:" + debit));
            assertThat(service.dismiss("FEE_MANDATE_STALLED:" + debit).dismissed()).isTrue();

            signIn(true, "DASHBOARD_READ");
            assertThat(service.home(null, null, null).autoDebit()).isNull();
            verify(queries, org.mockito.Mockito.times(2)).autoDebit(any(), any());
        }

        @Test
        @DisplayName("export: CSV of the visible blocks, formulas neutralised, commas quoted")
        void export() {
            signIn(true, "DASHBOARD_READ", "FEE_READ");
            String csv = service.exportCsv(null, null, null);
            assertThat(csv).startsWith("section,metric,value\n").contains("fees,collected (₹),500.00").contains("approvals,pending,")
                    .doesNotContain("enrolment,active students");
            assertThat(csv).contains("not available,enrollment,");
            assertThat(DashboardService.csv("=SUM(A1)")).isEqualTo("'=SUM(A1)");
            assertThat(DashboardService.csv("-12.5")).isEqualTo("-12.5");
            assertThat(DashboardService.csv("a, \"b\"")).isEqualTo("\"a, \"\"b\"\"\"");
        }

        @Test
        @DisplayName("from after to is rejected")
        void badRange() {
            signIn(true, "DASHBOARD_READ");
            assertThatThrownBy(() -> service.kpis(null, TODAY, TODAY.minusDays(1))).isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    @DisplayName("attendance formula")
    class Formula {

        @Test
        @DisplayName("late counts by the school's weight, half day as half, excused as present")
        void rate() throws Exception {
            var rate = DashboardService.class.getDeclaredMethod("rate", Map.class, java.math.BigDecimal.class);
            rate.setAccessible(true);
            Map<String, Long> day = Map.of("PRESENT", 1L, "LATE", 1L, "ABSENT", 1L, "HALF_DAY", 1L, "EXCUSED", 1L);
            assertThat(rate.invoke(null, day, java.math.BigDecimal.ZERO)).isEqualTo(new java.math.BigDecimal("50.0"));
            assertThat(rate.invoke(null, day, java.math.BigDecimal.ONE)).isEqualTo(new java.math.BigDecimal("70.0"));
            assertThat(rate.invoke(null, Map.of(), java.math.BigDecimal.ONE)).isNull();
        }
    }

    @Nested
    @DisplayName("action items")
    class Actions {

        @Test
        @DisplayName("only kinds the user can act on are queried; oldest first; paged")
        void listAndPage() {
            signIn(true, "DASHBOARD_READ", "LESSON_PLAN_REVIEW", "REEVALUATION_MANAGE");
            UUID a = UUID.randomUUID();
            UUID b = UUID.randomUUID();
            when(queries.pending(ActionItemType.LESSON_PLAN_REVIEW, YEAR, USER)).thenReturn(List.of(
                    new PendingRow(ActionItemType.LESSON_PLAN_REVIEW, a.toString(), "Plan", "6-A", "Asha", Instant.parse("2026-11-20T10:00:00Z"))));
            when(queries.pending(ActionItemType.REEVALUATION_REQUEST, YEAR, USER)).thenReturn(List.of(
                    new PendingRow(ActionItemType.REEVALUATION_REQUEST, b.toString(), "Re-eval", "7 Maths", null, Instant.parse("2026-11-19T10:00:00Z"))));

            Page<ActionItem> page = service.actionItems(null, null, 0, 1);
            assertThat(page.getTotalElements()).isEqualTo(2);
            assertThat(page.getContent()).singleElement().satisfies(i -> {
                assertThat(i.id()).isEqualTo("REEVALUATION_REQUEST:" + b);
                assertThat(i.api()).isEqualTo("POST /api/v1/reevaluation-requests/" + b + "/decide");
            });
            verify(queries, never()).pending(eq(ActionItemType.MARKS_LOCK), any(), any());
            verify(queries, never()).pending(eq(ActionItemType.REPORT_CARD_PUBLISH), any(), any());
        }

        @Test
        @DisplayName("quick counts: pending approvals is null when the user approves nothing")
        void noApprovals() {
            signIn(true, "DASHBOARD_READ");
            QuickCounts q = service.quickCounts();
            assertThat(q.pendingApprovals()).isNull();
            assertThat(q.attendancePendingSections()).isNull();
        }

        @Test
        @DisplayName("dismiss stores the item for the current user")
        void dismiss() {
            signIn(true, "DASHBOARD_READ", "MARKS_LOCK");
            UUID paper = UUID.randomUUID();
            assertThat(service.dismiss("MARKS_LOCK:" + paper).dismissed()).isTrue();
            verify(queries).dismiss(USER, ActionItemType.MARKS_LOCK, paper.toString());
        }

        @Test
        @DisplayName("report card ids carry term and class")
        void dismissReportCard() {
            signIn(true, "DASHBOARD_READ", "REPORT_CARD_PUBLISH");
            String key = UUID.randomUUID() + ":" + UUID.randomUUID();
            service.dismiss("REPORT_CARD_PUBLISH:" + key);
            verify(queries).dismiss(USER, ActionItemType.REPORT_CARD_PUBLISH, key);
        }

        @Test
        @DisplayName("malformed ids and unknown types are rejected; other people's kinds are forbidden")
        void dismissRejects() {
            signIn(true, "DASHBOARD_READ", "MARKS_LOCK");
            assertThatThrownBy(() -> service.dismiss("nonsense")).isInstanceOf(BusinessException.class);
            assertThatThrownBy(() -> service.dismiss("NOPE:" + UUID.randomUUID())).isInstanceOf(BusinessException.class);
            assertThatThrownBy(() -> service.dismiss("MARKS_LOCK:not-a-uuid")).isInstanceOf(BusinessException.class);
            assertThatThrownBy(() -> service.dismiss("LESSON_PLAN_REVIEW:" + UUID.randomUUID())).isInstanceOf(AccessDeniedException.class);
            verify(queries, never()).dismiss(any(), any(), any());
        }
    }
}
