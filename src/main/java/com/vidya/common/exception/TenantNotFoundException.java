package com.vidya.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a requested tenant cannot be found in the registry.
 *
 * <p>Maps to HTTP 404.
 */
@Getter
@ResponseStatus(HttpStatus.NOT_FOUND)
public class TenantNotFoundException extends RuntimeException {

    private final String tenantIdentifier;

    public TenantNotFoundException(String tenantIdentifier) {
        super(String.format("Tenant not found: %s", tenantIdentifier));
        this.tenantIdentifier = tenantIdentifier;
    }
}
