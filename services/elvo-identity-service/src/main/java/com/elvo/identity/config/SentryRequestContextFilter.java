package com.elvo.identity.config;

import java.io.IOException;

import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.sentry.Sentry;
import io.sentry.protocol.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class SentryRequestContextFilter extends OncePerRequestFilter {

    private final String serviceTag;

    public SentryRequestContextFilter(@Value("${elvo.monitoring.sentry.service-tag:elvo-identity-service}") String serviceTag) {
        this.serviceTag = serviceTag;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String correlationId = MDC.get(CorrelationIdFilter.CORRELATION_ID_KEY);
        String requestId = MDC.get(CorrelationIdFilter.REQUEST_ID_KEY);
        String userId = request.getHeader("X-User-Id");

        Sentry.configureScope(scope -> {
            scope.setTag("service", serviceTag);
            scope.setTag("http.method", request.getMethod());
            scope.setTag("http.path", request.getRequestURI());
            if (requestId != null && !requestId.isBlank()) {
                scope.setTag("requestId", requestId);
            }
            if (correlationId != null && !correlationId.isBlank()) {
                scope.setTag("correlationId", correlationId);
            }
            if (userId != null && !userId.isBlank()) {
                User sentryUser = new User();
                sentryUser.setId(userId);
                scope.setUser(sentryUser);
            }
        });

        try {
            filterChain.doFilter(request, response);
        } finally {
            Sentry.configureScope(scope -> {
                scope.removeTag("http.method");
                scope.removeTag("http.path");
                scope.removeTag("requestId");
                scope.removeTag("correlationId");
                scope.setUser(null);
            });
        }
    }
}
