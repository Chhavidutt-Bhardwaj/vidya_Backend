package com.vidya.tenantdata.fee.dto;

import com.vidya.tenantdata.fee.enums.AgeingBucket;
import com.vidya.tenantdata.fee.enums.ConcessionRequestStatus;
import com.vidya.tenantdata.fee.enums.DueStatus;
import com.vidya.tenantdata.fee.enums.LineSource;
import com.vidya.tenantdata.fee.enums.LineStatus;
import com.vidya.tenantdata.fee.enums.PaymentMode;
import com.vidya.tenantdata.fee.enums.PaymentStatus;
import com.vidya.tenantdata.operations.enums.ConcessionKind;
import com.vidya.tenantdata.operations.enums.NotificationChannel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Requests and responses of fee collection. All money is integer paise (₹1 = 100). */
public final class FeeDtos {

    private FeeDtos() {
    }

    // ── Invoices ────────────────────────────────────────────────────────────────────

    /**
     * @param installmentId only this instalment (default: every instalment of the year)
     * @param classIds      only these classes (default: every class with an ACTIVE fee structure)
     * @param dryRun        work out what would be billed and save nothing
     */
    public record GenerateInvoicesRequest(@NotNull UUID academicYearId, UUID installmentId, List<UUID> classIds, boolean dryRun) {
    }

    public record ClassNotBilled(UUID classId, String className, long students, String reason) {
    }

    public record GenerateInvoicesResult(boolean dryRun, int studentsConsidered, int studentsBilled, int ledgersCreated, int linesCreated,
                                         int linesAlreadyBilled, long totalPaise, long concessionPaise, List<ClassNotBilled> classesNotBilled) {
    }

    // ── Ledgers ─────────────────────────────────────────────────────────────────────

    /** One row of the ledgers list (and the header of a ledger). {@code duePaise} excludes cheques waiting to clear. */
    public record LedgerRow(UUID id, String invoiceNumber, UUID studentId, String admissionNumber, String rollNumber, String studentName,
                            UUID classId, String className, UUID sectionId, String sectionName, String parentName, String parentPhone,
                            long totalBilledPaise, long concessionPaise, long netPayablePaise, long paidPaise, long pendingClearancePaise,
                            long duePaise, long overduePaise, LocalDate nextDueDate, LocalDate oldestOverdueDate, DueStatus status,
                            Integer ageingDays, AgeingBucket bucket, Instant lastReminderAt, int remindersSent, UUID academicYearId) {
    }

    /**
     * @param lateFinePaise the school's late-fine rule applied to this line today, for information: it is not owed until the office
     *                      adds it as a charge
     */
    public record LineView(UUID id, UUID feeHeadId, String headName, String headCategory, LineSource source, Integer installmentNo,
                           String installment, String label, long amountPaise, long concessionPaise, long paidPaise, long pendingClearancePaise,
                           long duePaise, LocalDate dueDate, DueStatus status, LineStatus lineStatus, long lateFinePaise, String reason,
                           String cancelReason) {
    }

    public record TransactionView(UUID id, String receiptNumber, LocalDate paidOn, LocalDate realizedOn, long amountPaise, PaymentMode mode,
                                  PaymentStatus status, String referenceNo, String collectedBy, String statusReason) {
    }

    public record LedgerDetail(LedgerRow ledger, String academicYearName, List<LineView> lines, List<TransactionView> transactions,
                               List<ConcessionView> concessions, long lateFinePaise) {
    }

    public record AddChargeRequest(@NotNull UUID feeHeadId, @NotNull @Positive Long amountPaise, LocalDate dueDate,
                                   @Size(max = 200) String label, @NotBlank @Size(max = 500) String reason) {
    }

    // ── Payments and receipts ───────────────────────────────────────────────────────

    public record Allocation(@NotNull UUID lineId, @NotNull @Positive Long amountPaise) {
    }

    /** @param channels empty or null = the channels of the FEE_PAYMENT_RECEIVED template */
    public record SendReceipt(Set<NotificationChannel> channels) {
    }

    /**
     * @param allocation lines to pay and how much; omitted = oldest dues first
     * @param paidOn     default today; never in the future
     */
    public record CollectPaymentRequest(@NotNull UUID studentId, UUID academicYearId, @NotNull @Positive Long amountPaise,
                                        @NotNull PaymentMode mode, @Size(max = 100) String referenceNo, LocalDate chequeDate,
                                        @Size(max = 150) String bankName, LocalDate paidOn, List<@Valid Allocation> allocation,
                                        @Size(max = 500) String remarks, SendReceipt sendReceipt) {
    }

    public record AllocationView(UUID lineId, String label, long amountPaise) {
    }

    public record PaymentResult(UUID paymentId, String receiptNumber, PaymentStatus status, long amountPaise, List<AllocationView> allocations,
                                long newDuePaise, int receiptMessagesQueued) {
    }

    public enum ChequeOutcome { CLEARED, BOUNCED }

    public record ChequeStatusRequest(@NotNull ChequeOutcome status, LocalDate date, @Size(max = 500) String remarks) {
    }

    public record ReverseRequest(@NotBlank @Size(max = 500) String reason) {
    }

    public record SendReceiptRequest(Set<NotificationChannel> channels) {
    }

    public record ReceiptLine(UUID lineId, String label, String headName, String installment, long applicablePaise, long realizedPaise) {
    }

    /** The receipt modal / print. School name and address come from the school profile (master). */
    public record Receipt(UUID paymentId, String receiptNumber, PaymentStatus status, LocalDate paidOn, LocalDate realizedOn,
                          UUID studentId, String studentName, String admissionNumber, String className, String sectionName,
                          String invoiceNumber, String academicYearName, PaymentMode mode, String referenceNo, LocalDate chequeDate,
                          String bankName, long amountPaise, String amountInWords, List<ReceiptLine> lines, String collectedBy,
                          String remarks, String statusReason, long balanceDuePaise) {
    }

    public record DayBookRow(UUID paymentId, String receiptNumber, LocalDate paidOn, LocalDate realizedOn, UUID studentId, String studentName,
                             String admissionNumber, String className, String sectionName, long amountPaise, PaymentMode mode,
                             String referenceNo, PaymentStatus status, String collectedBy) {
    }

    public record ModeTotal(PaymentMode mode, long count, long amountPaise) {
    }

    /** Counter closing: totals exclude bounced and reversed payments. */
    public record DayBook(LocalDate from, LocalDate to, long totalPaise, List<ModeTotal> byMode, Page<DayBookRow> payments) {
    }

    // ── Defaulters and reminders ────────────────────────────────────────────────────

    public record BucketCount(AgeingBucket bucket, long count, long overduePaise) {
    }

    public record Defaulters(LocalDate asOf, List<BucketCount> buckets, Page<LedgerRow> content) {
    }

    /**
     * Who to remind: the given ledgers, or everyone matching {@code filter}. Overdue ledgers get FEE_OVERDUE, the others FEE_DUE_REMINDER
     * for their next due date.
     *
     * @param channels empty or null = the template's channels
     */
    public record ReminderRequest(List<UUID> ledgerIds, ReminderFilter filter, Set<NotificationChannel> channels) {
    }

    public record ReminderFilter(UUID academicYearId, UUID classId, UUID sectionId, AgeingBucket bucket, Long minDuePaise) {
    }

    public record ReminderResult(int ledgers, int ledgersWithoutContact, int ledgersNothingDue, int messagesQueued, List<String> notes) {
    }

    // ── Concession requests ─────────────────────────────────────────────────────────

    /** {@code percent} / {@code amountPaise} override the concession type's value (default: the type's). */
    public record CreateConcessionRequest(@NotNull UUID studentId, UUID academicYearId, @NotNull UUID concessionTypeId,
                                          @DecimalMin(value = "0.01") @DecimalMax("100") BigDecimal percent, @Positive Long amountPaise,
                                          @NotBlank @Size(max = 1000) String reason) {
    }

    public record ApproveRequest(@Size(max = 500) String remarks) {
    }

    public record RejectRequest(@NotBlank @Size(max = 500) String remarks) {
    }

    /** @param effectiveFrom lines due on or after this date lose the concession (default today) */
    public record RevokeRequest(@NotBlank @Size(max = 500) String reason, LocalDate effectiveFrom) {
    }

    public record ConcessionView(UUID id, String requestNumber, UUID studentId, String studentName, String admissionNumber, String className,
                                 UUID concessionTypeId, String typeCode, String typeName, ConcessionKind kind, BigDecimal percent,
                                 Long amountPaise, UUID feeHeadId, String headName, String reason, ConcessionRequestStatus status,
                                 long appliedPaise, String requestedBy, Instant requestedAt, String reviewedBy, Instant reviewedAt,
                                 String reviewRemarks, Instant revokedAt, String revokeReason, LocalDate effectiveFrom) {
    }

    // ── Summary ─────────────────────────────────────────────────────────────────────

    public record ModeShare(PaymentMode mode, long count, long amountPaise, BigDecimal percent) {
    }

    /**
     * Year totals (filtered by class / section) plus what was collected between {@code from} and {@code to}.
     *
     * @param collectionRate paid ÷ net payable of everything that has fallen due by {@code to}, in %
     */
    public record FeeSummary(UUID academicYearId, LocalDate from, LocalDate to, long ledgers, long totalInvoicedPaise, long concessionPaise,
                             long netPayablePaise, long paidPaise, long pendingClearancePaise, long outstandingPaise, long overduePaise,
                             long overdueLedgers, long dueByToPaise, BigDecimal collectionRate, long collectedInWindowPaise,
                             List<ModeShare> modeSplit, long pendingConcessionRequests) {
    }

    public record CollectionRow(String key, String label, long count, long amountPaise) {
    }

    /** Money realized between the dates, grouped by DAY, MODE, COLLECTOR or FEE_HEAD. */
    public record CollectionsReport(UUID academicYearId, LocalDate from, LocalDate to, String groupBy, long totalPaise, List<CollectionRow> rows) {
    }

    // ── Dashboard ──────────────────────────────────────────────────────────────────

    /**
     * Fee figures of the dashboard window.
     *
     * @param collectedPaise         realized between from and to
     * @param targetPaise            net payable of lines falling due between from and to
     * @param collectionRate         paid ÷ net payable of everything due by {@code to}, in % (null when nothing is due yet)
     * @param previousCollectedPaise realized in the window of the same length just before
     */
    public record DashboardFees(long collectedPaise, long targetPaise, BigDecimal collectionRate, long previousCollectedPaise, long overduePaise,
                                long overdueLedgers, long ledgers, long collectedTodayPaise, long paymentsToday, long chequesPending) {
    }

    public record FeeTrendPoint(LocalDate date, long collectedPaise, long cumulativePaise) {
    }

    /** Daily collections over the window (every day present, zero when nothing came in). */
    public record FeeTrend(UUID academicYearId, LocalDate from, LocalDate to, long totalPaise, List<FeeTrendPoint> points) {
    }

    public record FeeGroup(UUID id, String name, long billedPaise, long concessionPaise, long netPayablePaise, long paidPaise,
                           long pendingClearancePaise, long duePaise, long overduePaise, BigDecimal collectionRate) {
    }

    /** The year's billing per fee head and per class. {@code collectionRate} here is paid ÷ net payable of the whole year. */
    public record FeeBreakdown(UUID academicYearId, LocalDate asOf, List<FeeGroup> byHead, List<FeeGroup> byClass) {
    }
}
