package com.vidya.tenantdata.fee.controller;

import com.vidya.common.api.ApiResponse;
import com.vidya.tenantdata.fee.dto.FeeDtos.AddChargeRequest;
import com.vidya.tenantdata.fee.dto.FeeDtos.ApproveRequest;
import com.vidya.tenantdata.fee.dto.FeeDtos.ChequeStatusRequest;
import com.vidya.tenantdata.fee.dto.FeeDtos.CollectPaymentRequest;
import com.vidya.tenantdata.fee.dto.FeeDtos.CollectionsReport;
import com.vidya.tenantdata.fee.dto.FeeDtos.ConcessionView;
import com.vidya.tenantdata.fee.dto.FeeDtos.CreateConcessionRequest;
import com.vidya.tenantdata.fee.dto.FeeDtos.DayBook;
import com.vidya.tenantdata.fee.dto.FeeDtos.Defaulters;
import com.vidya.tenantdata.fee.dto.FeeDtos.FeeSummary;
import com.vidya.tenantdata.fee.dto.FeeDtos.GenerateInvoicesRequest;
import com.vidya.tenantdata.fee.dto.FeeDtos.GenerateInvoicesResult;
import com.vidya.tenantdata.fee.dto.FeeDtos.LedgerDetail;
import com.vidya.tenantdata.fee.dto.FeeDtos.LedgerRow;
import com.vidya.tenantdata.fee.dto.FeeDtos.LineView;
import com.vidya.tenantdata.fee.dto.FeeDtos.PaymentResult;
import com.vidya.tenantdata.fee.dto.FeeDtos.Receipt;
import com.vidya.tenantdata.fee.dto.FeeDtos.RejectRequest;
import com.vidya.tenantdata.fee.dto.FeeDtos.ReminderRequest;
import com.vidya.tenantdata.fee.dto.FeeDtos.ReminderResult;
import com.vidya.tenantdata.fee.dto.FeeDtos.ReverseRequest;
import com.vidya.tenantdata.fee.dto.FeeDtos.RevokeRequest;
import com.vidya.tenantdata.fee.dto.FeeDtos.SendReceiptRequest;
import com.vidya.tenantdata.fee.dto.FeeDtos.TransactionView;
import com.vidya.tenantdata.fee.enums.AgeingBucket;
import com.vidya.tenantdata.fee.enums.ConcessionRequestStatus;
import com.vidya.tenantdata.fee.enums.DueStatus;
import com.vidya.tenantdata.fee.enums.PaymentMode;
import com.vidya.tenantdata.fee.enums.PaymentStatus;
import com.vidya.tenantdata.fee.repository.FeeQueries.CollectionGroup;
import com.vidya.tenantdata.fee.service.FeeConcessionService;
import com.vidya.tenantdata.fee.service.FeeInvoiceService;
import com.vidya.tenantdata.fee.service.FeeLedgerService;
import com.vidya.tenantdata.fee.service.FeePaymentService;
import com.vidya.tenantdata.fee.service.FeeReminderService;
import com.vidya.tenantdata.fee.service.FeeReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Fee collection: invoices, ledgers, counter payments and receipts, defaulters and reminders, concession requests, summary.
 * School-wide endpoints need FEE_SCOPE_ALL; a ledger or receipt by id is also open to whoever may see that student (parents).
 * Money is in paise. See tenantdata/fee/FEES.md.
 */
@RestController
@RequestMapping("/api/v1/fees")
@RequiredArgsConstructor
@Tag(name = "Fees", description = "Fee invoices, ledgers, payments, receipts, defaulters, reminders and concessions")
public class FeeController {

    private static final String READ_ALL = "hasAuthority('FEE_READ') and @dataAccess.hasFullScope(authentication, 'FEE')";
    private static final String MANAGE = "hasAuthority('FEE_MANAGE') and @dataAccess.hasFullScope(authentication, 'FEE')";
    private static final String APPROVE = "hasAuthority('FEE_APPROVE') and @dataAccess.hasFullScope(authentication, 'FEE')";

    private final FeeInvoiceService invoices;
    private final FeeLedgerService ledgers;
    private final FeePaymentService payments;
    private final FeeReminderService reminders;
    private final FeeConcessionService concessions;
    private final FeeReportService reports;

    // ── Summary and reports ─────────────────────────────────────────────────────────

    @GetMapping("/summary")
    @PreAuthorize(READ_ALL)
    @Operation(summary = "Fee KPI cards", description = "Year totals (invoiced, concession, paid, outstanding, overdue) and collections in the window "
            + "(default: year start to today) with the split by payment mode")
    public ResponseEntity<ApiResponse<FeeSummary>> summary(@RequestParam(required = false) UUID academicYearId,
                                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                           @RequestParam(required = false) UUID classId,
                                                           @RequestParam(required = false) UUID sectionId) {
        return ok("FEE_SUMMARY", "Fee summary", reports.summary(academicYearId, from, to, classId, sectionId));
    }

    @GetMapping("/reports/collections")
    @PreAuthorize(READ_ALL)
    @Operation(summary = "Collections report", description = "Money realized between the dates (default month to date) grouped by DAY, MODE, COLLECTOR or FEE_HEAD")
    public ResponseEntity<ApiResponse<CollectionsReport>> collections(@RequestParam(required = false) UUID academicYearId,
                                                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                                      @RequestParam(required = false) CollectionGroup groupBy) {
        return ok("FEE_COLLECTIONS", "Collections", reports.collections(academicYearId, from, to, groupBy));
    }

    // ── Invoices and ledgers ────────────────────────────────────────────────────────

    @PostMapping("/invoices/generate")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Bill students from the class fee structures",
            description = "Adds the missing lines (per compulsory head and instalment) to each student's ledger; safe to repeat. dryRun saves nothing")
    public ResponseEntity<ApiResponse<GenerateInvoicesResult>> generate(@Valid @RequestBody GenerateInvoicesRequest request) {
        GenerateInvoicesResult r = invoices.generate(request);
        return ok("FEE_INVOICES_GENERATED", r.dryRun() ? "Invoice preview" : "Invoices generated", r);
    }

    @GetMapping("/ledgers")
    @PreAuthorize(READ_ALL)
    @Operation(summary = "Student ledgers", description = "Search by name, admission no., invoice no., roll no. or parent name / phone. "
            + "feeHeadId / installmentId restrict the totals to those lines. sort: duePaise, overduePaise, paidPaise, netPayablePaise, studentName, "
            + "invoiceNumber, ageingDays, className, admissionNumber (default duePaise,desc)")
    public ResponseEntity<ApiResponse<Page<LedgerRow>>> ledgers(@RequestParam(required = false) UUID academicYearId,
                                                                @RequestParam(required = false) String search,
                                                                @RequestParam(required = false) UUID classId,
                                                                @RequestParam(required = false) UUID sectionId,
                                                                @RequestParam(required = false) Set<DueStatus> status,
                                                                @RequestParam(required = false) UUID feeHeadId,
                                                                @RequestParam(required = false) UUID installmentId,
                                                                @RequestParam(defaultValue = "0") int page,
                                                                @RequestParam(defaultValue = "20") int size,
                                                                @RequestParam(required = false) List<String> sort) {
        return ok("FEE_LEDGERS", "Ledgers", ledgers.list(academicYearId, search, classId, sectionId, status, feeHeadId, installmentId, page, size, sort));
    }

    @GetMapping("/ledgers/{ledgerId}")
    @PreAuthorize("hasAuthority('FEE_READ')")
    @Operation(summary = "One student's ledger", description = "Lines (with the informational late fine), payments and concessions")
    public ResponseEntity<ApiResponse<LedgerDetail>> ledger(@PathVariable UUID ledgerId) {
        return ok("FEE_LEDGER", "Ledger", ledgers.detail(ledgerId));
    }

    @PostMapping("/ledgers/{ledgerId}/charges")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Add a one-off charge", description = "Late fee, lost ID card, transport for one student ... (default due today)")
    public ResponseEntity<ApiResponse<LineView>> addCharge(@PathVariable UUID ledgerId, @Valid @RequestBody AddChargeRequest request) {
        return created("FEE_CHARGE_ADDED", "Charge added", invoices.addCharge(ledgerId, request));
    }

    @DeleteMapping("/ledgers/{ledgerId}/lines/{lineId}")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Cancel a ledger line", description = "Only a line with nothing paid or pending; the line is kept, marked CANCELLED")
    public ResponseEntity<ApiResponse<LineView>> cancelLine(@PathVariable UUID ledgerId, @PathVariable UUID lineId, @RequestParam String reason) {
        return ok("FEE_LINE_CANCELLED", "Line cancelled", invoices.cancelLine(ledgerId, lineId, reason));
    }

    // ── Payments and receipts ───────────────────────────────────────────────────────

    @PostMapping("/payments")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Record a counter payment", description = "Allocated to the given lines, else oldest dues first. Cheques stay "
            + "PENDING_CLEARANCE until cleared. sendReceipt queues the receipt message to guardians")
    public ResponseEntity<ApiResponse<PaymentResult>> collect(@Valid @RequestBody CollectPaymentRequest request) {
        return created("FEE_PAYMENT_RECORDED", "Payment recorded", payments.collect(request));
    }

    @GetMapping("/payments")
    @PreAuthorize(READ_ALL)
    @Operation(summary = "Payments day book", description = "Payments by payment date (default today), with totals per mode excluding bounced "
            + "and reversed ones")
    public ResponseEntity<ApiResponse<DayBook>> dayBook(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                        @RequestParam(required = false) PaymentMode mode,
                                                        @RequestParam(required = false) PaymentStatus status,
                                                        @RequestParam(required = false) UUID collectedBy,
                                                        @RequestParam(required = false) UUID classId,
                                                        @RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "50") int size) {
        return ok("FEE_DAY_BOOK", "Day book", payments.dayBook(from, to, mode, status, collectedBy, classId, page, size));
    }

    @PostMapping("/payments/{paymentId}/cheque-status")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Cheque cleared or bounced")
    public ResponseEntity<ApiResponse<TransactionView>> chequeStatus(@PathVariable UUID paymentId, @Valid @RequestBody ChequeStatusRequest request) {
        return ok("FEE_CHEQUE_STATUS", "Cheque " + request.status().name().toLowerCase(), payments.chequeStatus(paymentId, request));
    }

    @PostMapping("/payments/{paymentId}/reverse")
    @PreAuthorize(APPROVE)
    @Operation(summary = "Reverse a payment", description = "Takes the money back off its lines; the receipt stays, marked REVERSED")
    public ResponseEntity<ApiResponse<TransactionView>> reverse(@PathVariable UUID paymentId, @Valid @RequestBody ReverseRequest request) {
        return ok("FEE_PAYMENT_REVERSED", "Payment reversed", payments.reverse(paymentId, request.reason()));
    }

    @GetMapping("/receipts/{paymentId}")
    @PreAuthorize("hasAuthority('FEE_READ')")
    @Operation(summary = "Receipt", description = "The receipt of a payment: student, lines, amount in words, mode, reference, collected by")
    public ResponseEntity<ApiResponse<Receipt>> receipt(@PathVariable UUID paymentId) {
        return ok("FEE_RECEIPT", "Receipt", payments.receipt(paymentId));
    }

    @PostMapping("/receipts/{paymentId}/send")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Send the receipt to guardians again", description = "channels default to the FEE_PAYMENT_RECEIVED template's")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> sendReceipt(@PathVariable UUID paymentId,
                                                                        @RequestBody(required = false) SendReceiptRequest request) {
        int queued = payments.resend(paymentId, request == null ? null : request.channels());
        return ok("FEE_RECEIPT_SENT", queued + " message(s) queued", Map.of("queued", queued));
    }

    // ── Defaulters and reminders ────────────────────────────────────────────────────

    @GetMapping("/defaulters")
    @PreAuthorize(READ_ALL)
    @Operation(summary = "Defaulters and ageing", description = "Ledgers with something past due, with counts per ageing bucket "
            + "(days since the oldest unpaid due date). minDuePaise compares the overdue amount")
    public ResponseEntity<ApiResponse<Defaulters>> defaulters(@RequestParam(required = false) UUID academicYearId,
                                                              @RequestParam(required = false) UUID classId,
                                                              @RequestParam(required = false) UUID sectionId,
                                                              @RequestParam(required = false) AgeingBucket bucket,
                                                              @RequestParam(required = false) Long minDuePaise,
                                                              @RequestParam(required = false) String search,
                                                              @RequestParam(defaultValue = "0") int page,
                                                              @RequestParam(defaultValue = "20") int size,
                                                              @RequestParam(required = false) List<String> sort) {
        return ok("FEE_DEFAULTERS", "Defaulters", ledgers.defaulters(academicYearId, classId, sectionId, bucket, minDuePaise, search, page, size, sort));
    }

    @PostMapping("/reminders")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Remind guardians", description = "For the given ledgers or a filter: FEE_OVERDUE when overdue, otherwise FEE_DUE_REMINDER. "
            + "At most once a day per ledger, guardian and channel")
    public ResponseEntity<ApiResponse<ReminderResult>> remind(@RequestBody ReminderRequest request) {
        ReminderResult r = reminders.send(request);
        return ok("FEE_REMINDERS_SENT", r.messagesQueued() + " message(s) queued", r);
    }

    // ── Concession requests ─────────────────────────────────────────────────────────

    @GetMapping("/concession-requests")
    @PreAuthorize(READ_ALL)
    @Operation(summary = "Concession requests")
    public ResponseEntity<ApiResponse<Page<ConcessionView>>> concessionRequests(@RequestParam(required = false) UUID academicYearId,
                                                                                @RequestParam(required = false) ConcessionRequestStatus status,
                                                                                @RequestParam(required = false) UUID concessionTypeId,
                                                                                @RequestParam(required = false) UUID classId,
                                                                                @RequestParam(required = false) String search,
                                                                                @RequestParam(defaultValue = "0") int page,
                                                                                @RequestParam(defaultValue = "20") int size) {
        return ok("FEE_CONCESSIONS", "Concession requests", concessions.list(academicYearId, status, concessionTypeId, classId, search, page, size));
    }

    @GetMapping("/concession-requests/{id}")
    @PreAuthorize(READ_ALL)
    @Operation(summary = "One concession request")
    public ResponseEntity<ApiResponse<ConcessionView>> concessionRequest(@PathVariable UUID id) {
        return ok("FEE_CONCESSION", "Concession request", concessions.get(id));
    }

    @PostMapping("/concession-requests")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Ask for a concession", description = "Value defaults to the concession type's. A type that does not require approval "
            + "is approved at once")
    public ResponseEntity<ApiResponse<ConcessionView>> requestConcession(@Valid @RequestBody CreateConcessionRequest request) {
        return created("FEE_CONCESSION_REQUESTED", "Concession requested", concessions.create(request));
    }

    @PostMapping("/concession-requests/{id}/approve")
    @PreAuthorize(APPROVE)
    @Operation(summary = "Approve a concession", description = "By someone other than who asked; applied to the student's ledger at once")
    public ResponseEntity<ApiResponse<ConcessionView>> approve(@PathVariable UUID id, @Valid @RequestBody(required = false) ApproveRequest request) {
        return ok("FEE_CONCESSION_APPROVED", "Concession approved", concessions.approve(id, request));
    }

    @PostMapping("/concession-requests/{id}/reject")
    @PreAuthorize(APPROVE)
    @Operation(summary = "Reject a concession")
    public ResponseEntity<ApiResponse<ConcessionView>> reject(@PathVariable UUID id, @Valid @RequestBody RejectRequest request) {
        return ok("FEE_CONCESSION_REJECTED", "Concession rejected", concessions.reject(id, request));
    }

    @PostMapping("/concession-requests/{id}/revoke")
    @PreAuthorize(APPROVE)
    @Operation(summary = "Revoke an approved concession", description = "Lines due on or after effectiveFrom (default today) lose it")
    public ResponseEntity<ApiResponse<ConcessionView>> revoke(@PathVariable UUID id, @Valid @RequestBody RevokeRequest request) {
        return ok("FEE_CONCESSION_REVOKED", "Concession revoked", concessions.revoke(id, request));
    }

    private static <T> ResponseEntity<ApiResponse<T>> ok(String code, String message, T data) {
        return ResponseEntity.ok(ApiResponse.success(code, message, data));
    }

    private static <T> ResponseEntity<ApiResponse<T>> created(String code, String message, T data) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(code, message, data));
    }
}
