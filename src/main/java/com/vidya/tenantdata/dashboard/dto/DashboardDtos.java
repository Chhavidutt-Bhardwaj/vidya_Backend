package com.vidya.tenantdata.dashboard.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Responses of the school overview dashboard. Every block that the user may not read, or that needs a module that is not built
 * yet, is {@code null} and listed in {@code unavailable} with the reason, so the frontend can show "not available" instead of zeros.
 */
public final class DashboardDtos {

    private DashboardDtos() {
    }

    /** Why a block is missing: the user lacks the module's read permission / school-wide scope, or the module is not built yet. */
    public enum UnavailableReason { NO_PERMISSION, NOT_BUILT, NO_ACADEMIC_YEAR }

    public record Unavailable(String block, UnavailableReason reason, String message) {
    }

    /** The academic year and date window every number was computed for. */
    public record Window(UUID academicYearId, String academicYearName, LocalDate yearStart, LocalDate yearEnd, LocalDate from, LocalDate to) {
    }

    // ── GET /dashboard ───────────────────────────────────────────────────────────────

    /** Everything on the home screen in one round trip. School name and campuses come from {@code GET /dashboard/campuses}. */
    public record DashboardHome(Window window, Kpis kpis, Snapshot snapshot, List<PulseTile> operationsPulse,
                                QuickCounts quickCounts, ActionItemPreview actionItems, AutoDebit autoDebit, List<Unavailable> unavailable) {
    }

    public record ActionItemPreview(long pendingCount, List<ActionItem> top) {
    }

    // ── GET /dashboard/kpis ─────────────────────────────────────────────────────────

    public record Kpis(Window window, EnrollmentKpi enrollment, AttendanceKpi attendance, AdmissionsKpi admissions, FeesKpi fees,
                       List<Unavailable> unavailable) {
    }

    /**
     * @param value      students with a live (ADMITTED / ACTIVE) enrolment in the year
     * @param capacity   sum of the year's section capacities; null when no section has a capacity
     * @param delta      % change against the previous academic year's enrolment; null when there is no previous year
     */
    public record EnrollmentKpi(long value, Long capacity, BigDecimal occupancyPercent, BigDecimal delta, String deltaLabel, String href) {
    }

    /**
     * Attendance of {@code date} (the window's {@code to}).
     *
     * @param presentRate       school's attendance formula: present + excused + ½ half-day + late × late weight, over marked
     * @param unexcusedAbsences ABSENT marks of the day
     * @param delta             percentage points against the average of the previous 30 days that have marks
     * @param sectionsMarked    sections with at least one mark on the date, of {@code sectionsTotal} sections with students
     */
    public record AttendanceKpi(LocalDate date, boolean workingDay, BigDecimal presentRate, long marked, long present, long absent,
                                long late, long halfDay, long excused, long unexcusedAbsences, BigDecimal delta, String deltaLabel,
                                long sectionsMarked, long sectionsTotal, String href) {
    }

    /**
     * There is no enquiry / application pipeline yet, so this reports students admitted (enrolled) in the window. {@code activeLeads}
     * stays null until the admissions module (03_ADMISSIONS.md) is built.
     */
    public record AdmissionsKpi(Long activeLeads, long admittedInWindow, BigDecimal delta, String deltaLabel, String href) {
    }

    /**
     * @param collectedPaise  realized in the window
     * @param targetPaise     net payable of lines falling due in the window
     * @param collectionRate  paid ÷ net payable of everything due by {@code to}, in % (null when nothing has fallen due)
     * @param delta           % change of collectedPaise against the window of the same length just before
     */
    public record FeesKpi(boolean collectionAvailable, Long collectedPaise, Long targetPaise, BigDecimal collectionRate, BigDecimal delta,
                          String deltaLabel, Long overduePaise, Long overdueLedgers, Long ledgers, long activeFeeStructures,
                          long draftFeeStructures, String href) {
    }

    // ── GET /dashboard/snapshot ─────────────────────────────────────────────────────

    /** Basic facts about the school for the selected year. A null part means the user may not read it. */
    public record Snapshot(Window window, StudentCounts students, StaffCounts staff, AcademicCounts academics, List<Unavailable> unavailable) {
    }

    public record StudentCounts(long enrolled, long admitted, long active, long withoutSection, long boys, long girls, long otherOrUnknown,
                                long guardiansLinked) {
    }

    public record StaffCounts(long active, long teaching, long nonTeaching, long onLeaveOrInactive) {
    }

    public record AcademicCounts(long classes, long sections, long subjects, long sectionsWithoutClassTeacher, String timetableStatus) {
    }

    // ── GET /dashboard/operations-pulse ─────────────────────────────────────────────

    /**
     * @param status COVERED / NONE / UPCOMING / NOT_SCHEDULED / CONFIGURED / NOT_SET_UP / ATTENTION
     * @param count  the tile's main number (substitutions today, days to the exam, vehicles ...), when it has one
     */
    public record PulseTile(String key, String title, String description, String status, Long count, String href) {
    }

    // ── GET /dashboard/quick-counts ─────────────────────────────────────────────────

    /** Badge numbers. A null count means not available (no permission, or module not built). */
    /** {@code feeCollectedTodayPaise} / {@code feePaymentsToday}: realized today; {@code chequesPending}: cheques waiting to clear. */
    public record QuickCounts(LocalDate date, Long attendancePendingSections, Long substitutionsToday, Long pendingApprovals,
                              Long newAdmissionsThisWeek, Long upcomingHolidays, Long feeCollectedTodayPaise, Long feePaymentsToday,
                              Long chequesPending, List<Unavailable> unavailable) {
    }

    // ── GET /dashboard/attendance-trend ─────────────────────────────────────────────

    public record AttendanceTrend(LocalDate from, LocalDate to, BigDecimal averageRate, List<AttendancePoint> points) {
    }

    public record AttendancePoint(LocalDate date, long marked, long present, long absent, BigDecimal presentRate) {
    }

    // ── GET /dashboard/enrollment-by-class ──────────────────────────────────────────

    public record ClassEnrollment(UUID classId, String className, int sortOrder, long students, Long capacity, BigDecimal occupancyPercent,
                                  int sections) {
    }

    // ── GET /dashboard/auto-debit ───────────────────────────────────────────────────

    /**
     * This month's mandate auto-debit (06_FEES_FINANCE.md §5.3). Counts are debits (one per child and attempt) of the month's cycle.
     *
     * @param enabled            auto-debit switched on in the school's online fee settings
     * @param debitDate          the month's debit day (siblings follow on later days)
     * @param inProgressPaise    sent to the bank, result not back yet (held on the ledgers like a cheque)
     * @param bouncedPaise       debits the bank returned (a retry may be scheduled)
     * @param bounceChargesPaise NACH bounce charges + GST added to ledgers this month
     * @param errors             debits the gateway refused at submission (see action items FEE_DEBIT_ERROR)
     */
    public record AutoDebit(boolean enabled, LocalDate month, LocalDate debitDate, long activeMandates, long pendingMandates,
                            long scheduled, long scheduledPaise, long inProgress, long inProgressPaise, long collected, long collectedPaise,
                            long bounced, long bouncedPaise, long bounceChargesPaise, long skipped, long errors, String href) {
    }

    // ── GET /dashboard/action-items ─────────────────────────────────────────────────

    public enum ActionItemType {
        LESSON_PLAN_REVIEW, MARKS_LOCK, REPORT_CARD_PUBLISH, REEVALUATION_REQUEST, FEE_CONCESSION,
        /** Auto-debit the gateway refused at submission. */
        FEE_DEBIT_ERROR,
        /** Online payment with money left over after every due was paid: refund it. */
        FEE_UNALLOCATED,
        /** Mandate still pending at the bank / payer after {@code 3} days. */
        FEE_MANDATE_STALLED
    }

    /**
     * @param id       {@code TYPE:key}; pass it to {@code POST /dashboard/action-items/{id}/dismiss}
     * @param entityId the record to open (lesson plan, exam paper, re-evaluation request, or {@code termId:classId} for report cards)
     * @param api      the endpoint that acts on the item in its own module
     */
    public record ActionItem(String id, ActionItemType type, String title, String description, String raisedBy, Instant raisedAt,
                             String entityId, String href, String api) {
    }

    public record DismissResult(String id, boolean dismissed) {
    }
}
