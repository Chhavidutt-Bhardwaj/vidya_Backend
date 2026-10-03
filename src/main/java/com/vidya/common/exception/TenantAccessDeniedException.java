package com.vidya.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when an authenticated user attempts to access a tenant they do not belong to.
 *
 * <p>Maps to HTTP 403 Forbidden.
 *
 * <p>This is the security guard that prevents users from accessing
 * another school's data by manipulating the tenant identifier in a request.
 */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class TenantAccessDeniedException extends RuntimeException {

    public TenantAccessDeniedException() {
        super("Access to the requested tenant is denied");
    }

    public TenantAccessDeniedException(String message) {
        super(message);
    }
}
