package com.vidya.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a tenant provisioning step fails.
 *
 * <p>Maps to HTTP 500 — provisioning is an internal platform operation.
 *
 * <p>The error is logged with full detail internally. The external API
 * response will contain only a safe message without internal implementation details.
 */
@Getter
@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
public class TenantProvisioningException extends RuntimeException {

    private final String tenantCode;
    private final String step;

    public TenantProvisioningException(String tenantCode, String step, String message) {
        super(String.format("Tenant provisioning failed for [%s] at step [%s]: %s",
                tenantCode, step, message));
        this.tenantCode = tenantCode;
        this.step = step;
    }

    public TenantProvisioningException(String tenantCode, String step, String message, Throwable cause) {
        super(String.format("Tenant provisioning failed for [%s] at step [%s]: %s",
                tenantCode, step, message), cause);
        this.tenantCode = tenantCode;
        this.step = step;
    }
}
