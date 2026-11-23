package com.vidya.tenantdata.fee.online.service;

import com.vidya.tenantdata.fee.online.enums.GatewayProvider;
import com.vidya.tenantdata.fee.online.repository.OnlineFeeQueries;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** The raw webhooks and what was done with them (own transaction, so the log survives a failed handler). */
@Component
@RequiredArgsConstructor
@Transactional(transactionManager = "tenantTransactionManager")
public class WebhookLog {

    private final OnlineFeeQueries queries;

    public void record(GatewayProvider provider, String eventId, String type, String payload, String outcome, String detail) {
        queries.recordEvent(provider, eventId, type, payload, outcome, detail);
    }

    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public boolean applied(GatewayProvider provider, String eventId) {
        return queries.eventApplied(provider, eventId);
    }
}
