package com.vidya.tenantdata.fee.online.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vidya.tenantdata.fee.online.enums.CheckoutKind;
import com.vidya.tenantdata.fee.online.enums.CheckoutStatus;
import com.vidya.tenantdata.fee.online.enums.GatewayProvider;
import com.vidya.tenantdata.fee.online.enums.MandateMethod;
import com.vidya.tenantdata.fee.online.enums.MandateStatus;
import com.vidya.tenantdata.fee.online.gateway.GatewayModels.Charge;
import com.vidya.tenantdata.fee.online.gateway.GatewayModels.ChargeSubmitted;
import com.vidya.tenantdata.fee.online.gateway.GatewayModels.CheckoutSession;
import com.vidya.tenantdata.fee.online.gateway.GatewayModels.CheckoutSetup;
import com.vidya.tenantdata.fee.online.gateway.GatewayModels.Credentials;
import com.vidya.tenantdata.fee.online.gateway.GatewayModels.GatewayEvent;
import com.vidya.tenantdata.fee.online.gateway.GatewayModels.LinkReport;
import com.vidya.tenantdata.fee.online.gateway.GatewayModels.MandateRef;
import com.vidya.tenantdata.fee.online.gateway.GatewayModels.MandateSession;
import com.vidya.tenantdata.fee.online.gateway.GatewayModels.MandateSetup;
import com.vidya.tenantdata.fee.online.gateway.GatewayModels.MandateState;
import com.vidya.tenantdata.fee.online.gateway.GatewayModels.MandateUpdate;
import com.vidya.tenantdata.fee.online.gateway.GatewayModels.PaymentReport;
import com.vidya.tenantdata.fee.online.gateway.GatewayModels.Webhook;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Razorpay (as infi_payment's RazorpayBusiness): mandates through a registration (authorisation) link — emandate or UPI Autopay —
 * whose token is then charged with an order + recurring payment; payment links and orders for checkouts.
 * Webhooks: hex(HMAC-SHA256(body, webhook secret)) in X-Razorpay-Signature.
 */
@Component
public class RazorpayFeeGateway extends AbstractFeeGateway {

    static final String BASE = "https://api.razorpay.com/v1";
    /** Authorisation links stay open this long. */
    static final long AUTH_LINK_DAYS = 30;

    private final Clock clock;

    public RazorpayFeeGateway(GatewayHttp http, Clock clock) {
        super(http);
        this.clock = clock;
    }

    @Override
    public GatewayProvider provider() {
        return GatewayProvider.RAZORPAY;
    }

    private Map<String, String> headers(Credentials c) {
        Map<String, String> h = new LinkedHashMap<>();
        h.put("Authorization", "Basic " + Base64.getEncoder().encodeToString((c.keyId() + ":" + c.secret()).getBytes(StandardCharsets.UTF_8)));
        h.put("Content-Type", "application/json");
        return h;
    }

    @Override
    protected String errorMessage(JsonNode body) {
        return text(body, "error", "description");
    }

    // ── Mandates ───────────────────────────────────────────────────────────────────────

    @Override
    public MandateSession createMandate(Credentials c, MandateSetup s) {
        ObjectNode body = obj();
        ObjectNode customer = body.putObject("customer");
        customer.put("name", s.payer().name());
        if (s.payer().email() != null) {
            customer.put("email", s.payer().email());
        }
        customer.put("contact", s.payer().phone());
        body.put("type", "link");
        boolean upi = s.method() == MandateMethod.UPI;
        body.put("amount", upi ? 100 : 0);
        body.put("currency", "INR");
        body.put("description", s.description());
        ObjectNode reg = body.putObject("subscription_registration");
        reg.put("method", upi ? "upi" : "emandate");
        reg.put("max_amount", s.maxAmountPaise());
        reg.put("expire_at", s.expiresOn().atTime(LocalTime.of(23, 59)).atZone(IST).toEpochSecond());
        if (upi) {
            reg.put("frequency", "as_presented");
        }
        body.put("receipt", s.reference());
        body.put("email_notify", true);
        body.put("sms_notify", true);
        body.put("expire_by", clock.instant().getEpochSecond() + AUTH_LINK_DAYS * 86_400);
        body.putObject("notes").put("vidya_ref", s.reference());
        JsonNode r = call("POST", BASE + "/subscription_registration/auth_links", headers(c), body);
        return new MandateSession(null, text(r, "customer_id"), text(r, "id"), text(r, "short_url"), MandateStatus.PENDING);
    }

    @Override
    public MandateState fetchMandate(Credentials c, MandateRef m) {
        String token = m.gatewayMandateId();
        if (token == null && m.gatewayAuthId() != null) {
            JsonNode inv = call("GET", BASE + "/invoices/" + m.gatewayAuthId(), headers(c), null);
            String invStatus = text(inv, "status");
            if ("cancelled".equalsIgnoreCase(invStatus) || "expired".equalsIgnoreCase(invStatus)) {
                return new MandateState("expired".equalsIgnoreCase(invStatus) ? MandateStatus.EXPIRED : MandateStatus.CANCELLED, null,
                        "authorisation link " + invStatus);
            }
            String paymentId = text(inv, "payment_id");
            if (paymentId == null) {
                return new MandateState(MandateStatus.PENDING, null, "not authorised yet");
            }
            token = text(call("GET", BASE + "/payments/" + paymentId, headers(c), null), "token_id");
        }
        if (token == null || m.gatewayCustomerId() == null) {
            return new MandateState(MandateStatus.PENDING, token, "not authorised yet");
        }
        JsonNode t = call("GET", BASE + "/customers/" + m.gatewayCustomerId() + "/tokens/" + token, headers(c), null);
        String st = text(t, "recurring_details", "status");
        String reason = text(t, "recurring_details", "failure_reason");
        return new MandateState(tokenStatus(st), token, reason != null ? reason : st);
    }

    @Override
    public void cancelMandate(Credentials c, MandateRef m) {
        if (m.gatewayMandateId() != null && m.gatewayCustomerId() != null) {
            call("DELETE", BASE + "/customers/" + m.gatewayCustomerId() + "/tokens/" + m.gatewayMandateId(), headers(c), null);
        } else if (m.gatewayAuthId() != null) {
            call("POST", BASE + "/invoices/" + m.gatewayAuthId() + "/cancel", headers(c), obj());
        }
    }

    static MandateStatus tokenStatus(String s) {
        if (s == null) {
            return MandateStatus.PENDING;
        }
        return switch (s.toLowerCase()) {
            case "confirmed" -> MandateStatus.ACTIVE;
            case "rejected" -> MandateStatus.FAILED;
            case "cancelled" -> MandateStatus.CANCELLED;
            case "paused" -> MandateStatus.PAUSED;
            case "expired" -> MandateStatus.EXPIRED;
            default -> MandateStatus.PENDING; // initiated
        };
    }

    // ── Debits ─────────────────────────────────────────────────────────────────────────

    @Override
    public int presentationLeadDays() {
        return 0;
    }

    @Override
    public ChargeSubmitted charge(Credentials c, Charge ch) {
        ObjectNode order = obj();
        order.put("amount", ch.amountPaise());
        order.put("currency", "INR");
        order.put("receipt", ch.reference());
        order.put("payment_capture", true);
        order.putObject("notes").put("vidya_ref", ch.reference());
        String orderId = text(call("POST", BASE + "/orders", headers(c), order), "id");

        ObjectNode pay = obj();
        if (ch.payer().email() != null) {
            pay.put("email", ch.payer().email());
        }
        pay.put("contact", ch.payer().phone());
        pay.put("amount", ch.amountPaise());
        pay.put("currency", "INR");
        pay.put("order_id", orderId);
        pay.put("customer_id", ch.mandate().gatewayCustomerId());
        pay.put("token", ch.mandate().gatewayMandateId());
        pay.put("recurring", "1");
        pay.put("description", ch.remarks());
        pay.putObject("notes").put("vidya_ref", ch.reference());
        try {
            JsonNode r = call("POST", BASE + "/payments/create/recurring", headers(c), pay);
            return new ChargeSubmitted(text(r, "razorpay_payment_id"), orderId);
        } catch (GatewayException e) {
            // Keep the order id: a payment may still be reported against it
            throw new GatewayException(provider(), e.getHttpStatus(), e.getMessage() + " (order " + orderId + ")");
        }
    }

    // ── Links and orders ───────────────────────────────────────────────────────────────

    @Override
    public CheckoutSession createCheckout(Credentials c, CheckoutSetup s) {
        if (s.kind() == CheckoutKind.LINK) {
            ObjectNode body = obj();
            body.put("amount", s.amountPaise());
            body.put("currency", "INR");
            body.put("accept_partial", s.allowPartial());
            if (s.allowPartial()) {
                body.put("first_min_partial_amount", s.minPartialPaise());
            }
            body.put("reference_id", s.reference());
            body.put("description", s.purpose());
            ObjectNode cust = body.putObject("customer");
            cust.put("name", s.payer().name());
            cust.put("contact", s.payer().phone());
            if (s.payer().email() != null) {
                cust.put("email", s.payer().email());
            }
            ObjectNode notify = body.putObject("notify");
            notify.put("sms", false);
            notify.put("email", false);
            body.put("reminder_enable", false);
            if (s.returnUrl() != null) {
                body.put("callback_url", s.returnUrl());
                body.put("callback_method", "get");
            }
            if (s.expiresAt() != null) {
                body.put("expire_by", s.expiresAt().getEpochSecond());
            }
            body.putObject("notes").put("vidya_ref", s.reference());
            JsonNode r = call("POST", BASE + "/payment_links", headers(c), body);
            return new CheckoutSession(text(r, "id"), text(r, "short_url"), null);
        }
        ObjectNode body = obj();
        body.put("amount", s.amountPaise());
        body.put("currency", "INR");
        body.put("receipt", s.reference());
        body.put("partial_payment", false);
        body.putObject("notes").put("vidya_ref", s.reference());
        String id = text(call("POST", BASE + "/orders", headers(c), body), "id");
        return new CheckoutSession(id, null, id);
    }

    @Override
    public void cancelCheckout(Credentials c, CheckoutKind kind, String reference, String gatewayId) {
        if (kind == CheckoutKind.LINK && gatewayId != null) {
            call("POST", BASE + "/payment_links/" + gatewayId + "/cancel", headers(c), obj());
        }
        // Razorpay orders cannot be cancelled; unpaid ones lapse
    }

    // ── Webhooks ───────────────────────────────────────────────────────────────────────

    @Override
    public Webhook parseWebhook(Credentials c, Map<String, String> headers, String body) {
        String secret = c.webhookSecret();
        if (secret == null || secret.isBlank()) {
            throw GatewayException.badSignature(provider());
        }
        String expected = HexFormat.of().formatHex(hmacSha256(secret, body));
        if (!sameSignature(expected, headers.get("x-razorpay-signature"))) {
            throw GatewayException.badSignature(provider());
        }
        JsonNode root = parse(body);
        String event = text(root, "event");
        JsonNode payload = root.path("payload");
        JsonNode payment = payload.path("payment").path("entity");
        List<GatewayEvent> events = new ArrayList<>();
        switch (event == null ? "" : event) {
            case "invoice.paid" -> {
                // The payer authorised the registration link: the token exists but the bank may still have to confirm it
                JsonNode inv = payload.path("invoice").path("entity");
                events.add(new MandateUpdate(text(inv, "receipt"), text(payment, "token_id"), text(inv, "customer_id"), text(inv, "id"),
                        MandateStatus.PENDING, "authorised by the payer"));
            }
            case "invoice.expired" -> {
                JsonNode inv = payload.path("invoice").path("entity");
                events.add(new MandateUpdate(text(inv, "receipt"), null, text(inv, "customer_id"), text(inv, "id"), MandateStatus.EXPIRED,
                        "authorisation link expired"));
            }
            case "token.confirmed", "token.rejected", "token.cancelled", "token.paused" -> {
                JsonNode t = payload.path("token").path("entity");
                String reason = text(t, "recurring_details", "failure_reason");
                events.add(new MandateUpdate(null, text(t, "id"), text(t, "customer_id"), null, tokenStatus(event.substring("token.".length())),
                        reason != null ? reason : event));
            }
            case "payment.captured", "payment.failed" -> {
                boolean ok = event.equals("payment.captured");
                events.add(new PaymentReport(text(payment, "notes", "vidya_ref"), text(payment, "order_id"), text(payment, "id"),
                        payment.path("amount").asLong(0), text(payment, "method"), ok, ok ? null : text(payment, "error_description")));
            }
            case "payment_link.paid", "payment_link.partially_paid", "payment_link.expired", "payment_link.cancelled" -> {
                JsonNode link = payload.path("payment_link").path("entity");
                CheckoutStatus st = switch (event) {
                    case "payment_link.paid" -> CheckoutStatus.PAID;
                    case "payment_link.partially_paid" -> CheckoutStatus.PARTIALLY_PAID;
                    case "payment_link.expired" -> CheckoutStatus.EXPIRED;
                    default -> CheckoutStatus.CANCELLED;
                };
                boolean paid = st == CheckoutStatus.PAID || st == CheckoutStatus.PARTIALLY_PAID;
                events.add(new LinkReport(text(link, "reference_id"), text(link, "id"), paid ? text(payment, "id") : null,
                        paid ? payment.path("amount").asLong(0) : 0, false, st, text(payment, "method")));
            }
            default -> {
                // payment.authorized (captured follows), order.paid, refunds ...
            }
        }
        return new Webhook(blank(headers.get("x-razorpay-event-id")), event, events);
    }
}
