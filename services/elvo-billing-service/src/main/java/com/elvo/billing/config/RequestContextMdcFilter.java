package com.elvo.billing.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class RequestContextMdcFilter extends OncePerRequestFilter {

    private static final String HEADER_CORRELATION_ID = "X-Correlation-Id";
    private static final String HEADER_REQUEST_ID = "X-Request-Id";
    private static final String HEADER_IDEMPOTENCY_KEY = "Idempotency-Key";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String correlationId = firstNonBlank(request.getHeader(HEADER_CORRELATION_ID), UUID.randomUUID().toString());
        String requestId = firstNonBlank(request.getHeader(HEADER_REQUEST_ID), UUID.randomUUID().toString());
        String idempotencyKey = firstNonBlank(request.getHeader(HEADER_IDEMPOTENCY_KEY), "n/a");

        MDC.put("correlationId", correlationId);
        MDC.put("requestId", requestId);
        MDC.put("idempotencyKey", idempotencyKey);

        response.setHeader(HEADER_CORRELATION_ID, correlationId);
        response.setHeader(HEADER_REQUEST_ID, requestId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("correlationId");
            MDC.remove("requestId");
            MDC.remove("idempotencyKey");
        }
    }

    private static String firstNonBlank(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }
}