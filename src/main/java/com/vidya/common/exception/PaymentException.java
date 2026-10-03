package com.vidya.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a payment operation fails or the provider returns an error.
 *
 * <p>Maps to HTTP 422 Unprocessable Content.
 */
@Getter
@ResponseStatus(HttpStatus.UNPROCESSABLE_CONTENT)
public class PaymentException extends RuntimeException {

    private final String code;
    private final String provider;

    public PaymentException(String code, String provider, String message) {
        super(message);
        this.code = code;
        this.provider = provider;
    }

    public PaymentException(String code, String provider, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.provider = provider;
    }
}
