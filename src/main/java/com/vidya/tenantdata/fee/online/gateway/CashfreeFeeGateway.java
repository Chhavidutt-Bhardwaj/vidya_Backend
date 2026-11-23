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
import com.vidya.tenantdata.fee.online.gateway.GatewayModels.Payer;
import com.vidya.tenantdata.fee.online.gateway.GatewayModels.PaymentReport;
import com.vidya.tenantdata.fee.online.gateway.GatewayModels.Webhook;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cashfree PG (API version 2025-01-01), as infi_payment's CashfreeBusiness: ON_DEMAND subscriptions for mandates, subscription
 * payments (CHARGE) for debits, payment links and orders for checkouts. Webhooks: base64(HMAC-SHA256(timestamp + body, client secret)).
 */
@Component
public class CashfreeFeeGateway extends AbstractFeeGateway {

    static final String API_VERSION = "2025-01-01";
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    public CashfreeFeeGateway(GatewayHttp http) {
        super(http);
    }

    @Override
    public GatewayProvider provider() {
        return GatewayProvider.CASHFREE;
    }

    static String base(Credentials c) {
        return c.production() ? "https://api.cashfree.com/pg" : "https://sandbox.cashfree.com/pg";
    }

    private Map<String, String> headers(Credentials c, String idempotencyKey) {
        Map<String, String> h = new LinkedHashMap<>();
        h.put("x-client-id", c.keyId());
        h.put("x-client-secret", c.secret());
        h.put("x-api-version", API_VERSION);
        h.put("Content-Type", "application/json");
        h.put("Accept", "application/json");
        if (idempotencyKey != null) {
            h.put("x-idempotency-key", idempotencyKey);
        }
        return h;
    }

    @Override
    protected String errorMessage(JsonNode body) {
        return text(body, "message");
    }

    // ── Mandates ───────────────────────────────────────────────────────────────────────

    @Override
    public MandateSession createMandate(Credentials c, MandateSetup s) {
        ObjectNode body = obj();
        body.put("subscription_id", s.reference());
        body.set("customer_details", customer(s.payer()));
        ObjectNode plan = body.putObject("plan_details");
        plan.put("plan_name", "School fees " + s.reference());
        plan.put("plan_type", "ON_DEMAND");
        plan.put("plan_currency", "INR");
        plan.put("plan_max_amount", rupees(s.maxAmountPaise()));
        plan.put("plan_note", s.description());
        ObjectNode auth = body.putObject("authorization_details");
        auth.put("authorization_amount", 1);
        auth.put("authorization_amount_refund", true);
        auth.putArray("payment_methods").add(s.method() == MandateMethod.UPI ? "upi" : "enach");
        ObjectNode meta = body.putObject("subscription_meta");
        if (s.returnUrl() != null) {
            meta.put("return_url", s.returnUrl());
        }
        // Cashfree sends the payer the authorisation link itself
        meta.putArray("notification_channel").add("EMAIL").add("SMS");
        body.put("subscription_expiry_time", OffsetDateTime.of(s.expiresOn(), LocalTime.of(23, 59, 59), IST.getRules().getOffset(s.expiresOn().atStartOfDay()))
                .format(ISO));
        body.put("subscription_note", s.description());
        JsonNode r = call("POST", base(c) + "/subscriptions", headers(c, s.reference()), body);
        return new MandateSession(text(r, "cf_subscription_id"), null, text(r, "subscription_session_id"), null,
                status(text(r, "subscription_status")));
    }

    @Override
    public MandateState fetchMandate(Credentials c, MandateRef m) {
        JsonNode r = call("GET", base(c) + "/subscriptions/" + enc(m.reference()), headers(c, null), null);
        String st = text(r, "subscription_status");
        return new MandateState(status(st), text(r, "cf_subscription_id"), st);
    }

    @Override
    public void cancelMandate(Credentials c, MandateRef m) {
        ObjectNode body = obj();
        body.put("subscription_id", m.reference());
        body.put("action", "CANCEL");
        call("POST", base(c) + "/subscriptions/" + enc(m.reference()) + "/manage", headers(c, null), body);
    }

    /** Cashfree subscription states → ours. */
    static MandateStatus status(String s) {
        if (s == null) {
            return MandateStatus.PENDING;
        }
        return switch (s.toUpperCase()) {
            case "ACTIVE" -> MandateStatus.ACTIVE;
            case "ON_HOLD", "PAUSED", "CUSTOMER_PAUSED" -> MandateStatus.PAUSED;
            case "CANCELLED", "CUSTOMER_CANCELLED" -> MandateStatus.CANCELLED;
            case "COMPLETED", "EXPIRED", "LINK_EXPIRED" -> MandateStatus.EXPIRED;
            case "FAILED", "BANK_APPROVAL_FAILED" -> MandateStatus.FAILED;
            default -> MandateStatus.PENDING; // INITIALIZED, BANK_APPROVAL_PENDING
        };
    }

    // ── Debits ─────────────────────────────────────────────────────────────────────────

    @Override
    public int presentationLeadDays() {
        return 1;
    }

    @Override
    public ChargeSubmitted charge(Credentials c, Charge ch) {
        ObjectNode body = obj();
        body.put("subscription_id", ch.mandate().reference());
        body.put("payment_id", ch.reference());
        body.put("payment_amount", rupees(ch.amountPaise()));
        body.put("payment_schedule_date", OffsetDateTime.of(ch.debitOn(), LocalTime.of(10, 0), IST.getRules().getOffset(ch.debitOn().atStartOfDay()))
                .format(ISO));
        body.put("payment_remarks", ch.remarks());
        body.put("payment_type", "CHARGE");
        JsonNode r = call("POST", base(c) + "/subscriptions/pay", headers(c, ch.reference()), body);
        return new ChargeSubmitted(text(r, "cf_payment_id"), null);
    }

    // ── Links and orders ───────────────────────────────────────────────────────────────

    @Override
    public CheckoutSession createCheckout(Credentials c, CheckoutSetup s) {
        if (s.kind() == CheckoutKind.LINK) {
            ObjectNode body = obj();
            body.put("link_id", s.reference());
            body.put("link_amount", rupees(s.amountPaise()));
            body.put("link_currency", "INR");
            body.put("link_purpose", s.purpose());
            body.set("customer_details", customer(s.payer()));
            body.put("link_partial_payments", s.allowPartial());
            if (s.allowPartial()) {
                body.put("link_minimum_partial_amount", rupees(s.minPartialPaise()));
            }
            if (s.expiresAt() != null) {
                body.put("link_expiry_time", s.expiresAt().atZone(IST).toOffsetDateTime().format(ISO));
            }
            ObjectNode notify = body.putObject("link_notify");
            notify.put("send_sms", false);
            notify.put("send_email", false);
            body.put("link_auto_reminders", false);
            if (s.returnUrl() != null) {
                body.putObject("link_meta").put("return_url", s.returnUrl());
            }
            body.putObject("link_notes").put("vidya_ref", s.reference());
            JsonNode r = call("POST", base(c) + "/links", headers(c, s.reference()), body);
            return new CheckoutSession(s.reference(), text(r, "link_url"), text(r, "cf_link_id"));
        }
        ObjectNode body = obj();
        body.put("order_id", s.reference());
        body.put("order_amount", rupees(s.amountPaise()));
        body.put("order_currency", "INR");
        ObjectNode cust = customer(s.payer());
        cust.put("customer_id", "payer_" + s.payer().phone().replaceAll("[^0-9A-Za-z]", ""));
        body.set("customer_details", cust);
        if (s.returnUrl() != null) {
            body.putObject("order_meta").put("return_url", s.returnUrl());
        }
        if (s.expiresAt() != null) {
            body.put("order_expiry_time", s.expiresAt().atZone(IST).toOffsetDateTime().format(ISO));
        }
        body.put("order_note", s.purpose());
        body.putObject("order_tags").put("vidya_ref", s.reference());
        JsonNode r = call("POST", base(c) + "/orders", headers(c, s.reference()), body);
        return new CheckoutSession(s.reference(), null, text(r, "payment_session_id"));
    }

    @Override
    public void cancelCheckout(Credentials c, CheckoutKind kind, String reference, String gatewayId) {
        if (kind == CheckoutKind.LINK) {
            call("POST", base(c) + "/links/" + enc(reference) + "/cancel", headers(c, null), obj());
        } else {
            ObjectNode body = obj();
            body.put("order_status", "TERMINATED");
            call("PATCH", base(c) + "/orders/" + enc(reference), headers(c, null), body);
        }
    }

    private ObjectNode customer(Payer p) {
        ObjectNode n = obj();
        n.put("customer_name", p.name());
        if (p.email() != null) {
            n.put("customer_email", p.email());
        }
        n.put("customer_phone", p.phone());
        return n;
    }

    // ── Webhooks ───────────────────────────────────────────────────────────────────────

    @Override
    public Webhook parseWebhook(Credentials c, Map<String, String> headers, String body) {
        String ts = headers.get("x-webhook-timestamp");
        String sig = headers.get("x-webhook-signature");
        String expected = Base64.getEncoder().encodeToString(hmacSha256(c.secret(), (ts == null ? "" : ts) + body));
        if (ts == null || !sameSignature(expected, sig)) {
            throw GatewayException.badSignature(provider());
        }
        JsonNode root = parse(body);
        String type = text(root, "type");
        JsonNode data = root.path("data");
        List<GatewayEvent> events = new ArrayList<>();
        switch (type == null ? "" : type.toUpperCase()) {
            case "SUBSCRIPTION_STATUS_CHANGED" -> {
                JsonNode d = data.path("subscription_details");
                String st = text(d, "subscription_status");
                events.add(new MandateUpdate(text(d, "subscription_id"), text(d, "cf_subscription_id"), null, null, status(st), st));
            }
            case "SUBSCRIPTION_AUTH_STATUS" -> {
                String auth = text(data, "authorization_details", "authorization_status");
                String pay = text(data, "payment_status");
                MandateStatus st = "ACTIVE".equalsIgnoreCase(auth) || "SUCCESS".equalsIgnoreCase(pay) ? MandateStatus.ACTIVE
                        : "FAILED".equalsIgnoreCase(auth) || "FAILED".equalsIgnoreCase(pay) ? MandateStatus.FAILED : MandateStatus.PENDING;
                String reason = text(data, "failure_details", "failure_reason");
                events.add(new MandateUpdate(text(data, "subscription_id"), text(data, "cf_subscription_id"), null, null, st,
                        reason != null ? reason : auth));
            }
            case "SUBSCRIPTION_PAYMENT_SUCCESS", "SUBSCRIPTION_PAYMENT_FAILED", "SUBSCRIPTION_PAYMENT_CANCELLED" -> {
                // The authorisation payment (payment_type AUTH) is reported by SUBSCRIPTION_AUTH_STATUS
                if (!"AUTH".equalsIgnoreCase(text(data, "payment_type"))) {
                    boolean ok = type.equalsIgnoreCase("SUBSCRIPTION_PAYMENT_SUCCESS");
                    String reason = text(data, "failure_details", "failure_reason");
                    events.add(new PaymentReport(text(data, "payment_id"), null, text(data, "cf_payment_id"), paise(data.get("payment_amount")),
                            "NACH", ok, ok ? null : reason != null ? reason : text(data, "payment_status")));
                }
            }
            case "PAYMENT_LINK_EVENT" -> {
                String linkId = text(data, "link_id");
                String st = text(data, "link_status");
                CheckoutStatus cs = switch (st == null ? "" : st.toUpperCase()) {
                    case "PAID" -> CheckoutStatus.PAID;
                    case "PARTIALLY_PAID" -> CheckoutStatus.PARTIALLY_PAID;
                    case "EXPIRED" -> CheckoutStatus.EXPIRED;
                    case "CANCELLED" -> CheckoutStatus.CANCELLED;
                    default -> null;
                };
                if (cs != null) {
                    long paid = paise(data.get("link_amount_paid"));
                    events.add(new LinkReport(linkId, linkId, paid > 0 ? linkId + ":" + paid : null, paid, true, cs, "LINK"));
                }
            }
            case "PAYMENT_SUCCESS_WEBHOOK" -> {
                String orderId = text(data, "order", "order_id");
                events.add(new PaymentReport(orderId, orderId, text(data, "payment", "cf_payment_id"), paise(data.path("payment").get("payment_amount")),
                        text(data, "payment", "payment_group"), true, null));
            }
            default -> {
                // PAYMENT_FAILED_WEBHOOK, PAYMENT_USER_DROPPED_WEBHOOK, refunds ...: the order stays payable, nothing to apply
            }
        }
        return new Webhook(blank(headers.get("x-idempotency-key")), type, events);
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
