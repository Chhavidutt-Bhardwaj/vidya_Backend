package com.vidya.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a duplicate resource creation is attempted.
 *
 * <p>Maps to HTTP 409 Conflict.
 */
@Getter
@ResponseStatus(HttpStatus.CONFLICT)
public class DuplicateResourceException extends RuntimeException {

    private final String resourceType;
    private final String field;
    private final Object value;

    public DuplicateResourceException(String resourceType, String field, Object value) {
        super(String.format("%s already exists with %s: %s", resourceType, field, value));
        this.resourceType = resourceType;
        this.field = field;
        this.value = value;
    }
}
