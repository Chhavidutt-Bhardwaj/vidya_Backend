package com.vidya.tenantdata.fee.online.gateway;

import com.vidya.tenantdata.fee.online.enums.GatewayProvider;
import lombok.Getter;

/** The gateway refused a call, could not be reached, or a webhook signature did not match. */
@Getter
public class GatewayException extends RuntimeException {

    private final GatewayProvider provider;
    private final int httpStatus;
    private final boolean signatureInvalid;

    public GatewayException(GatewayProvider provider, int httpStatus, String message) {
        this(provider, httpStatus, message, false, null);
    }

    public GatewayException(GatewayProvider provider, String message, Throwable cause) {
        this(provider, 0, message, false, cause);
    }

    private GatewayException(GatewayProvider provider, int httpStatus, String message, boolean signatureInvalid, Throwable cause) {
        super(provider + ": " + message, cause);
        this.provider = provider;
        this.httpStatus = httpStatus;
        this.signatureInvalid = signatureInvalid;
    }

    public static GatewayException badSignature(GatewayProvider provider) {
        return new GatewayException(provider, 401, "webhook signature does not match", true, null);
    }
}
