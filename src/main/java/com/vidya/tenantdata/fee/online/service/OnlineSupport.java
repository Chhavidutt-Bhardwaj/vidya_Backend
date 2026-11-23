package com.vidya.tenantdata.fee.online.service;

import com.vidya.common.exception.BusinessException;
import com.vidya.tenantdata.fee.online.gateway.GatewayException;

import java.util.UUID;

/** References sent to gateways and the error shown when a gateway refuses. */
final class OnlineSupport {

    private OnlineSupport() {
    }

    /** A short unique reference, e.g. {@code MD3F9A…}: letters and digits only, accepted by both gateways as ids / receipts. */
    static String reference(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase();
    }

    static BusinessException gatewayError(GatewayException e) {
        return new BusinessException("FEE_GATEWAY_ERROR", "The payment gateway did not accept the request: " + e.getMessage());
    }
}
