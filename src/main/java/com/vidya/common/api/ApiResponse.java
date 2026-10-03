package com.vidya.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * Unified API response envelope.
 *
 * <p>All controller responses must be wrapped in this envelope.
 * Never return raw entities or arbitrary objects from controllers.
 *
 * @param <T> the payload type
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private final boolean success;
    private final String code;
    private final String message;
    private final T data;
    private final Instant timestamp;
    private final String correlationId;

    // -------------------------------------------------------------------------
    // Factory methods
    // -------------------------------------------------------------------------

    public static <T> ApiResponse<T> success(String code, String message, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .code(code)
                .message(message)
                .data(data)
                .timestamp(Instant.now())
                .build();
    }

    public static <T> ApiResponse<T> success(String code, String message) {
        return ApiResponse.<T>builder()
                .success(true)
                .code(code)
                .message(message)
                .timestamp(Instant.now())
                .build();
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .code(code)
                .message(message)
                .timestamp(Instant.now())
                .build();
    }

    public ApiResponse<T> withCorrelationId(String correlationId) {
        return ApiResponse.<T>builder()
                .success(this.success)
                .code(this.code)
                .message(this.message)
                .data(this.data)
                .timestamp(this.timestamp)
                .correlationId(correlationId)
                .build();
    }
}
