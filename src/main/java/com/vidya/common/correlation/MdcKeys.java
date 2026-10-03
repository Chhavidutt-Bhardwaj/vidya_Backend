package com.vidya.common.correlation;

/**
 * MDC (Mapped Diagnostic Context) key constants.
 *
 * <p>These keys are used across filters, services, and the logging pattern.
 * The logging pattern in application.yml references these keys directly.
 *
 * <p>Logging pattern: {@code [%X{correlationId}] [%X{tenantId}] [%X{userId}]}
 */
public final class MdcKeys {

    private MdcKeys() {}

    /** Unique per-request identifier. Propagated in response header. */
    public static final String CORRELATION_ID = "correlationId";

    /** Active tenant identifier for the current request. */
    public static final String TENANT_ID = "tenantId";

    /** Authenticated user's UUID. */
    public static final String USER_ID = "userId";

    /** School identifier associated with the current request. */
    public static final String SCHOOL_ID = "schoolId";

    /** HTTP method for the current request. */
    public static final String HTTP_METHOD = "httpMethod";

    /** Request URI. */
    public static final String REQUEST_URI = "requestUri";
}
