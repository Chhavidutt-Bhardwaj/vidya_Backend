package com.vidya.common.exception;

import com.vidya.common.api.ApiResponse;
import com.vidya.common.api.ApiResponseCode;
import com.vidya.common.correlation.MdcKeys;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * Central exception handler for all REST controllers.
 *
 * <p>Guarantees:
 * <ul>
 *   <li>Stack traces are NEVER returned in the response body</li>
 *   <li>Internal database/JPA details are NEVER leaked</li>
 *   <li>Every error response includes a correlationId for log tracing</li>
 *   <li>Unexpected exceptions are logged at ERROR level with full detail</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ── Validation ────────────────────────────────────────────────────────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidation(
            MethodArgumentNotValidException ex) {

        Map<String, String> errors = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }

        log.warn("[{}] Validation failed: {}", correlationId(), errors);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.<Map<String, String>>error(
                        ApiResponseCode.VALIDATION_ERROR,
                        "Request validation failed"
                ).withCorrelationId(correlationId()));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(
            ConstraintViolationException ex) {

        log.warn("[{}] Constraint violation: {}", correlationId(), ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.<Void>error(
                        ApiResponseCode.VALIDATION_ERROR,
                        "Constraint violation: " + ex.getMessage()
                ).withCorrelationId(correlationId()));
    }

    // ── Domain Exceptions ─────────────────────────────────────────────────────

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(ResourceNotFoundException ex) {
        log.info("[{}] Resource not found: {}", correlationId(), ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.<Void>error(ApiResponseCode.RESOURCE_NOT_FOUND, ex.getMessage())
                        .withCorrelationId(correlationId()));
    }

    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(org.springframework.web.servlet.resource.NoResourceFoundException ex) {
        log.warn("[{}] Static resource or endpoint not found: {}", correlationId(), ex.getResourcePath());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.<Void>error(ApiResponseCode.RESOURCE_NOT_FOUND, "Resource or endpoint not found: " + ex.getResourcePath())
                        .withCorrelationId(correlationId()));
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicate(DuplicateResourceException ex) {
        log.info("[{}] Duplicate resource: {}", correlationId(), ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.<Void>error(ApiResponseCode.DUPLICATE_RESOURCE, ex.getMessage())
                        .withCorrelationId(correlationId()));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        log.info("[{}] Business rule violation [{}]: {}", correlationId(), ex.getCode(), ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_CONTENT)
                .body(ApiResponse.<Void>error(ex.getCode(), ex.getMessage())
                        .withCorrelationId(correlationId()));
    }

    @ExceptionHandler(InvalidStateTransitionException.class)
    public ResponseEntity<ApiResponse<Void>> handleStateTransition(
            InvalidStateTransitionException ex) {
        log.info("[{}] Invalid state transition: {}", correlationId(), ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_CONTENT)
                .body(ApiResponse.<Void>error(ApiResponseCode.INVALID_STATE_TRANSITION, ex.getMessage())
                        .withCorrelationId(correlationId()));
    }

    // ── Tenant Exceptions ─────────────────────────────────────────────────────

    @ExceptionHandler(TenantNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleTenantNotFound(TenantNotFoundException ex) {
        log.warn("[{}] Tenant not found: {}", correlationId(), ex.getTenantIdentifier());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.<Void>error(ApiResponseCode.TENANT_NOT_FOUND, ex.getMessage())
                        .withCorrelationId(correlationId()));
    }

    @ExceptionHandler(TenantAccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleTenantAccessDenied(
            TenantAccessDeniedException ex) {
        log.warn("[{}] Tenant access denied: {}", correlationId(), ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.<Void>error(ApiResponseCode.TENANT_ACCESS_DENIED, ex.getMessage())
                        .withCorrelationId(correlationId()));
    }

    @ExceptionHandler(TenantSuspendedException.class)
    public ResponseEntity<ApiResponse<Void>> handleTenantSuspended(TenantSuspendedException ex) {
        log.warn("[{}] Tenant suspended: {}", correlationId(), ex.getTenantCode());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.<Void>error(ApiResponseCode.TENANT_SUSPENDED, ex.getMessage())
                        .withCorrelationId(correlationId()));
    }

    @ExceptionHandler(TenantProvisioningException.class)
    public ResponseEntity<ApiResponse<Void>> handleProvisioning(TenantProvisioningException ex) {
        // Log full detail — DO NOT expose in response
        log.error("[{}] Tenant provisioning failed at step [{}] for tenant [{}]",
                correlationId(), ex.getStep(), ex.getTenantCode(), ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.<Void>error(
                        ApiResponseCode.TENANT_PROVISIONING_FAILED,
                        "Tenant provisioning encountered an error. Please contact support with correlationId: " + correlationId()
                ).withCorrelationId(correlationId()));
    }

    // ── Payment Exceptions ────────────────────────────────────────────────────

    @ExceptionHandler(PaymentException.class)
    public ResponseEntity<ApiResponse<Void>> handlePayment(PaymentException ex) {
        log.error("[{}] Payment error [provider={}] [code={}]: {}",
                correlationId(), ex.getProvider(), ex.getCode(), ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_CONTENT)
                .body(ApiResponse.<Void>error(ApiResponseCode.PAYMENT_FAILED,
                        "Payment processing failed. Please try again or contact support.")
                        .withCorrelationId(correlationId()));
    }

    // ── Spring Security & Authentication ──────────────────────────────────────

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnauthorized(UnauthorizedException ex) {
        log.info("[{}] Unauthorized access: {}", correlationId(), ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.<Void>error(ApiResponseCode.UNAUTHORIZED, ex.getMessage())
                        .withCorrelationId(correlationId()));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException ex) {
        log.info("[{}] Authentication failed: {}", correlationId(), ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.<Void>error(ApiResponseCode.UNAUTHORIZED, "Authentication required")
                        .withCorrelationId(correlationId()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        log.warn("[{}] Access denied: {}", correlationId(), ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.<Void>error(ApiResponseCode.FORBIDDEN, "Access denied")
                        .withCorrelationId(correlationId()));
    }

    // ── Invalid Arguments ──────────────────────────────────────────────────

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("[{}] Invalid argument: {}", correlationId(), ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.<Void>error(ApiResponseCode.INVALID_ARGUMENT, ex.getMessage())
                        .withCorrelationId(correlationId()));
    }

    // ── Catch-All ─────────────────────────────────────────────────────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleAll(Exception ex) {
        // Full stack trace only in logs — never in response
        log.error("[{}] Unexpected error", correlationId(), ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.<Void>error(
                        ApiResponseCode.INTERNAL_ERROR,
                        "An unexpected error occurred. CorrelationId: " + correlationId()
                ).withCorrelationId(correlationId()));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private String correlationId() {
        String id = MDC.get(MdcKeys.CORRELATION_ID);
        return id != null ? id : "unknown";
    }
}
