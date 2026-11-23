package com.vidya.tenantdata.fee.online;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidya.common.exception.BusinessException;
import com.vidya.tenantdata.fee.online.entity.FeeAutoDebitSettings;
import com.vidya.tenantdata.fee.online.enums.CheckoutKind;
import com.vidya.tenantdata.fee.online.enums.CheckoutStatus;
import com.vidya.tenantdata.fee.online.enums.GatewayEnvironment;
import com.vidya.tenantdata.fee.online.enums.GatewayProvider;
import com.vidya.tenantdata.fee.online.enums.MandateMethod;
import com.vidya.tenantdata.fee.online.enums.MandateStatus;
import com.vidya.tenantdata.fee.online.gateway.CashfreeFeeGateway;
import com.vidya.tenantdata.fee.online.gateway.GatewayException;
import com.vidya.tenantdata.fee.online.gateway.GatewayHttp;
import com.vidya.tenantdata.fee.online.gateway.GatewayModels.Charge;
import com.vidya.tenantdata.fee.online.gateway.GatewayModels.ChargeSubmitted;
import com.vidya.tenantdata.fee.online.gateway.GatewayModels.CheckoutSetup;
import com.vidya.tenantdata.fee.online.gateway.GatewayModels.Credentials;
import com.vidya.tenantdata.fee.online.gateway.GatewayModels.LinkReport;
import com.vidya.tenantdata.fee.online.gateway.GatewayModels.MandateRef;
import com.vidya.tenantdata.fee.online.gateway.GatewayModels.MandateSession;
import com.vidya.tenantdata.fee.online.gateway.GatewayModels.MandateSetup;
import com.vidya.tenantdata.fee.online.gateway.GatewayModels.MandateUpdate;
import com.vidya.tenantdata.fee.online.gateway.GatewayModels.Payer;
import com.vidya.tenantdata.fee.online.gateway.GatewayModels.PaymentReport;
import com.vidya.tenantdata.fee.online.gateway.GatewayModels.Webhook;
import com.vidya.tenantdata.fee.online.gateway.RazorpayFeeGateway;
import com.vidya.tenantdata.fee.online.gateway.SecretCipher;
import com.vidya.tenantdata.fee.online.gateway.FeeGateway;
import com.vidya.tenantdata.fee.online.gateway.FeeGatewayRegistry;
import com.vidya.tenantdata.fee.online.service.AutoDebitSteps;
import com.vidya.tenantdata.fee.online.service.CheckoutService;
import com.vidya.tenantdata.fee.online.service.FeeWebhookService;
import com.vidya.tenantdata.fee.online.service.MandateService;
import com.vidya.tenantdata.fee.online.service.WebhookLog;
import com.vidya.tenantdata.fee.online.service.CheckoutService.Share;
import com.vidya.tenantdata.fee.online.service.OnlineFeeConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Online fees: gateways, webhooks, sibling split, secrets")
class OnlineFeeUnitTest {

    /** Records requests and answers from a queue. */
    static class FakeHttp extends GatewayHttp {
        record Call(String method, String url, Map<String, String> headers, String body) {
        }

        final List<Call> calls = new ArrayList<>();
        final List<Response> answers = new ArrayList<>();

        FakeHttp answer(int status, String body) {
            answers.add(new Response(status, body));
            return this;
        }

        @Override
        public Response send(String method, String url, Map<String, String> headers, String body) {
            calls.add(new Call(method, url, headers, body));
            return answers.isEmpty() ? new Response(200, "{}") : answers.removeFirst();
        }
    }

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Payer PAYER = new Payer("Asha Rao", "asha@x.test", "9800000001");

    private static JsonNode body(FakeHttp.Call c) throws Exception {
        return JSON.readTree(c.body());
    }

    private static String hmacB64(String secret, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }

    private static String hmacHex(String secret, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }

    // ── Cashfree ───────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Cashfree")
    class Cashfree {

        final Credentials cred = new Credentials(GatewayProvider.CASHFREE, GatewayEnvironment.SANDBOX, "cf_app", "cf_secret", null);
        FakeHttp http;
        CashfreeFeeGateway gw;

        @BeforeEach
        void setUp() {
            http = new FakeHttp();
            gw = new CashfreeFeeGateway(http);
        }

        @Test
        @DisplayName("a mandate is an ON_DEMAND subscription up to the maximum, authorised by eNACH")
        void mandate() throws Exception {
            http.answer(200, "{\"cf_subscription_id\":\"777\",\"subscription_session_id\":\"sess_1\",\"subscription_status\":\"INITIALIZED\"}");
            MandateSession s = gw.createMandate(cred, new MandateSetup("MD1", MandateMethod.ENACH, PAYER, 10_000_000, LocalDate.of(2036, 11, 22),
                    "https://school.test/back", "School fees: Kid A"));

            FakeHttp.Call c = http.calls.getFirst();
            assertThat(c.method()).isEqualTo("POST");
            assertThat(c.url()).isEqualTo("https://sandbox.cashfree.com/pg/subscriptions");
            assertThat(c.headers()).containsEntry("x-client-id", "cf_app").containsEntry("x-client-secret", "cf_secret").containsEntry("x-api-version", "2025-01-01");
            JsonNode b = body(c);
            assertThat(b.path("subscription_id").asText()).isEqualTo("MD1");
            assertThat(b.path("plan_details").path("plan_type").asText()).isEqualTo("ON_DEMAND");
            assertThat(b.path("plan_details").path("plan_max_amount").decimalValue()).isEqualByComparingTo("100000.00");
            assertThat(b.path("authorization_details").path("payment_methods").get(0).asText()).isEqualTo("enach");
            assertThat(b.path("customer_details").path("customer_phone").asText()).isEqualTo("9800000001");
            assertThat(b.path("subscription_expiry_time").asText()).startsWith("2036-11-22T23:59:59+05:30");
            assertThat(s.gatewayMandateId()).isEqualTo("777");
            assertThat(s.gatewayAuthId()).isEqualTo("sess_1");
            assertThat(s.status()).isEqualTo(MandateStatus.PENDING);
        }

        @Test
        @DisplayName("a debit is a CHARGE on the subscription for the debit day, keyed by our reference")
        void charge() throws Exception {
            http.answer(200, "{\"cf_payment_id\":\"cfp_9\",\"payment_status\":\"PENDING\"}");
            ChargeSubmitted r = gw.charge(cred, new Charge("DB1", new MandateRef("MD1", "777", null, null), PAYER, 359_000, LocalDate.of(2097, 6, 10),
                    "School fees"));
            FakeHttp.Call c = http.calls.getFirst();
            assertThat(c.url()).endsWith("/subscriptions/pay");
            assertThat(c.headers()).containsEntry("x-idempotency-key", "DB1");
            JsonNode b = body(c);
            assertThat(b.path("subscription_id").asText()).isEqualTo("MD1");
            assertThat(b.path("payment_id").asText()).isEqualTo("DB1");
            assertThat(b.path("payment_type").asText()).isEqualTo("CHARGE");
            assertThat(b.path("payment_amount").decimalValue()).isEqualByComparingTo("3590.00");
            assertThat(b.path("payment_schedule_date").asText()).startsWith("2097-06-10");
            assertThat(r.gatewayPaymentId()).isEqualTo("cfp_9");
            assertThat(gw.presentationLeadDays()).isEqualTo(1);
        }

        @Test
        @DisplayName("a link may take part payments; an error body becomes the gateway's message")
        void linkAndError() throws Exception {
            http.answer(200, "{\"link_url\":\"https://payments.cashfree.com/links/x\",\"cf_link_id\":\"55\"}");
            var s = gw.createCheckout(cred, new CheckoutSetup("PL1", CheckoutKind.LINK, PAYER, 500_000, true, 10_000, Instant.parse("2097-06-20T00:00:00Z"),
                    "School fees", null));
            JsonNode b = body(http.calls.getFirst());
            assertThat(http.calls.getFirst().url()).endsWith("/links");
            assertThat(b.path("link_partial_payments").asBoolean()).isTrue();
            assertThat(b.path("link_minimum_partial_amount").decimalValue()).isEqualByComparingTo("100.00");
            assertThat(b.path("link_notify").path("send_sms").asBoolean()).isFalse();
            assertThat(s.url()).isEqualTo("https://payments.cashfree.com/links/x");

            http.answer(400, "{\"message\":\"subscription not active\",\"code\":\"x\"}");
            assertThatThrownBy(() -> gw.charge(cred, new Charge("DB2", new MandateRef("MD1", null, null, null), PAYER, 100, LocalDate.of(2097, 6, 10), "x")))
                    .isInstanceOf(GatewayException.class).hasMessageContaining("subscription not active");
        }

        @Test
        @DisplayName("webhooks: signature over timestamp + body with the client secret; mandate, debit and link events")
        void webhooks() throws Exception {
            String status = "{\"type\":\"SUBSCRIPTION_STATUS_CHANGED\",\"data\":{\"subscription_details\":{\"subscription_id\":\"MD1\","
                    + "\"cf_subscription_id\":\"777\",\"subscription_status\":\"ACTIVE\"}}}";
            Webhook w = gw.parseWebhook(cred, signed(status, "1700000000"), status);
            assertThat(w.events()).containsExactly(new MandateUpdate("MD1", "777", null, null, MandateStatus.ACTIVE, "ACTIVE"));

            String failed = "{\"type\":\"SUBSCRIPTION_PAYMENT_FAILED\",\"data\":{\"payment_id\":\"DB1\",\"cf_payment_id\":\"cfp_9\","
                    + "\"payment_amount\":3590.0,\"payment_type\":\"CHARGE\",\"failure_details\":{\"failure_reason\":\"Insufficient funds\"}}}";
            assertThat(gw.parseWebhook(cred, signed(failed, "1"), failed).events())
                    .containsExactly(new PaymentReport("DB1", null, "cfp_9", 359_000, "NACH", false, "Insufficient funds"));

            String auth = "{\"type\":\"SUBSCRIPTION_PAYMENT_SUCCESS\",\"data\":{\"payment_id\":\"a\",\"payment_type\":\"AUTH\",\"payment_amount\":1}}";
            assertThat(gw.parseWebhook(cred, signed(auth, "1"), auth).events()).isEmpty();

            String link = "{\"type\":\"PAYMENT_LINK_EVENT\",\"data\":{\"link_id\":\"PL1\",\"link_status\":\"PARTIALLY_PAID\",\"link_amount_paid\":3500}}";
            assertThat(gw.parseWebhook(cred, signed(link, "1"), link).events())
                    .containsExactly(new LinkReport("PL1", "PL1", "PL1:350000", 350_000, true, CheckoutStatus.PARTIALLY_PAID, "LINK"));

            assertThatThrownBy(() -> gw.parseWebhook(cred, Map.of("x-webhook-timestamp", "1", "x-webhook-signature", "forged"), status))
                    .isInstanceOf(GatewayException.class).matches(e -> ((GatewayException) e).isSignatureInvalid());
        }

        private Map<String, String> signed(String body, String ts) throws Exception {
            return Map.of("x-webhook-timestamp", ts, "x-webhook-signature", hmacB64("cf_secret", ts + body));
        }
    }

    // ── Razorpay ───────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Razorpay")
    class Razorpay {

        final Credentials cred = new Credentials(GatewayProvider.RAZORPAY, GatewayEnvironment.PRODUCTION, "rzp_live_k", "rzp_s", "wh_s");
        FakeHttp http;
        RazorpayFeeGateway gw;

        @BeforeEach
        void setUp() {
            http = new FakeHttp();
            gw = new RazorpayFeeGateway(http, Clock.fixed(Instant.parse("2097-06-07T05:00:00Z"), ZoneId.of("Asia/Kolkata")));
        }

        @Test
        @DisplayName("a mandate is a registration link (emandate, max amount in paise) the payer opens")
        void mandate() throws Exception {
            http.answer(200, "{\"id\":\"inv_1\",\"customer_id\":\"cust_1\",\"short_url\":\"https://rzp.io/i/abc\"}");
            MandateSession s = gw.createMandate(cred, new MandateSetup("MD2", MandateMethod.ENACH, PAYER, 10_000_000, LocalDate.of(2036, 11, 22), null, "x"));
            FakeHttp.Call c = http.calls.getFirst();
            assertThat(c.url()).isEqualTo("https://api.razorpay.com/v1/subscription_registration/auth_links");
            assertThat(c.headers().get("Authorization")).isEqualTo("Basic " + Base64.getEncoder().encodeToString("rzp_live_k:rzp_s".getBytes()));
            JsonNode b = body(c);
            assertThat(b.path("amount").asLong()).isZero();
            assertThat(b.path("receipt").asText()).isEqualTo("MD2");
            assertThat(b.path("subscription_registration").path("method").asText()).isEqualTo("emandate");
            assertThat(b.path("subscription_registration").path("max_amount").asLong()).isEqualTo(10_000_000);
            assertThat(s.authUrl()).isEqualTo("https://rzp.io/i/abc");
            assertThat(s.gatewayCustomerId()).isEqualTo("cust_1");
            assertThat(s.gatewayAuthId()).isEqualTo("inv_1");
        }

        @Test
        @DisplayName("a debit is an order then a recurring payment on the token")
        void charge() throws Exception {
            http.answer(200, "{\"id\":\"order_1\"}").answer(200, "{\"razorpay_payment_id\":\"pay_1\"}");
            ChargeSubmitted r = gw.charge(cred, new Charge("DB3", new MandateRef("MD2", "token_1", "cust_1", "inv_1"), PAYER, 300_000,
                    LocalDate.of(2097, 6, 10), "School fees"));
            assertThat(http.calls).extracting(FakeHttp.Call::url).containsExactly("https://api.razorpay.com/v1/orders",
                    "https://api.razorpay.com/v1/payments/create/recurring");
            assertThat(body(http.calls.get(0)).path("amount").asLong()).isEqualTo(300_000);
            assertThat(body(http.calls.get(0)).path("receipt").asText()).isEqualTo("DB3");
            JsonNode pay = body(http.calls.get(1));
            assertThat(pay.path("order_id").asText()).isEqualTo("order_1");
            assertThat(pay.path("token").asText()).isEqualTo("token_1");
            assertThat(pay.path("customer_id").asText()).isEqualTo("cust_1");
            assertThat(pay.path("recurring").asText()).isEqualTo("1");
            assertThat(r).isEqualTo(new ChargeSubmitted("pay_1", "order_1"));
        }

        @Test
        @DisplayName("webhooks: hex HMAC with the webhook secret; token, payment and payment-link events")
        void webhooks() throws Exception {
            String token = "{\"event\":\"token.confirmed\",\"payload\":{\"token\":{\"entity\":{\"id\":\"token_1\",\"customer_id\":\"cust_1\"}}}}";
            Webhook w = gw.parseWebhook(cred, signed(token, "evt_1"), token);
            assertThat(w.eventId()).isEqualTo("evt_1");
            assertThat(w.events()).containsExactly(new MandateUpdate(null, "token_1", "cust_1", null, MandateStatus.ACTIVE, "token.confirmed"));

            String paid = "{\"event\":\"invoice.paid\",\"payload\":{\"invoice\":{\"entity\":{\"id\":\"inv_1\",\"receipt\":\"MD2\",\"customer_id\":\"cust_1\"}},"
                    + "\"payment\":{\"entity\":{\"id\":\"pay_0\",\"token_id\":\"token_1\"}}}}";
            assertThat(gw.parseWebhook(cred, signed(paid, null), paid).events())
                    .containsExactly(new MandateUpdate("MD2", "token_1", "cust_1", "inv_1", MandateStatus.PENDING, "authorised by the payer"));

            String failed = "{\"event\":\"payment.failed\",\"payload\":{\"payment\":{\"entity\":{\"id\":\"pay_1\",\"order_id\":\"order_1\",\"amount\":300000,"
                    + "\"method\":\"emandate\",\"error_description\":\"Insufficient balance\",\"notes\":{\"vidya_ref\":\"DB3\"}}}}}";
            assertThat(gw.parseWebhook(cred, signed(failed, null), failed).events())
                    .containsExactly(new PaymentReport("DB3", "order_1", "pay_1", 300_000, "emandate", false, "Insufficient balance"));

            String part = "{\"event\":\"payment_link.partially_paid\",\"payload\":{\"payment_link\":{\"entity\":{\"id\":\"plink_1\",\"reference_id\":\"PL9\"}},"
                    + "\"payment\":{\"entity\":{\"id\":\"pay_7\",\"amount\":150000,\"method\":\"upi\"}}}}";
            assertThat(gw.parseWebhook(cred, signed(part, null), part).events())
                    .containsExactly(new LinkReport("PL9", "plink_1", "pay_7", 150_000, false, CheckoutStatus.PARTIALLY_PAID, "upi"));

            assertThatThrownBy(() -> gw.parseWebhook(cred, Map.of("x-razorpay-signature", hmacHex("rzp_s", token)), token))
                    .isInstanceOf(GatewayException.class);
        }

        private Map<String, String> signed(String body, String eventId) throws Exception {
            return eventId == null ? Map.of("x-razorpay-signature", hmacHex("wh_s", body))
                    : Map.of("x-razorpay-signature", hmacHex("wh_s", body), "x-razorpay-event-id", eventId);
        }
    }

    // ── Webhook routing ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("webhook routing")
    class Routing {

        final Credentials cred = new Credentials(GatewayProvider.RAZORPAY, GatewayEnvironment.SANDBOX, "k", "s", "w");
        final OnlineFeeConfigService config = mock(OnlineFeeConfigService.class);
        final FeeGateway gw = mock(FeeGateway.class);
        final MandateService mandates = mock(MandateService.class);
        final AutoDebitSteps debits = mock(AutoDebitSteps.class);
        final CheckoutService checkouts = mock(CheckoutService.class);
        final WebhookLog webhookLog = mock(WebhookLog.class);
        FeeWebhookService service;

        @BeforeEach
        void setUp() {
            when(gw.provider()).thenReturn(GatewayProvider.RAZORPAY);
            when(config.webhookCredentials(GatewayProvider.RAZORPAY)).thenReturn(cred);
            service = new FeeWebhookService(config, new FeeGatewayRegistry(List.of(gw)), mandates, debits, checkouts, webhookLog);
        }

        @Test
        @DisplayName("a payment that is no auto-debit is tried as a checkout order; the webhook is logged as applied")
        void paymentFallsBackToOrder() {
            PaymentReport r = new PaymentReport("OR1", "order_1", "pay_1", 1000, "upi", true, null);
            when(gw.parseWebhook(any(), any(), any())).thenReturn(new Webhook("evt_1", "payment.captured", List.of(r)));
            when(debits.outcome(GatewayProvider.RAZORPAY, r)).thenReturn(null);
            when(checkouts.applyOrder(GatewayProvider.RAZORPAY, r)).thenReturn("APPLIED: OR1 +1000");
            assertThat(service.handle(GatewayProvider.RAZORPAY, Map.of(), "{}")).isEqualTo(FeeWebhookService.Result.OK);
            verify(webhookLog).record(GatewayProvider.RAZORPAY, "evt_1", "payment.captured", "{}", "APPLIED", "APPLIED: OR1 +1000");
        }

        @Test
        @DisplayName("bad signature → 401; already applied event → nothing done; handler error → retry; unknown account")
        void outcomes() {
            when(gw.parseWebhook(any(), any(), any())).thenThrow(GatewayException.badSignature(GatewayProvider.RAZORPAY));
            assertThat(service.handle(GatewayProvider.RAZORPAY, Map.of(), "{}")).isEqualTo(FeeWebhookService.Result.REJECTED);

            MandateUpdate u = new MandateUpdate(null, "token_1", "cust_1", null, MandateStatus.ACTIVE, "x");
            org.mockito.Mockito.reset(gw);
            when(gw.provider()).thenReturn(GatewayProvider.RAZORPAY);
            when(gw.parseWebhook(any(), any(), any())).thenReturn(new Webhook("evt_2", "token.confirmed", List.of(u)));
            when(webhookLog.applied(GatewayProvider.RAZORPAY, "evt_2")).thenReturn(true);
            assertThat(service.handle(GatewayProvider.RAZORPAY, Map.of(), "{}")).isEqualTo(FeeWebhookService.Result.OK);
            verify(mandates, never()).apply(any(), any());

            when(webhookLog.applied(GatewayProvider.RAZORPAY, "evt_2")).thenReturn(false);
            when(mandates.apply(GatewayProvider.RAZORPAY, u)).thenThrow(new IllegalStateException("db down"));
            assertThat(service.handle(GatewayProvider.RAZORPAY, Map.of(), "{}")).isEqualTo(FeeWebhookService.Result.RETRY);

            when(config.webhookCredentials(GatewayProvider.RAZORPAY)).thenReturn(null);
            assertThat(service.handle(GatewayProvider.RAZORPAY, Map.of(), "{}")).isEqualTo(FeeWebhookService.Result.UNKNOWN_ACCOUNT);
        }
    }

    // ── Rules ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("rules")
    class Rules {

        final UUID senior = UUID.randomUUID();
        final UUID junior = UUID.randomUUID();
        final List<Share> shares = List.of(new Share(senior, UUID.randomUUID(), 300_000), new Share(junior, UUID.randomUUID(), 200_000));

        @Test
        @DisplayName("a part payment for siblings goes to the higher class first, the rest to the next")
        void siblingSplit() {
            assertThat(CheckoutService.split(350_000, shares)).containsExactly(Map.entry(senior, 300_000L), Map.entry(junior, 50_000L));
            assertThat(CheckoutService.split(250_000, shares)).containsExactly(Map.entry(senior, 250_000L), Map.entry(junior, 0L));
            Map<UUID, Long> over = CheckoutService.split(600_000, shares);
            assertThat(over.values().stream().mapToLong(Long::longValue).sum()).isEqualTo(500_000);
        }

        @Test
        @DisplayName("bounce GST is rounded to the paisa; the next debit day rolls into next month once passed")
        void money() {
            assertThat(OnlineFeeConfigService.gstOn(50_000, new BigDecimal("18.00"))).isEqualTo(9_000);
            assertThat(OnlineFeeConfigService.gstOn(33_333, new BigDecimal("18"))).isEqualTo(6_000);
            assertThat(OnlineFeeConfigService.nextDebitDate(10, LocalDate.of(2097, 6, 10))).isEqualTo(LocalDate.of(2097, 6, 10));
            assertThat(OnlineFeeConfigService.nextDebitDate(10, LocalDate.of(2097, 6, 11))).isEqualTo(LocalDate.of(2097, 7, 10));
            assertThat(FeeAutoDebitSettings.parseDays("3, 1,3,0, 7")).containsExactly(7, 3, 1);
        }

        @Test
        @DisplayName("gateway secrets are encrypted with a fresh IV and need the server key")
        void secrets() {
            SecretCipher cipher = new SecretCipher(Base64.getEncoder().encodeToString(new byte[32]));
            String a = cipher.encrypt("rzp_secret");
            assertThat(a).startsWith("v1:").doesNotContain("rzp_secret").isNotEqualTo(cipher.encrypt("rzp_secret"));
            assertThat(cipher.decrypt(a)).isEqualTo("rzp_secret");
            byte[] other = new byte[32];
            other[0] = 1;
            assertThatThrownBy(() -> new SecretCipher(Base64.getEncoder().encodeToString(other)).decrypt(a)).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> new SecretCipher("").encrypt("x")).isInstanceOf(BusinessException.class);
        }
    }
}
