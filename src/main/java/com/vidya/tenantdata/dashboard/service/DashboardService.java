package com.vidya.tenantdata.dashboard.service;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.common.exception.UnauthorizedException;
import com.vidya.common.util.SecurityUtils;
import com.vidya.tenantdata.academic.entity.AcademicYear;
import com.vidya.tenantdata.academic.repository.AcademicYearRepository;
import com.vidya.tenantdata.calendar.service.CalendarService;
import com.vidya.tenantdata.dashboard.dto.DashboardDtos.AcademicCounts;
import com.vidya.tenantdata.dashboard.dto.DashboardDtos.ActionItem;
import com.vidya.tenantdata.dashboard.dto.DashboardDtos.ActionItemPreview;
import com.vidya.tenantdata.dashboard.dto.DashboardDtos.ActionItemType;
import com.vidya.tenantdata.dashboard.dto.DashboardDtos.AdmissionsKpi;
import com.vidya.tenantdata.dashboard.dto.DashboardDtos.AttendanceKpi;
import com.vidya.tenantdata.dashboard.dto.DashboardDtos.AttendancePoint;
import com.vidya.tenantdata.dashboard.dto.DashboardDtos.AttendanceTrend;
import com.vidya.tenantdata.dashboard.dto.DashboardDtos.AutoDebit;
import com.vidya.tenantdata.dashboard.dto.DashboardDtos.ClassEnrollment;
import com.vidya.tenantdata.dashboard.dto.DashboardDtos.DashboardHome;
import com.vidya.tenantdata.dashboard.dto.DashboardDtos.DismissResult;
import com.vidya.tenantdata.dashboard.dto.DashboardDtos.EnrollmentKpi;
import com.vidya.tenantdata.dashboard.dto.DashboardDtos.FeesKpi;
import com.vidya.tenantdata.fee.dto.FeeDtos.DashboardFees;
import com.vidya.tenantdata.fee.dto.FeeDtos.FeeBreakdown;
import com.vidya.tenantdata.fee.dto.FeeDtos.FeeTrend;
import com.vidya.tenantdata.fee.service.FeeReportService;
import com.vidya.tenantdata.dashboard.dto.DashboardDtos.Kpis;
import com.vidya.tenantdata.dashboard.dto.DashboardDtos.PulseTile;
import com.vidya.tenantdata.dashboard.dto.DashboardDtos.QuickCounts;
import com.vidya.tenantdata.dashboard.dto.DashboardDtos.Snapshot;
import com.vidya.tenantdata.dashboard.dto.DashboardDtos.Window;
import com.vidya.tenantdata.dashboard.dto.DashboardDtos.StaffCounts;
import com.vidya.tenantdata.dashboard.dto.DashboardDtos.StudentCounts;
import com.vidya.tenantdata.dashboard.dto.DashboardDtos.Unavailable;
import com.vidya.tenantdata.dashboard.dto.DashboardDtos.UnavailableReason;
import com.vidya.tenantdata.dashboard.repository.DashboardQueries;
import com.vidya.tenantdata.dashboard.repository.DashboardQueries.NextExam;
import com.vidya.tenantdata.dashboard.repository.DashboardQueries.PendingRow;
import com.vidya.tenantdata.dashboard.repository.DashboardQueries.Transport;
import com.vidya.tenantdata.operations.service.AttendancePolicyService;
import com.vidya.tenantdata.security.DataAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The school overview dashboard: read-only aggregates over students, attendance, staff, timetable, exams and transport, plus
 * the user's pending approvals. Each block is computed only when the user holds that module's read permission and, for
 * person data, school-wide (ALL) scope; otherwise it is null and listed in {@code unavailable}. Blocks whose module is not
 * built yet (admissions pipeline, live transport, compliance sync) are reported as {@code NOT_BUILT}.
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    static final int TREND_MAX_DAYS = 366;
    static final int BASELINE_DAYS = 30;
    static final int PREVIEW_ITEMS = 5;

    private final DashboardQueries queries;
    private final AcademicYearRepository yearRepository;
    private final CalendarService calendarService;
    private final AttendancePolicyService attendancePolicy;
    private final DataAccessService dataAccess;
    private final Clock clock;
    private final FeeReportService feeReports;

    // ── Home ─────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public DashboardHome home(UUID academicYearId, LocalDate from, LocalDate to) {
        Ctx c = context(academicYearId, from, to);
        Kpis kpis = kpis(c);
        Snapshot snapshot = snapshot(c);
        List<Unavailable> unavailable = new ArrayList<>();
        List<PulseTile> pulse = pulse(c, unavailable);
        QuickCounts quick = quickCounts(c);
        List<ActionItem> items = allActionItems(c, null);
        AutoDebit autoDebit = null;
        if (can("FEE_READ", "FEE")) {
            autoDebit = autoDebit(YearMonth.from(c.today()));
        } else {
            unavailable.add(noPermission("autoDebit", "FEE_READ with school-wide fee scope"));
        }
        unavailable.addAll(kpis.unavailable());
        snapshot.unavailable().stream().filter(u -> !unavailable.contains(u)).forEach(unavailable::add);
        quick.unavailable().stream().filter(u -> !unavailable.contains(u)).forEach(unavailable::add);
        return new DashboardHome(c.window(), kpis, snapshot, pulse, quick,
                new ActionItemPreview(items.size(), items.stream().limit(PREVIEW_ITEMS).toList()), autoDebit, unavailable);
    }

    // ── Auto-debit ───────────────────────────────────────────────────────────────────

    /** The month's mandate auto-debit (default this month). The controller checks FEE_READ + school-wide fee scope. */
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public AutoDebit autoDebit(YearMonth month) {
        YearMonth m = month != null ? month : YearMonth.from(LocalDate.now(clock));
        return queries.autoDebit(m.atDay(1), "/fees?tab=auto-debit&month=" + m);
    }

    // ── KPIs ─────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public Kpis kpis(UUID academicYearId, LocalDate from, LocalDate to) {
        return kpis(context(academicYearId, from, to));
    }

    private Kpis kpis(Ctx c) {
        List<Unavailable> unavailable = new ArrayList<>();
        AcademicYear y = c.year();

        EnrollmentKpi enrollment = null;
        if (!can("STUDENT_READ", "STUDENT")) {
            unavailable.add(noPermission("enrollment", "STUDENT_READ with school-wide student scope"));
        } else if (y == null) {
            unavailable.add(noYear("enrollment"));
        } else {
            long value = queries.liveEnrollment(y.getId());
            Long capacity = queries.sectionCapacity(y.getId());
            BigDecimal delta = queries.previousYearId(y.getStartDate()).map(queries::pastEnrollment).map(prev -> change(value, prev)).orElse(null);
            enrollment = new EnrollmentKpi(value, capacity, DashboardQueries.percent(value, capacity), delta, "vs last academic year",
                    "/students?status=ACTIVE");
        }

        AttendanceKpi attendance = null;
        if (!can("ATTENDANCE_READ", "ATTENDANCE")) {
            unavailable.add(noPermission("attendance", "ATTENDANCE_READ with school-wide attendance scope"));
        } else {
            attendance = attendanceKpi(c.to(), y);
        }

        AdmissionsKpi admissions = null;
        if (!can("STUDENT_READ", "STUDENT")) {
            unavailable.add(noPermission("admissions", "STUDENT_READ with school-wide student scope"));
        } else {
            long days = ChronoUnit.DAYS.between(c.from(), c.to()) + 1;
            long now = queries.admittedBetween(c.from(), c.to());
            long before = queries.admittedBetween(c.from().minusDays(days), c.from().minusDays(1));
            admissions = new AdmissionsKpi(null, now, change(now, before), "vs previous " + days + " day(s)", "/students?sort=admissionDate,desc");
            unavailable.add(notBuilt("admissions.leads", "Enquiries and applications need the admissions pipeline (03_ADMISSIONS.md)."));
        }

        FeesKpi fees = null;
        if (!can("FEE_READ", "FEE")) {
            unavailable.add(noPermission("fees", "FEE_READ with school-wide fee scope"));
        } else {
            long[] structures = y == null ? new long[]{0, 0} : queries.feeStructures(y.getId());
            if (y == null) {
                fees = new FeesKpi(false, null, null, null, null, null, null, null, null, structures[0], structures[1], "/fees");
                unavailable.add(noYear("fees.collection"));
            } else {
                long days = ChronoUnit.DAYS.between(c.from(), c.to()) + 1;
                DashboardFees f = feeReports.dashboard(y.getId(), c.from(), c.to());
                fees = new FeesKpi(true, f.collectedPaise(), f.targetPaise(), f.collectionRate(), change(f.collectedPaise(), f.previousCollectedPaise()),
                        "vs previous " + days + " day(s)", f.overduePaise(), f.overdueLedgers(), f.ledgers(), structures[0], structures[1], "/fees");
            }
        }
        return new Kpis(c.window(), enrollment, attendance, admissions, fees, unavailable);
    }

    AttendanceKpi attendanceKpi(LocalDate date, AcademicYear y) {
        BigDecimal lateWeight = lateWeight();
        Map<String, Long> day = queries.attendanceByStatus(date);
        BigDecimal rate = rate(day, lateWeight);
        BigDecimal baseline = averageRate(queries.attendanceByDay(date.minusDays(BASELINE_DAYS), date.minusDays(1)), lateWeight);
        BigDecimal delta = rate == null || baseline == null ? null : rate.subtract(baseline);
        long[] sections = y == null ? new long[]{0, 0} : queries.sectionsMarked(y.getId(), date);
        long absent = day.getOrDefault("ABSENT", 0L);
        return new AttendanceKpi(date, workingDay(date), rate, total(day), day.getOrDefault("PRESENT", 0L), absent, day.getOrDefault("LATE", 0L),
                day.getOrDefault("HALF_DAY", 0L), day.getOrDefault("EXCUSED", 0L), absent, delta, "vs previous " + BASELINE_DAYS + "-day average",
                sections[1], sections[0], "/attendance?date=" + date);
    }

    // ── Snapshot ─────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public Snapshot snapshot(UUID academicYearId) {
        return snapshot(context(academicYearId, null, null));
    }

    private Snapshot snapshot(Ctx c) {
        List<Unavailable> unavailable = new ArrayList<>();
        AcademicYear y = c.year();
        StudentCounts students = null;
        if (!can("STUDENT_READ", "STUDENT")) {
            unavailable.add(noPermission("students", "STUDENT_READ with school-wide student scope"));
        } else if (y == null) {
            unavailable.add(noYear("students"));
        } else {
            students = queries.studentCounts(y.getId());
        }
        StaffCounts staff = null;
        if (can("STAFF_READ", "STAFF")) {
            staff = queries.staffCounts();
        } else {
            unavailable.add(noPermission("staff", "STAFF_READ with school-wide staff scope"));
        }
        AcademicCounts academics = null;
        if (!can("ACADEMIC_YEAR_READ", null)) {
            unavailable.add(noPermission("academics", "ACADEMIC_YEAR_READ"));
        } else if (y == null) {
            unavailable.add(noYear("academics"));
        } else {
            academics = queries.academicCounts(y.getId());
        }
        return new Snapshot(c.window(), students, staff, academics, unavailable);
    }

    // ── Operations pulse ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public List<PulseTile> operationsPulse() {
        return pulse(context(null, null, null), new ArrayList<>());
    }

    /** Live tiles for today, whatever window the rest of the page shows. Missing tiles are added to {@code unavailable}. */
    private List<PulseTile> pulse(Ctx c, List<Unavailable> unavailable) {
        LocalDate today = c.today();
        AcademicYear y = c.year();
        List<PulseTile> tiles = new ArrayList<>();

        if (can("ATTENDANCE_READ", "ATTENDANCE") && y != null) {
            long[] s = queries.sectionsMarked(y.getId(), today);
            boolean working = workingDay(today);
            String status = !working ? "NOT_A_WORKING_DAY" : s[1] >= s[0] ? "DONE" : "PENDING";
            tiles.add(new PulseTile("ATTENDANCE", s[1] + " / " + s[0] + " sections marked",
                    working ? (s[0] - s[1]) + " section(s) still to mark today" : "Today is not a working day", status, s[0] - s[1],
                    "/attendance?date=" + today));
        } else {
            unavailable.add(y == null ? noYear("pulse.attendance") : noPermission("pulse.attendance", "ATTENDANCE_READ with school-wide scope"));
        }

        if (can("SUBSTITUTION_READ", null)) {
            Map<String, Long> subs = queries.substitutionsByType(today);
            long total = total(subs);
            tiles.add(new PulseTile("PROXIES", total + " substitution(s) today",
                    subs.getOrDefault("TEACHER_SUBSTITUTE", 0L) + " proxy teacher(s), " + subs.getOrDefault("ROOM_CHANGE", 0L)
                            + " room change(s), " + subs.getOrDefault("CLASS_CANCELLED", 0L) + " class(es) cancelled",
                    total > 0 ? "COVERED" : "NONE", total, "/academics?tab=substitutions"));
        } else {
            unavailable.add(noPermission("pulse.proxies", "SUBSTITUTION_READ"));
        }

        if (can("EXAM_READ", null) && y != null) {
            NextExam next = queries.nextExam(y.getId(), today).orElse(null);
            if (next == null) {
                tiles.add(new PulseTile("NEXT_EXAM", "No exam scheduled", "No published exam paper from today in " + y.getName(),
                        "NOT_SCHEDULED", null, "/examinations"));
            } else {
                long inDays = ChronoUnit.DAYS.between(today, next.examDate());
                tiles.add(new PulseTile("NEXT_EXAM", next.termName() + " – " + next.scheduleName(),
                        next.className() + " · " + next.subjectName() + " on " + next.examDate() + " · " + next.papersNext7Days()
                                + " paper(s) in the next 7 days", "UPCOMING", inDays, "/examinations"));
            }
        } else {
            unavailable.add(y == null ? noYear("pulse.nextExam") : noPermission("pulse.nextExam", "EXAM_READ"));
        }

        if (can("TRANSPORT_READ", null)) {
            Transport t = queries.transport(today);
            String status = t.activeVehicles() + t.activeRoutes() == 0 ? "NOT_SET_UP"
                    : t.documentsExpiring() > 0 || t.inMaintenance() > 0 ? "ATTENTION" : "CONFIGURED";
            tiles.add(new PulseTile("TRANSPORT", t.activeVehicles() + " vehicle(s) on " + t.activeRoutes() + " route(s)",
                    t.inMaintenance() + " in maintenance, " + t.documentsExpiring() + " with documents expiring within 30 days. "
                            + "Live trip tracking is not built yet.", status, t.activeVehicles(), "/transport"));
        } else {
            unavailable.add(noPermission("pulse.transport", "TRANSPORT_READ"));
        }

        if (can("FEE_READ", "FEE")) {
            AutoDebit a = autoDebit(YearMonth.from(today));
            String status = !a.enabled() ? "NOT_SET_UP" : a.bounced() + a.errors() > 0 ? "ATTENTION" : a.inProgress() > 0 ? "IN_PROGRESS"
                    : a.scheduled() > 0 ? "UPCOMING" : a.collected() > 0 ? "DONE" : "NONE";
            String description = !a.enabled() ? "Auto-debit is off. Connect a gateway and switch it on in online fee settings."
                    : a.scheduled() + " scheduled, " + a.inProgress() + " at the bank, " + a.collected() + " collected, " + a.bounced()
                    + " bounced, " + a.errors() + " refused · " + a.activeMandates() + " active mandate(s), " + a.pendingMandates() + " pending";
            tiles.add(new PulseTile("AUTO_DEBIT", "Auto-debit on " + a.debitDate(), description, status, a.collectedPaise(), a.href()));
        } else {
            unavailable.add(noPermission("pulse.autoDebit", "FEE_READ with school-wide fee scope"));
        }

        unavailable.add(notBuilt("pulse.compliance", "APAAR / UDISE+ sync status is not stored yet (02_STUDENTS.md)."));
        return tiles;
    }

    // ── Quick counts ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public QuickCounts quickCounts() {
        return quickCounts(context(null, null, null));
    }

    private QuickCounts quickCounts(Ctx c) {
        LocalDate today = c.today();
        AcademicYear y = c.year();
        List<Unavailable> unavailable = new ArrayList<>();

        Long pendingSections = null;
        if (can("ATTENDANCE_READ", "ATTENDANCE") && y != null) {
            long[] s = queries.sectionsMarked(y.getId(), today);
            pendingSections = workingDay(today) ? s[0] - s[1] : 0L;
        }
        Long substitutions = can("SUBSTITUTION_READ", null) ? total(queries.substitutionsByType(today)) : null;
        Long approvals = allowedTypes().isEmpty() || y == null ? null : (long) allActionItems(c, null).size();
        Long admissions = can("STUDENT_READ", "STUDENT") ? queries.admittedBetween(today.minusDays(6), today) : null;
        Long holidays = can("CALENDAR_READ", null) ? queries.upcomingHolidays(today) : null;
        Long feeToday = null;
        Long paymentsToday = null;
        Long cheques = null;
        if (can("FEE_READ", "FEE") && y != null) {
            DashboardFees f = feeReports.dashboard(y.getId(), today, today);
            feeToday = f.collectedTodayPaise();
            paymentsToday = f.paymentsToday();
            cheques = f.chequesPending();
        }
        return new QuickCounts(today, pendingSections, substitutions, approvals, admissions, holidays, feeToday, paymentsToday, cheques, unavailable);
    }

    // ── Trends and breakdowns ────────────────────────────────────────────────────────

    /** Daily attendance rate over the window (default: month to date). Days without marks are left out. */
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public AttendanceTrend attendanceTrend(LocalDate from, LocalDate to) {
        Ctx c = context(null, from, to);
        if (ChronoUnit.DAYS.between(c.from(), c.to()) >= TREND_MAX_DAYS) {
            throw new BusinessException("DASHBOARD_RANGE_TOO_LONG", "Choose a date range of at most " + TREND_MAX_DAYS + " days.");
        }
        BigDecimal lateWeight = lateWeight();
        Map<LocalDate, Map<String, Long>> byDay = queries.attendanceByDay(c.from(), c.to());
        List<AttendancePoint> points = new ArrayList<>();
        byDay.forEach((d, counts) -> points.add(new AttendancePoint(d, total(counts), counts.getOrDefault("PRESENT", 0L),
                counts.getOrDefault("ABSENT", 0L), rate(counts, lateWeight))));
        return new AttendanceTrend(c.from(), c.to(), averageRate(byDay, lateWeight), points);
    }

    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public List<ClassEnrollment> enrollmentByClass(UUID academicYearId) {
        AcademicYear y = context(academicYearId, null, null).year();
        return y == null ? List.of() : queries.enrollmentByClass(y.getId());
    }

    /** Daily fee collections over the window (default month to date), with a running total. Empty without an academic year. */
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public FeeTrend feeTrend(UUID academicYearId, LocalDate from, LocalDate to) {
        Ctx c = context(academicYearId, from, to);
        if (ChronoUnit.DAYS.between(c.from(), c.to()) >= TREND_MAX_DAYS) {
            throw new BusinessException("DASHBOARD_RANGE_TOO_LONG", "Choose a date range of at most " + TREND_MAX_DAYS + " days.");
        }
        return c.year() == null ? new FeeTrend(null, c.from(), c.to(), 0, List.of()) : feeReports.trend(c.year().getId(), c.from(), c.to());
    }

    /** The year's billing, collection and dues per fee head and per class. */
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public FeeBreakdown feeBreakdown(UUID academicYearId) {
        AcademicYear y = context(academicYearId, null, null).year();
        return y == null ? new FeeBreakdown(null, LocalDate.now(clock), List.of(), List.of()) : feeReports.breakdown(y.getId());
    }

    // ── Action items ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public Page<ActionItem> actionItems(UUID academicYearId, ActionItemType type, int page, int size) {
        List<ActionItem> all = allActionItems(context(academicYearId, null, null), type);
        int from = Math.min(page * size, all.size());
        int to = Math.min(from + size, all.size());
        return new PageImpl<>(all.subList(from, to), PageRequest.of(page, size), all.size());
    }

    private List<ActionItem> allActionItems(Ctx c, ActionItemType only) {
        if (c.year() == null) {
            return List.of();
        }
        UUID userId = currentUser();
        List<PendingRow> rows = new ArrayList<>();
        for (ActionItemType type : allowedTypes()) {
            if (only == null || only == type) {
                rows.addAll(queries.pending(type, c.year().getId(), userId));
            }
        }
        return rows.stream()
                .sorted(Comparator.comparing(PendingRow::raisedAt, Comparator.nullsLast(Comparator.<Instant>naturalOrder())))
                .map(DashboardService::toItem)
                .toList();
    }

    /** Hides the item from the current user's dashboard only; it stays pending in its module. Idempotent. */
    @Transactional(transactionManager = "tenantTransactionManager")
    public DismissResult dismiss(String id) {
        int colon = id == null ? -1 : id.indexOf(':');
        if (colon <= 0) {
            throw new BusinessException("DASHBOARD_ITEM_ID_INVALID", "Action item id must look like TYPE:key, as returned by the action items list.");
        }
        ActionItemType type;
        try {
            type = ActionItemType.valueOf(id.substring(0, colon));
        } catch (IllegalArgumentException e) {
            throw new BusinessException("DASHBOARD_ITEM_ID_INVALID", "Unknown action item type " + id.substring(0, colon) + ".");
        }
        String key = id.substring(colon + 1);
        validateKey(type, key);
        if (!allowedTypes().contains(type)) {
            throw new AccessDeniedException("You do not handle " + type + " items");
        }
        queries.dismiss(currentUser(), type, key);
        return new DismissResult(id, true);
    }

    /** Action items the user can act on, with the permission (and data scope) the approving endpoint needs. */
    List<ActionItemType> allowedTypes() {
        List<ActionItemType> out = new ArrayList<>();
        if (can("LESSON_PLAN_REVIEW", "STAFF")) {
            out.add(ActionItemType.LESSON_PLAN_REVIEW);
        }
        if (can("MARKS_LOCK", "MARKS")) {
            out.add(ActionItemType.MARKS_LOCK);
        }
        if (can("REPORT_CARD_PUBLISH", "MARKS")) {
            out.add(ActionItemType.REPORT_CARD_PUBLISH);
        }
        if (can("REEVALUATION_MANAGE", null)) {
            out.add(ActionItemType.REEVALUATION_REQUEST);
        }
        if (can("FEE_APPROVE", "FEE")) {
            out.add(ActionItemType.FEE_CONCESSION);
        }
        if (can("FEE_MANAGE", "FEE")) {
            out.add(ActionItemType.FEE_DEBIT_ERROR);
            out.add(ActionItemType.FEE_UNALLOCATED);
            out.add(ActionItemType.FEE_MANDATE_STALLED);
        }
        return out;
    }

    static ActionItem toItem(PendingRow r) {
        String id = r.type().name() + ":" + r.key();
        return switch (r.type()) {
            case LESSON_PLAN_REVIEW -> new ActionItem(id, r.type(), r.title(), r.description(), r.raisedBy(), r.raisedAt(), r.key(),
                    "/academics?tab=lesson-plans&id=" + r.key(), "POST /api/v1/academic-config/lesson-plans/" + r.key() + "/review");
            case MARKS_LOCK -> new ActionItem(id, r.type(), r.title(), r.description(), r.raisedBy(), r.raisedAt(), r.key(),
                    "/examinations?tab=marks&configId=" + r.key(), "POST /api/v1/exams/marks/subject-config/" + r.key() + "/lock");
            case REPORT_CARD_PUBLISH -> {
                String[] termAndClass = r.key().split(":", 2);
                yield new ActionItem(id, r.type(), r.title(), r.description(), r.raisedBy(), r.raisedAt(), r.key(),
                        "/examinations?tab=report-cards&termId=" + termAndClass[0] + "&classId=" + termAndClass[1], "POST /api/v1/report-cards/publish");
            }
            case REEVALUATION_REQUEST -> new ActionItem(id, r.type(), r.title(), r.description(), r.raisedBy(), r.raisedAt(), r.key(),
                    "/examinations?tab=re-evaluation&id=" + r.key(), "POST /api/v1/reevaluation-requests/" + r.key() + "/decide");
            case FEE_CONCESSION -> new ActionItem(id, r.type(), r.title(), r.description(), r.raisedBy(), r.raisedAt(), r.key(),
                    "/fees?tab=concessions&id=" + r.key(), "POST /api/v1/fees/concession-requests/" + r.key() + "/approve");
            // Refused debit: no bounce charge was added; send the payer a link or collect at the counter
            case FEE_DEBIT_ERROR -> new ActionItem(id, r.type(), r.title(), r.description(), r.raisedBy(), r.raisedAt(), r.key(),
                    "/fees?tab=auto-debit&debitId=" + r.key(), "POST /api/v1/fees/online/checkouts");
            // Refunds through the gateway are not built: refund from the gateway dashboard, then dismiss
            case FEE_UNALLOCATED -> new ActionItem(id, r.type(), r.title(), r.description(), r.raisedBy(), r.raisedAt(), r.key(),
                    "/fees?tab=online&checkoutId=" + r.key(), "GET /api/v1/fees/online/checkouts/" + r.key());
            case FEE_MANDATE_STALLED -> new ActionItem(id, r.type(), r.title(), r.description(), r.raisedBy(), r.raisedAt(), r.key(),
                    "/fees?tab=mandates&id=" + r.key(), "POST /api/v1/fees/online/mandates/" + r.key() + "/refresh");
        };
    }

    private static void validateKey(ActionItemType type, String key) {
        try {
            if (type == ActionItemType.REPORT_CARD_PUBLISH) {
                String[] parts = key.split(":", -1);
                if (parts.length != 2) {
                    throw new IllegalArgumentException();
                }
                UUID.fromString(parts[0]);
                UUID.fromString(parts[1]);
            } else {
                UUID.fromString(key);
            }
        } catch (IllegalArgumentException e) {
            throw new BusinessException("DASHBOARD_ITEM_ID_INVALID", "Action item id " + type + ":" + key + " is not valid.");
        }
    }

    // ── Export ───────────────────────────────────────────────────────────────────────

    /** The home screen as CSV rows {@code section,metric,value}; only the blocks the user may see. Money in rupees. */
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public String exportCsv(UUID academicYearId, LocalDate from, LocalDate to) {
        DashboardHome h = home(academicYearId, from, to);
        StringBuilder out = new StringBuilder("section,metric,value\n");
        Window w = h.window();
        row(out, "window", "academic year", w.academicYearName());
        row(out, "window", "from", w.from());
        row(out, "window", "to", w.to());
        Kpis k = h.kpis();
        if (k.enrollment() != null) {
            row(out, "enrolment", "active students", k.enrollment().value());
            row(out, "enrolment", "capacity", k.enrollment().capacity());
            row(out, "enrolment", "occupancy %", k.enrollment().occupancyPercent());
            row(out, "enrolment", "change vs last year %", k.enrollment().delta());
        }
        if (k.attendance() != null) {
            row(out, "attendance", "date", k.attendance().date());
            row(out, "attendance", "rate %", k.attendance().presentRate());
            row(out, "attendance", "marked", k.attendance().marked());
            row(out, "attendance", "absent", k.attendance().absent());
            row(out, "attendance", "sections marked", k.attendance().sectionsMarked() + " / " + k.attendance().sectionsTotal());
        }
        if (k.admissions() != null) {
            row(out, "admissions", "admitted in window", k.admissions().admittedInWindow());
            row(out, "admissions", "change %", k.admissions().delta());
        }
        if (k.fees() != null && k.fees().collectionAvailable()) {
            row(out, "fees", "collected (₹)", rupees(k.fees().collectedPaise()));
            row(out, "fees", "falling due in window (₹)", rupees(k.fees().targetPaise()));
            row(out, "fees", "collection rate %", k.fees().collectionRate());
            row(out, "fees", "overdue (₹)", rupees(k.fees().overduePaise()));
            row(out, "fees", "ledgers overdue", k.fees().overdueLedgers());
        }
        Snapshot sn = h.snapshot();
        if (sn.students() != null) {
            row(out, "students", "enrolled", sn.students().enrolled());
            row(out, "students", "boys", sn.students().boys());
            row(out, "students", "girls", sn.students().girls());
            row(out, "students", "without section", sn.students().withoutSection());
        }
        if (sn.staff() != null) {
            row(out, "staff", "active", sn.staff().active());
            row(out, "staff", "teaching", sn.staff().teaching());
            row(out, "staff", "non-teaching", sn.staff().nonTeaching());
        }
        if (sn.academics() != null) {
            row(out, "academics", "classes", sn.academics().classes());
            row(out, "academics", "sections", sn.academics().sections());
            row(out, "academics", "subjects", sn.academics().subjects());
            row(out, "academics", "timetable", sn.academics().timetableStatus());
        }
        for (PulseTile t : h.operationsPulse()) {
            row(out, "today", t.key(), t.title() + " (" + t.status() + ")");
        }
        if (h.autoDebit() != null) {
            AutoDebit a = h.autoDebit();
            row(out, "auto-debit", "enabled", a.enabled());
            row(out, "auto-debit", "debit date", a.debitDate());
            row(out, "auto-debit", "active mandates", a.activeMandates());
            row(out, "auto-debit", "collected (₹)", rupees(a.collectedPaise()));
            row(out, "auto-debit", "bounced", a.bounced());
            row(out, "auto-debit", "bounce charges (₹)", rupees(a.bounceChargesPaise()));
        }
        row(out, "approvals", "pending", h.actionItems().pendingCount());
        for (Unavailable u : h.unavailable()) {
            row(out, "not available", u.block(), u.reason() + ": " + u.message());
        }
        return out.toString();
    }

    private static void row(StringBuilder out, String section, String metric, Object value) {
        out.append(csv(section)).append(',').append(csv(metric)).append(',').append(csv(value == null ? "" : value.toString())).append('\n');
    }

    /** Quotes a CSV cell; cells that a spreadsheet would run as a formula get a leading apostrophe. */
    public static String csv(String v) {
        String s = !v.isEmpty() && "=+-@".indexOf(v.charAt(0)) >= 0 && !v.matches("-?\\d+(\\.\\d+)?") ? "'" + v : v;
        return s.matches("[^,\"\\r\\n]*") ? s : "\"" + s.replace("\"", "\"\"") + "\"";
    }

    private static String rupees(Long paise) {
        return paise == null ? null : BigDecimal.valueOf(paise).movePointLeft(2).toPlainString();
    }

    // ── Context and access ───────────────────────────────────────────────────────────

    /** Year (given, else the current one; may be null for a school that has none yet) and date window (default month to date). */
    record Ctx(AcademicYear year, LocalDate from, LocalDate to, LocalDate today) {
        Window window() {
            return year == null ? new Window(null, null, null, null, from, to)
                    : new Window(year.getId(), year.getName(), year.getStartDate(), year.getEndDate(), from, to);
        }
    }

    Ctx context(UUID academicYearId, LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now(clock);
        AcademicYear year = academicYearId != null
                ? yearRepository.findByIdAndNotDeleted(academicYearId).orElseThrow(() -> new ResourceNotFoundException("AcademicYear", academicYearId))
                : yearRepository.findCurrentYear().orElse(null);
        LocalDate end = to != null ? to : today;
        LocalDate start = from != null ? from : end.withDayOfMonth(1);
        if (start.isAfter(end)) {
            throw new BusinessException("DASHBOARD_RANGE_INVALID", "'from' must be on or before 'to'.");
        }
        return new Ctx(year, start, end, today);
    }

    /** The permission, and school-wide scope on the data domain when one is given. */
    boolean can(String permission, String domain) {
        if (!SecurityUtils.hasAuthority(permission)) {
            return false;
        }
        return domain == null || dataAccess.hasFullScope(SecurityContextHolder.getContext().getAuthentication(), domain);
    }

    private static UUID currentUser() {
        return SecurityUtils.getCurrentUserId().orElseThrow(() -> new UnauthorizedException("Sign in to see your action items."));
    }

    private boolean workingDay(LocalDate date) {
        try {
            return calendarService.isWorkingDay(date);
        } catch (ResourceNotFoundException e) {
            return false; // no academic year covers the date
        }
    }

    private BigDecimal lateWeight() {
        var policy = attendancePolicy.policy();
        return policy == null || policy.lateCountsAs() == null ? BigDecimal.ZERO : policy.lateCountsAs().weight();
    }

    // ── Arithmetic ───────────────────────────────────────────────────────────────────

    /**
     * The school's attendance formula (same as the student attendance summary): present + excused + ½ half-day + late × the
     * school's late weight, over all marks, in % with 1 decimal. Null when nothing is marked.
     */
    static BigDecimal rate(Map<String, Long> counts, BigDecimal lateWeight) {
        long total = total(counts);
        if (total == 0) {
            return null;
        }
        BigDecimal effective = BigDecimal.valueOf(counts.getOrDefault("PRESENT", 0L) + counts.getOrDefault("EXCUSED", 0L))
                .add(BigDecimal.valueOf(counts.getOrDefault("HALF_DAY", 0L)).multiply(new BigDecimal("0.5")))
                .add(BigDecimal.valueOf(counts.getOrDefault("LATE", 0L)).multiply(lateWeight));
        return effective.multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP);
    }

    /** Mean of the daily rates (each marked day weighs the same); null when no day has marks. */
    static BigDecimal averageRate(Map<LocalDate, Map<String, Long>> byDay, BigDecimal lateWeight) {
        List<BigDecimal> rates = byDay.values().stream().map(c -> rate(c, lateWeight)).filter(r -> r != null).toList();
        if (rates.isEmpty()) {
            return null;
        }
        return rates.stream().reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(rates.size()), 1, RoundingMode.HALF_UP);
    }

    /** % change from {@code before} to {@code now}, 1 decimal; null when there is nothing to compare with. */
    static BigDecimal change(long now, long before) {
        if (before == 0) {
            return null;
        }
        return BigDecimal.valueOf((now - before) * 100.0 / before).setScale(1, RoundingMode.HALF_UP);
    }

    private static long total(Map<String, Long> counts) {
        return counts.values().stream().mapToLong(Long::longValue).sum();
    }

    private static Unavailable noPermission(String block, String needs) {
        return new Unavailable(block, UnavailableReason.NO_PERMISSION, "Needs " + needs + ".");
    }

    private static Unavailable notBuilt(String block, String message) {
        return new Unavailable(block, UnavailableReason.NOT_BUILT, message);
    }

    private static Unavailable noYear(String block) {
        return new Unavailable(block, UnavailableReason.NO_ACADEMIC_YEAR, "Create and start an academic year first.");
    }
}
