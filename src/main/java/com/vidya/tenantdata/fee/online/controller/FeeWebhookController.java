package com.vidya.tenantdata.fee.online.controller;

import com.vidya.tenant.context.TenantContext;
import com.vidya.tenant.registry.TenantRegistration;
import com.vidya.tenant.registry.TenantRegistryService;
import com.vidya.tenantdata.fee.online.enums.GatewayProvider;
import com.vidya.tenantdata.fee.online.service.FeeWebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Webhooks of the schools' own gateway accounts: {@code /api/v1/webhooks/fees/{tenantCode}/{cashfree|razorpay}}. No login (the path is
 * open, like the platform payment webhooks): the school comes from the URL and the request is trusted only when its signature
 * matches that school's keys. Non-2xx answers make the gateway retry.
 */
@RestController
@RequestMapping("/api/v1/webhooks/fees")
@RequiredArgsConstructor
@Tag(name = "Fees – gateway webhooks", description = "Cashfree / Razorpay callbacks for school fee mandates, debits, links and orders")
public class FeeWebhookController {

    private final TenantRegistryService registry;
    private final FeeWebhookService webhooks;

    @PostMapping("/{tenantCode}/{provider}")
    @Operation(summary = "Gateway webhook for school fees")
    public ResponseEntity<String> receive(@PathVariable String tenantCode, @PathVariable String provider, @RequestBody byte[] body,
                                          @RequestHeader Map<String, String> headers) {
        GatewayProvider p;
        try {
            p = GatewayProvider.valueOf(provider.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("unknown gateway");
        }
        Optional<TenantRegistration> school = registry.findByTenantCode(tenantCode).filter(TenantRegistration::isAccessible);
        if (school.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("unknown school");
        }
        Map<String, String> lower = new HashMap<>();
        headers.forEach((k, v) -> lower.put(k.toLowerCase(Locale.ROOT), v));
        try {
            TenantContext.setTenantCode(school.get().tenantCode());
            return switch (webhooks.handle(p, lower, new String(body, StandardCharsets.UTF_8))) {
                case OK -> ResponseEntity.ok("OK");
                case REJECTED -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("bad signature");
                case UNKNOWN_ACCOUNT -> ResponseEntity.status(HttpStatus.NOT_FOUND).body("gateway not connected");
                case RETRY -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("retry");
            };
        } finally {
            TenantContext.clear();
        }
    }
}
