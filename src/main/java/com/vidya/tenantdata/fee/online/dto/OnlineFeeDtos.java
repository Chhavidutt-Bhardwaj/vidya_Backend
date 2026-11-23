package com.vidya.tenantdata.fee.online.dto;

import com.vidya.tenantdata.fee.online.enums.CheckoutKind;
import com.vidya.tenantdata.fee.online.enums.CheckoutStatus;
import com.vidya.tenantdata.fee.online.enums.DebitStatus;
import com.vidya.tenantdata.fee.online.enums.GatewayEnvironment;
import com.vidya.tenantdata.fee.online.enums.GatewayProvider;
import com.vidya.tenantdata.fee.online.enums.MandateMethod;
import com.vidya.tenantdata.fee.online.enums.MandateStatus;
import com.vidya.tenantdata.fee.online.repository.OnlineFeeQueries.DebitRow;
import com.vidya.tenantdata.fee.online.repository.OnlineFeeQueries.StudentBrief;
import com.vidya.tenantdata.operations.enums.NotificationChannel;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Requests and responses of online fees (gateways, mandates, auto-debits, payment links / orders). Money in paise. */
public final class OnlineFeeDtos {

    private OnlineFeeDtos() {
    }

    // ── Settings and gateway accounts ───────────────────────────────────────────────────

    /**
     * @param nextDebitDate         the next monthly debit day from today
     * @param bounceTotalPaise      bounce charge + GST added to the fees when a debit fails
     * @param serverKeyConfigured   false = this server cannot store gateway secrets (vidya.fees.online.secret-key missing)
     */
    public record SettingsView(boolean enabled, GatewayProvider mandateProvider, GatewayProvider paymentProvider, int debitDay, List<Integer> noticeDays,
                               int inviteDaysBefore, String mandateSetupUrl, int siblingGapDays, int maxAttempts, int retryAfterDays,
                               long bounceChargePaise, BigDecimal bounceGstPercent, long bounceTotalPaise, long mandateMaxAmountPaise,
                               int mandateValidityYears, int linkExpiryDays, String returnUrl, LocalDate nextDebitDate, boolean serverKeyConfigured,
                               Instant updatedAt) {
    }

    /** Fields left null are kept. */
    public record UpdateSettingsRequest(Boolean enabled, GatewayProvider mandateProvider, GatewayProvider paymentProvider,
                                        @Min(1) @Max(28) Integer debitDay, @Size(max = 5) List<@Min(1) @Max(15) Integer> noticeDays,
                                        @Min(0) @Max(27) Integer inviteDaysBefore, @Size(max = 500) String mandateSetupUrl,
                                        @Min(0) @Max(7) Integer siblingGapDays, @Min(1) @Max(4) Integer maxAttempts,
                                        @Min(1) @Max(15) Integer retryAfterDays, @PositiveOrZero Long bounceChargePaise,
                                        @DecimalMin("0") @DecimalMax("100") BigDecimal bounceGstPercent, @Positive Long mandateMaxAmountPaise,
                                        @Min(1) @Max(30) Integer mandateValidityYears, @Min(1) @Max(60) Integer linkExpiryDays,
                                        @Size(max = 500) String returnUrl) {
    }

    /** @param webhookUrl to paste into the gateway dashboard (all payment, subscription / token and link events) */
    public record GatewayAccountView(GatewayProvider provider, GatewayEnvironment environment, String keyId, boolean hasSecret, boolean hasWebhookSecret,
                                     boolean active, String webhookUrl, Instant updatedAt) {
    }

    /**
     * @param secret        Cashfree client secret / Razorpay key secret; required the first time, omit to keep
     * @param webhookSecret Razorpay webhook secret (Cashfree signs with the client secret); omit to keep
     */
    public record SaveGatewayRequest(@NotNull GatewayEnvironment environment, @NotBlank @Size(max = 200) String keyId, @Size(max = 500) String secret,
                                     @Size(max = 500) String webhookSecret, Boolean active) {
    }

    // ── Mandates ────────────────────────────────────────────────────────────────────────

    /**
     * @param guardianId     the payer's student_guardian row; omit when a parent sets up their own mandate
     * @param studentIds     children to cover; omit = all children of the payer not covered yet
     * @param provider       omit = the school's mandate gateway
     * @param maxAmountPaise largest single debit; omit = the school default
     */
    public record CreateMandateRequest(UUID guardianId, List<UUID> studentIds, @NotNull MandateMethod method, GatewayProvider provider,
                                       @Positive Long maxAmountPaise, Boolean notifyPayer) {
    }

    /**
     * @param authUrl   Razorpay: the link the payer opens to authorise. Cashfree: null (Cashfree e-mails / texts its own link;
     *                  an app can also open Cashfree's checkout with {@code sessionId})
     * @param students  covered children, higher class first
     */
    public record MandateView(UUID id, String reference, GatewayProvider provider, MandateMethod method, MandateStatus status, String payerName,
                              String payerEmail, String payerPhone, long maxAmountPaise, LocalDate expiresOn, String authUrl, String sessionId,
                              List<StudentBrief> students, Instant activatedAt, Instant endedAt, String endReason, Instant createdAt,
                              Instant lastSyncedAt) {
    }

    public record ReasonRequest(@NotBlank @Size(max = 500) String reason) {
    }

    // ── Auto-debits ─────────────────────────────────────────────────────────────────────

    public record StatusTotal(DebitStatus status, long count, long amountPaise, long bounceChargesPaise) {
    }

    public record DebitSummary(LocalDate month, LocalDate debitDate, List<StatusTotal> byStatus, long collectedPaise, long failedPaise,
                               long activeMandates, long pendingMandates) {
    }

    /** @param planMonth also plan this month's debits even if its planning window has passed (catch-up), "YYYY-MM" */
    public record RunRequest(@Pattern(regexp = "\\d{4}-\\d{2}") String planMonth) {
    }

    public record RunView(UUID id, LocalDate runDate, String trigger, String status, int planned, int invites, int notices, int submitted, int skipped,
                          int errors, String detail, Instant startedAt, Instant finishedAt) {
    }

    // ── Payment links and orders ────────────────────────────────────────────────────────

    /**
     * @param studentIds     children to pay for (siblings: a part payment goes to the higher class first)
     * @param amountPaise    omit = everything due on those ledgers
     * @param allowPartial   LINK only: the payer may pay less than the amount, several times
     * @param guardianId     payer's guardian row; omit = the caller (parent) or the first child's primary guardian
     * @param notifyChannels LINK: also send the link to the payer through these channels (empty = template channels)
     */
    public record CreateCheckoutRequest(@NotEmpty @Size(max = 10) List<UUID> studentIds, @NotNull CheckoutKind kind, @Positive Long amountPaise,
                                        Boolean allowPartial, @Positive Long minPartialPaise, UUID guardianId, Boolean notifyPayer,
                                        Set<NotificationChannel> notifyChannels, @Size(max = 500) String returnUrl) {
    }

    public record CheckoutChildView(UUID studentId, String studentName, String className, int rank, long duePaise, long paidPaise) {
    }

    /**
     * @param keyId     Razorpay key id, for opening Checkout with {@code sessionId} (= order id)
     * @param sessionId Cashfree payment_session_id / Razorpay order id (ORDER); Cashfree link id (LINK)
     */
    public record CheckoutView(UUID id, String reference, CheckoutKind kind, GatewayProvider provider, CheckoutStatus status, long amountPaise,
                               long paidPaise, long unallocatedPaise, boolean allowPartial, String payerName, String purpose, String url,
                               String sessionId, String keyId, Instant expiresAt, List<CheckoutChildView> children, Instant createdAt) {
    }

    /** The parent-app card of one child: mandate covering them, auto-debits, recent links / orders. */
    public record StudentOnlineFees(UUID studentId, MandateView mandate, List<DebitRow> debits, List<CheckoutView> checkouts, LocalDate nextDebitDate,
                                    boolean autoDebitEnabled) {
    }
}
