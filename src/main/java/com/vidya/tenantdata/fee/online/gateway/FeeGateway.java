package com.vidya.tenantdata.fee.online.gateway;

import com.vidya.tenantdata.fee.online.enums.CheckoutKind;
import com.vidya.tenantdata.fee.online.enums.GatewayProvider;
import com.vidya.tenantdata.fee.online.gateway.GatewayModels.Charge;
import com.vidya.tenantdata.fee.online.gateway.GatewayModels.ChargeSubmitted;
import com.vidya.tenantdata.fee.online.gateway.GatewayModels.CheckoutSession;
import com.vidya.tenantdata.fee.online.gateway.GatewayModels.CheckoutSetup;
import com.vidya.tenantdata.fee.online.gateway.GatewayModels.Credentials;
import com.vidya.tenantdata.fee.online.gateway.GatewayModels.MandateRef;
import com.vidya.tenantdata.fee.online.gateway.GatewayModels.MandateSession;
import com.vidya.tenantdata.fee.online.gateway.GatewayModels.MandateSetup;
import com.vidya.tenantdata.fee.online.gateway.GatewayModels.MandateState;
import com.vidya.tenantdata.fee.online.gateway.GatewayModels.Webhook;

import java.util.Map;

/**
 * One payment gateway, called with the school's own keys (same shape as infi_payment's PaymentGatewayProcessor: mandate, recurring
 * charge, link, order, webhook). Calls throw {@link GatewayException} when the gateway refuses or cannot be reached.
 */
public interface FeeGateway {

    GatewayProvider provider();

    /** Registers an on-demand mandate (eNACH / UPI Autopay) up to the maximum amount; the payer then authorises it. */
    MandateSession createMandate(Credentials c, MandateSetup setup);

    MandateState fetchMandate(Credentials c, MandateRef mandate);

    void cancelMandate(Credentials c, MandateRef mandate);

    /** Presents a debit on an active mandate; the result comes later by webhook. */
    ChargeSubmitted charge(Credentials c, Charge charge);

    /** Days between sending a debit and the day the bank takes it (Cashfree schedules eNACH for the next day at the earliest). */
    int presentationLeadDays();

    CheckoutSession createCheckout(Credentials c, CheckoutSetup setup);

    /** Stops a link / order from taking more money (best effort: an order that cannot be cancelled simply expires). */
    void cancelCheckout(Credentials c, CheckoutKind kind, String reference, String gatewayId);

    /**
     * Verifies the signature and reads the events. Headers have lower-case names.
     *
     * @throws GatewayException with {@code signatureInvalid()} when the signature does not match
     */
    Webhook parseWebhook(Credentials c, Map<String, String> headers, String body);
}
