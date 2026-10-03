package com.vidya.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a business rule is violated.
 *
 * <p>Maps to HTTP 422 Unprocessable Content.
 * Use for domain-level invariant violations that are not validation errors.
 */
@Getter
@ResponseStatus(HttpStatus.UNPROCESSABLE_CONTENT)
public class BusinessException extends RuntimeException {

    private final String code;

    public BusinessException(String message) {
        this("BUSINESS_RULE_VIOLATION", message);
    }

    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
    }
}
