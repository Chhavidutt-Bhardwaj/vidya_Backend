package com.vidya.common.exception;

import com.vidya.common.api.ApiResponse;
import com.vidya.common.api.ApiResponseCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlingTest {

    private GlobalExceptionHandler exceptionHandler;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
    }

    @Test
    @DisplayName("UnauthorizedException maps to HTTP 401 UNAUTHORIZED")
    void handleUnauthorizedException() {
        UnauthorizedException ex = new UnauthorizedException("Invalid token");
        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleUnauthorized(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getCode()).isEqualTo(ApiResponseCode.UNAUTHORIZED);
    }

    @Test
    @DisplayName("AccessDeniedException maps to HTTP 403 FORBIDDEN")
    void handleAccessDeniedException() {
        AccessDeniedException ex = new AccessDeniedException("Forbidden action");
        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleAccessDenied(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getCode()).isEqualTo(ApiResponseCode.FORBIDDEN);
    }

    @Test
    @DisplayName("TenantNotFoundException maps to HTTP 404 NOT_FOUND")
    void handleTenantNotFoundException() {
        TenantNotFoundException ex = new TenantNotFoundException("TNT-UNKNOWN");
        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleTenantNotFound(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(ApiResponseCode.TENANT_NOT_FOUND);
    }

    @Test
    @DisplayName("TenantSuspendedException maps to HTTP 403 FORBIDDEN")
    void handleTenantSuspendedException() {
        TenantSuspendedException ex = new TenantSuspendedException("TNT-SUSPENDED");
        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleTenantSuspended(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(ApiResponseCode.TENANT_SUSPENDED);
    }

    @Test
    @DisplayName("InvalidStateTransitionException maps to HTTP 422 UNPROCESSABLE_ENTITY")
    void handleInvalidStateTransition() {
        InvalidStateTransitionException ex = new InvalidStateTransitionException("SchoolOnboarding", "DRAFT", "ACTIVE");
        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleStateTransition(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(ApiResponseCode.INVALID_STATE_TRANSITION);
    }

    @Test
    @DisplayName("Generic unhandled Exception maps to HTTP 500 without leaking stack traces")
    void handleGenericException() {
        Exception ex = new NullPointerException("Secret connection string null pointer");
        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleAll(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getCode()).isEqualTo(ApiResponseCode.INTERNAL_ERROR);
        // Ensure secret error message is NOT exposed to client
        assertThat(response.getBody().getMessage()).doesNotContain("Secret connection string");
        assertThat(response.getBody().getMessage()).contains("An unexpected error occurred");
    }
}
