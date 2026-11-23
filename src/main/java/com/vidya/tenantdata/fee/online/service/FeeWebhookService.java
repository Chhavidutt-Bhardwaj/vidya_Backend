package com.vidya.tenantdata.fee.online.service;

import com.vidya.tenantdata.fee.online.enums.GatewayProvider;
import com.vidya.tenantdata.fee.online.gateway.FeeGatewayRegistry;
import com.vidya.tenantdata.fee.online.gateway.GatewayException;
import com.vidya.tenantdata.fee.online.gateway.GatewayModels.Credentials;
import com.vidya.tenantdata.fee.online.gateway.GatewayModels.GatewayEvent;
import com.vidya.tenantdata.fee.online.gateway.GatewayModels.LinkReport;
import com.vidya.tenantdata.fee.online.gateway.GatewayModels.MandateUpdate;
import com.vidya.tenantdata.fee.online.gateway.GatewayModels.PaymentReport;
import com.vidya.tenantdata.fee.online.gateway.GatewayModels.Webhook;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Gateway webhooks of one school (tenant already set from the URL): verify the signature with the school's keys, then route each
 * event — mandate state, auto-debit result (else checkout order payment), payment link payment. Each event is applied in its own
 * transaction; every webhook is logged in fee_gateway_event.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeeWebhookService {

    public enum Result {
        /** Verified and handled (or nothing to do). */
        OK,
        /** Signature did not match. */
        REJECTED,
        /** The school has not connected this gateway. */
        UNKNOWN_ACCOUNT,
        /** A handler failed: answer non-2xx so the gateway sends it again. */
        RETRY
    }

    private final OnlineFeeConfigService config;
    private final FeeGatewayRegistry gateways;
    private final MandateService mandates;
    private final AutoDebitSteps debits;
    private final CheckoutService checkouts;
    private final WebhookLog webhookLog;

    public Result handle(GatewayProvider provider, Map<String, String> headers, String body) {
        Credentials c = config.webhookCredentials(provider);
        if (c == null) {
            log.warn("{} webhook for a school without a {} account", provider, provider);
            return Result.UNKNOWN_ACCOUNT;
        }
        Webhook w;
        try {
            w = gateways.get(provider).parseWebhook(c, headers, body);
        } catch (GatewayException e) {
            webhookLog.record(provider, null, null, body, "REJECTED", e.getMessage());
            return e.isSignatureInvalid() ? Result.REJECTED : Result.RETRY;
        }
        if (webhookLog.applied(provider, w.eventId())) {
            webhookLog.record(provider, w.eventId(), w.type(), body, "DUPLICATE", null);
            return Result.OK;
        }
        List<String> notes = new ArrayList<>();
        boolean failed = false;
        for (GatewayEvent e : w.events()) {
            try {
                notes.add(apply(provider, e));
            } catch (RuntimeException ex) {
                failed = true;
                log.error("{} webhook {} event {} failed: {}", provider, w.type(), e, ex.getMessage(), ex);
                notes.add("ERROR: " + ex.getMessage());
            }
        }
        String outcome = failed ? "ERROR" : notes.stream().anyMatch(n -> n.startsWith("APPLIED")) ? "APPLIED" : "IGNORED";
        webhookLog.record(provider, w.eventId(), w.type(), body, outcome, notes.isEmpty() ? "no events" : String.join("; ", notes));
        return failed ? Result.RETRY : Result.OK;
    }

    private String apply(GatewayProvider provider, GatewayEvent e) {
        return switch (e) {
            case MandateUpdate u -> mandates.apply(provider, u);
            case PaymentReport r -> {
                String debit = debits.outcome(provider, r);
                yield debit != null ? debit : checkouts.applyOrder(provider, r);
            }
            case LinkReport l -> checkouts.applyLink(provider, l);
        };
    }
}
