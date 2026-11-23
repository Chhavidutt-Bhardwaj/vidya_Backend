package com.vidya.tenantdata.fee.online.controller;

import com.vidya.common.api.ApiResponse;
import com.vidya.tenantdata.fee.online.dto.OnlineFeeDtos.CheckoutView;
import com.vidya.tenantdata.fee.online.dto.OnlineFeeDtos.CreateCheckoutRequest;
import com.vidya.tenantdata.fee.online.dto.OnlineFeeDtos.CreateMandateRequest;
import com.vidya.tenantdata.fee.online.dto.OnlineFeeDtos.DebitSummary;
import com.vidya.tenantdata.fee.online.dto.OnlineFeeDtos.GatewayAccountView;
import com.vidya.tenantdata.fee.online.dto.OnlineFeeDtos.MandateView;
import com.vidya.tenantdata.fee.online.dto.OnlineFeeDtos.ReasonRequest;
import com.vidya.tenantdata.fee.online.dto.OnlineFeeDtos.RunRequest;
import com.vidya.tenantdata.fee.online.dto.OnlineFeeDtos.RunView;
import com.vidya.tenantdata.fee.online.dto.OnlineFeeDtos.SaveGatewayRequest;
import com.vidya.tenantdata.fee.online.dto.OnlineFeeDtos.SettingsView;
import com.vidya.tenantdata.fee.online.dto.OnlineFeeDtos.StudentOnlineFees;
import com.vidya.tenantdata.fee.online.dto.OnlineFeeDtos.UpdateSettingsRequest;
import com.vidya.tenantdata.fee.online.enums.DebitStatus;
import com.vidya.tenantdata.fee.online.enums.GatewayProvider;
import com.vidya.tenantdata.fee.online.enums.MandateStatus;
import com.vidya.tenantdata.fee.online.repository.OnlineFeeQueries.DebitRow;
import com.vidya.tenantdata.fee.online.service.AutoDebitService;
import com.vidya.tenantdata.fee.online.service.CheckoutService;
import com.vidya.tenantdata.fee.online.service.MandateService;
import com.vidya.tenantdata.fee.online.service.OnlineFeeConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Online fees through the school's own Cashfree / Razorpay account: gateway keys and auto-debit settings, eNACH / UPI Autopay mandates,
 * the monthly auto-debit (10th by default, siblings higher class first), payment links and checkout orders. Money in paise.
 * See tenantdata/fee/online/ONLINE_FEES.md.
 */
@RestController
@RequestMapping("/api/v1/fees/online")
@RequiredArgsConstructor
@Tag(name = "Fees – online", description = "Gateways, auto-debit mandates and monthly debits, payment links and orders")
public class OnlineFeeController {

    private static final String READ_ALL = "hasAuthority('FEE_READ') and @dataAccess.hasFullScope(authentication, 'FEE')";
    private static final String MANAGE = "hasAuthority('FEE_MANAGE') and @dataAccess.hasFullScope(authentication, 'FEE')";
    private static final String APPROVE = "hasAuthority('FEE_APPROVE') and @dataAccess.hasFullScope(authentication, 'FEE')";
    /** Office staff, or a parent paying for their own children (checked in the service). */
    private static final String PAY = "hasAnyAuthority('FEE_MANAGE', 'FEE_PAY')";

    private final OnlineFeeConfigService config;
    private final MandateService mandates;
    private final AutoDebitService autoDebit;
    private final CheckoutService checkouts;

    // ── Settings and gateway accounts ───────────────────────────────────────────────────

    @GetMapping("/settings")
    @PreAuthorize(READ_ALL)
    @Operation(summary = "Online fee and auto-debit settings")
    public ResponseEntity<ApiResponse<SettingsView>> settings() {
        return ok("FEE_ONLINE_SETTINGS", "Online fee settings", config.view());
    }

    @PutMapping("/settings")
    @PreAuthorize(APPROVE)
    @Operation(summary = "Change online fee and auto-debit settings", description = "Debit day (1–28, default 10), notice days, invitation lead, "
            + "sibling gap, retries, NACH bounce charge and GST %, gateways. Fields left out are kept")
    public ResponseEntity<ApiResponse<SettingsView>> updateSettings(@Valid @RequestBody UpdateSettingsRequest request) {
        return ok("FEE_ONLINE_SETTINGS_UPDATED", "Online fee settings saved", config.update(request));
    }

    @GetMapping("/gateways")
    @PreAuthorize(APPROVE)
    @Operation(summary = "The school's gateway accounts", description = "Secrets are never returned; webhookUrl goes into the gateway dashboard")
    public ResponseEntity<ApiResponse<List<GatewayAccountView>>> gateways() {
        return ok("FEE_GATEWAYS", "Gateway accounts", config.gateways());
    }

    @PutMapping("/gateways/{provider}")
    @PreAuthorize(APPROVE)
    @Operation(summary = "Connect or update a gateway account", description = "The school's own keys (stored encrypted). Omit secrets to keep them")
    public ResponseEntity<ApiResponse<GatewayAccountView>> saveGateway(@PathVariable GatewayProvider provider, @Valid @RequestBody SaveGatewayRequest request) {
        return ok("FEE_GATEWAY_SAVED", "Gateway account saved", config.saveGateway(provider, request));
    }

    @DeleteMapping("/gateways/{provider}")
    @PreAuthorize(APPROVE)
    @Operation(summary = "Disconnect a gateway account")
    public ResponseEntity<ApiResponse<Void>> deleteGateway(@PathVariable GatewayProvider provider) {
        config.deleteGateway(provider);
        return ok("FEE_GATEWAY_DELETED", "Gateway account removed", null);
    }

    // ── Mandates ────────────────────────────────────────────────────────────────────────

    @PostMapping("/mandates")
    @PreAuthorize(PAY)
    @Operation(summary = "Set up an auto-debit mandate", description = "eNACH or UPI Autopay for a payer and their children (default: all not yet "
            + "covered). Razorpay returns authUrl for the payer; Cashfree sends its own link and returns sessionId for its checkout")
    public ResponseEntity<ApiResponse<MandateView>> createMandate(@Valid @RequestBody CreateMandateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("FEE_MANDATE_CREATED", "Mandate created", mandates.create(request)));
    }

    @GetMapping("/mandates")
    @PreAuthorize(READ_ALL)
    @Operation(summary = "Mandates of the school", description = "Newest first; filter by status")
    public ResponseEntity<ApiResponse<Page<MandateView>>> mandates(@RequestParam(required = false) Set<MandateStatus> status,
                                                                  @RequestParam(defaultValue = "0") int page,
                                                                  @RequestParam(defaultValue = "20") int size) {
        return ok("FEE_MANDATES", "Mandates", mandates.list(status, page, size));
    }

    @GetMapping("/mandates/mine")
    @PreAuthorize("hasAuthority('FEE_PAY')")
    @Operation(summary = "The caller's own mandates (parent app)")
    public ResponseEntity<ApiResponse<List<MandateView>>> myMandates() {
        return ok("FEE_MANDATES", "Your mandates", mandates.mine());
    }

    @GetMapping("/mandates/{id}")
    @PreAuthorize("hasAuthority('FEE_READ')")
    @Operation(summary = "One mandate", description = "School-wide fee scope, or the payer")
    public ResponseEntity<ApiResponse<MandateView>> mandate(@PathVariable UUID id) {
        return ok("FEE_MANDATE", "Mandate", mandates.get(id));
    }

    @PostMapping("/mandates/{id}/refresh")
    @PreAuthorize("hasAuthority('FEE_READ')")
    @Operation(summary = "Ask the gateway for the mandate's state now", description = "For a missed webhook")
    public ResponseEntity<ApiResponse<MandateView>> refreshMandate(@PathVariable UUID id) {
        return ok("FEE_MANDATE", "Mandate refreshed", mandates.refresh(id));
    }

    @PostMapping("/mandates/{id}/cancel")
    @PreAuthorize(PAY)
    @Operation(summary = "Cancel a mandate", description = "At the gateway too. Debits not sent yet are cancelled; the children can get a new mandate")
    public ResponseEntity<ApiResponse<MandateView>> cancelMandate(@PathVariable UUID id, @Valid @RequestBody ReasonRequest request) {
        return ok("FEE_MANDATE_CANCELLED", "Mandate cancelled", mandates.cancel(id, request.reason()));
    }

    // ── Auto-debits ─────────────────────────────────────────────────────────────────────

    @GetMapping("/debits")
    @PreAuthorize(READ_ALL)
    @Operation(summary = "Auto-debits", description = "month = YYYY-MM; newest first, siblings in class order")
    public ResponseEntity<ApiResponse<Page<DebitRow>>> debits(@RequestParam(required = false) YearMonth month,
                                                             @RequestParam(required = false) Set<DebitStatus> status,
                                                             @RequestParam(required = false) UUID mandateId,
                                                             @RequestParam(required = false) UUID studentId,
                                                             @RequestParam(defaultValue = "0") int page,
                                                             @RequestParam(defaultValue = "50") int size) {
        return ok("FEE_DEBITS", "Auto-debits", autoDebit.list(month, status, mandateId, studentId, page, size));
    }

    @GetMapping("/debits/summary")
    @PreAuthorize(READ_ALL)
    @Operation(summary = "Auto-debit totals of a month", description = "Count and amount per status, mandates active / pending")
    public ResponseEntity<ApiResponse<DebitSummary>> debitSummary(@RequestParam(required = false) YearMonth month) {
        return ok("FEE_DEBIT_SUMMARY", "Auto-debit summary", autoDebit.summary(month));
    }

    @PostMapping("/debits/run")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Run the auto-debit job now", description = "Plans, invites, sends notices and submits what is due; safe to repeat. "
            + "planMonth (YYYY-MM) plans that month even outside its window (catch-up)")
    public ResponseEntity<ApiResponse<RunView>> run(@Valid @RequestBody(required = false) RunRequest request) {
        YearMonth plan = request == null || request.planMonth() == null ? null : YearMonth.parse(request.planMonth());
        return ok("FEE_DEBIT_RUN", "Auto-debit run finished", autoDebit.run("MANUAL", plan));
    }

    @GetMapping("/debits/runs")
    @PreAuthorize(READ_ALL)
    @Operation(summary = "Recent auto-debit runs")
    public ResponseEntity<ApiResponse<List<RunView>>> runs(@RequestParam(defaultValue = "20") int limit) {
        return ok("FEE_DEBIT_RUNS", "Auto-debit runs", autoDebit.runs(limit));
    }

    @PostMapping("/debits/{id}/cancel")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Skip a scheduled debit", description = "Only before it is sent to the bank")
    public ResponseEntity<ApiResponse<DebitRow>> cancelDebit(@PathVariable UUID id, @Valid @RequestBody ReasonRequest request) {
        return ok("FEE_DEBIT_CANCELLED", "Debit cancelled", autoDebit.cancel(id, request.reason()));
    }

    // ── Payment links and orders ────────────────────────────────────────────────────────

    @PostMapping("/checkouts")
    @PreAuthorize(PAY)
    @Operation(summary = "Create a payment link or checkout order", description = "For one or more children of a payer. Money received is applied "
            + "higher class first; a child not fully covered stays part paid")
    public ResponseEntity<ApiResponse<CheckoutView>> createCheckout(@Valid @RequestBody CreateCheckoutRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("FEE_CHECKOUT_CREATED", "Payment created", checkouts.create(request)));
    }

    @GetMapping("/checkouts/{id}")
    @PreAuthorize("hasAuthority('FEE_READ')")
    @Operation(summary = "One payment link / order with what each child got")
    public ResponseEntity<ApiResponse<CheckoutView>> checkout(@PathVariable UUID id) {
        return ok("FEE_CHECKOUT", "Payment", checkouts.get(id));
    }

    @PostMapping("/checkouts/{id}/cancel")
    @PreAuthorize(PAY)
    @Operation(summary = "Cancel an unpaid payment link / order")
    public ResponseEntity<ApiResponse<CheckoutView>> cancelCheckout(@PathVariable UUID id) {
        return ok("FEE_CHECKOUT_CANCELLED", "Payment cancelled", checkouts.cancel(id));
    }

    // ── Per student (parent app) ────────────────────────────────────────────────────────

    @GetMapping("/students/{studentId}")
    @PreAuthorize("hasAuthority('FEE_READ') and @dataAccess.canAccessStudent(authentication, #studentId, 'FEE')")
    @Operation(summary = "A child's online fees", description = "Mandate covering them, auto-debits, recent links / orders, next debit day")
    public ResponseEntity<ApiResponse<StudentOnlineFees>> student(@PathVariable UUID studentId) {
        SettingsView s = config.view();
        return ok("FEE_STUDENT_ONLINE", "Online fees", new StudentOnlineFees(studentId, mandates.forStudent(studentId).orElse(null),
                autoDebit.list(null, null, null, studentId, 0, 24).getContent(), checkouts.forStudent(studentId), s.nextDebitDate(), s.enabled()));
    }

    private static <T> ResponseEntity<ApiResponse<T>> ok(String code, String message, T data) {
        return ResponseEntity.ok(ApiResponse.success(code, message, data));
    }
}
