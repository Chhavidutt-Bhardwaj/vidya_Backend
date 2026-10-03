package com.vidya.common.correlation;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * HTTP filter that attaches a correlation ID to every request.
 *
 * <p>The correlation ID is either taken from the incoming {@code X-Correlation-ID}
 * request header or generated fresh as a UUID.
 *
 * <p>The correlation ID is:
 * <ul>
 *   <li>Placed in MDC so all log statements include it automatically</li>
 *   <li>Written to the {@code X-Correlation-ID} response header</li>
 *   <li>Cleared from MDC in a {@code finally} block to prevent leakage</li>
 * </ul>
 *
 * <p>This filter runs first (Order = 1) to ensure all downstream filters
 * and handlers have access to the correlation ID.
 */
@Component
@Order(1)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String REQUEST_HEADER  = "X-Correlation-ID";
    public static final String RESPONSE_HEADER = "X-Correlation-ID";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String correlationId = resolveCorrelationId(request);

        try {
            MDC.put(MdcKeys.CORRELATION_ID, correlationId);
            MDC.put(MdcKeys.HTTP_METHOD, request.getMethod());
            MDC.put(MdcKeys.REQUEST_URI, request.getRequestURI());

            response.setHeader(RESPONSE_HEADER, correlationId);

            filterChain.doFilter(request, response);
        } finally {
            // CRITICAL: always clear MDC — prevents leakage in thread pool reuse
            MDC.remove(MdcKeys.CORRELATION_ID);
            MDC.remove(MdcKeys.HTTP_METHOD);
            MDC.remove(MdcKeys.REQUEST_URI);
            MDC.remove(MdcKeys.TENANT_ID);
            MDC.remove(MdcKeys.USER_ID);
            MDC.remove(MdcKeys.SCHOOL_ID);
        }
    }

    private String resolveCorrelationId(HttpServletRequest request) {
        String incoming = request.getHeader(REQUEST_HEADER);
        if (incoming != null && !incoming.isBlank()) {
            // Sanitize to prevent log injection
            return incoming.replaceAll("[^a-zA-Z0-9\\-]", "").substring(0, Math.min(incoming.length(), 64));
        }
        return UUID.randomUUID().toString();
    }
}
