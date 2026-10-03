package com.vidya.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a tenant is in SUSPENDED state and cannot process requests.
 *
 * <p>Maps to HTTP 403 Forbidden.
 */
@Getter
@ResponseStatus(HttpStatus.FORBIDDEN)
public class TenantSuspendedException extends RuntimeException {

    private final String tenantCode;

    public TenantSuspendedException(String tenantCode) {
        super(String.format("Tenant [%s] is currently suspended", tenantCode));
        this.tenantCode = tenantCode;
    }
}
