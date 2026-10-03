package com.vidya.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a requested resource cannot be found.
 *
 * <p>Maps to HTTP 404.
 */
@Getter
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ResourceNotFoundException extends RuntimeException {

    private final String resourceType;
    private final Object identifier;

    public ResourceNotFoundException(String resourceType, Object identifier) {
        super(String.format("%s not found with identifier: %s", resourceType, identifier));
        this.resourceType = resourceType;
        this.identifier = identifier;
    }
}
