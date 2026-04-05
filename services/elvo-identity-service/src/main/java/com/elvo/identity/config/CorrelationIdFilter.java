package com.elvo.identity.config;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(CorrelationIdFilter.class);

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String CORRELATION_ID_KEY = "correlationId";
    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String REQUEST_ID_KEY = "requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String correlationId = resolveIdentifier(request.getHeader(CORRELATION_ID_HEADER));
        String requestId = resolveIdentifier(request.getHeader(REQUEST_ID_HEADER));
        long startNs = System.nanoTime();

        MDC.put(CORRELATION_ID_KEY, correlationId);
        MDC.put(REQUEST_ID_KEY, requestId);
        response.setHeader(CORRELATION_ID_HEADER, correlationId);
        response.setHeader(REQUEST_ID_HEADER, requestId);

        LOGGER.info("Incoming request {} {}", request.getMethod(), request.getRequestURI());
        try {
            filterChain.doFilter(request, response);
        } finally {
            long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
            LOGGER.info("Completed request {} {} with status {} in {} ms",
                    request.getMethod(), request.getRequestURI(), response.getStatus(), elapsedMs);
            MDC.remove(CORRELATION_ID_KEY);
            MDC.remove(REQUEST_ID_KEY);
        }
    }

    private static String resolveIdentifier(String existingValue) {
        if (existingValue == null || existingValue.isBlank()) {
            return UUID.randomUUID().toString();
        }

        return existingValue;
    }
}
