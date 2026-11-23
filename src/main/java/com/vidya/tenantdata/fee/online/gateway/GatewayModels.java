package com.vidya.tenantdata.fee.online.gateway;

import com.vidya.tenantdata.fee.online.enums.CheckoutKind;
import com.vidya.tenantdata.fee.online.enums.CheckoutStatus;
import com.vidya.tenantdata.fee.online.enums.GatewayEnvironment;
import com.vidya.tenantdata.fee.online.enums.GatewayProvider;
import com.vidya.tenantdata.fee.online.enums.MandateMethod;
import com.vidya.tenantdata.fee.online.enums.MandateStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** What goes to and comes back from a gateway. Money is paise everywhere; each gateway converts. */
public final class GatewayModels {

    private GatewayModels() {
    }

    /** The school's keys, decrypted for one call. */
    public record Credentials(GatewayProvider provider, GatewayEnvironment environment, String keyId, String secret, String webhookSecret) {

        public boolean production() {
            return environment == GatewayEnvironment.PRODUCTION;
        }

        @Override
        public String toString() {
            return "Credentials[" + provider + ", " + environment + ", " + keyId + "]";
        }
    }

    public record Payer(String name, String email, String phone) {
    }

    /** @param reference our id, sent as the gateway's subscription id / receipt */
    public record MandateSetup(String reference, MandateMethod method, Payer payer, long maxAmountPaise, LocalDate expiresOn, String returnUrl,
                               String description) {
    }

    /**
     * @param gatewayMandateId Cashfree cf_subscription_id; Razorpay token (known once the payer authorises)
     * @param gatewayAuthId    Cashfree subscription session id (for the JS checkout); Razorpay authorisation link (invoice) id
     * @param authUrl          link the payer opens to authorise (Razorpay); Cashfree sends its own link by e-mail / SMS
     */
    public record MandateSession(String gatewayMandateId, String gatewayCustomerId, String gatewayAuthId, String authUrl, MandateStatus status) {
    }

    public record MandateRef(String reference, String gatewayMandateId, String gatewayCustomerId, String gatewayAuthId) {
    }

    public record MandateState(MandateStatus status, String gatewayMandateId, String reason) {
    }

    /** @param reference our debit reference: Cashfree payment_id, Razorpay order receipt */
    public record Charge(String reference, MandateRef mandate, Payer payer, long amountPaise, LocalDate debitOn, String remarks) {
    }

    public record ChargeSubmitted(String gatewayPaymentId, String gatewayOrderId) {
    }

    public record CheckoutSetup(String reference, CheckoutKind kind, Payer payer, long amountPaise, boolean allowPartial, long minPartialPaise,
                                Instant expiresAt, String purpose, String returnUrl) {
    }

    /** @param sessionId Cashfree payment_session_id; Razorpay order id (open Checkout with the key id) */
    public record CheckoutSession(String gatewayId, String url, String sessionId) {
    }

    /** A verified webhook and what it says. */
    public record Webhook(String eventId, String type, List<GatewayEvent> events) {
    }

    public sealed interface GatewayEvent permits MandateUpdate, PaymentReport, LinkReport {
    }

    /** A mandate changed state; matched by reference, then gateway mandate id, then authorisation id. */
    public record MandateUpdate(String reference, String gatewayMandateId, String gatewayCustomerId, String gatewayAuthId, MandateStatus status,
                                String reason) implements GatewayEvent {
    }

    /**
     * A payment of an auto-debit or of a checkout order succeeded or failed; matched by reference (our debit / order reference),
     * else by the gateway order id.
     */
    public record PaymentReport(String reference, String gatewayOrderId, String gatewayPaymentId, long amountPaise, String method, boolean success,
                                String reason) implements GatewayEvent {
    }

    /**
     * A payment link was paid (in full or part) or closed. {@code cumulative}: amountPaise is the total paid on the link so far
     * (Cashfree), else this payment's amount (Razorpay).
     */
    public record LinkReport(String reference, String linkId, String gatewayPaymentId, long amountPaise, boolean cumulative, CheckoutStatus status,
                             String method) implements GatewayEvent {
    }
}
