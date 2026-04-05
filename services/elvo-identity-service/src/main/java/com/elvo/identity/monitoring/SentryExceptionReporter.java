package com.elvo.identity.monitoring;

import java.util.Map;

import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.elvo.identity.config.CorrelationIdFilter;

import io.sentry.Sentry;
import io.sentry.SentryLevel;
import io.sentry.IScope;
import io.sentry.protocol.User;
import jakarta.servlet.http.HttpServletRequest;

@Component
public class SentryExceptionReporter {

    private final String serviceTag;

    public SentryExceptionReporter(@Value("${elvo.monitoring.sentry.service-tag:elvo-identity-service}") String serviceTag) {
        this.serviceTag = serviceTag;
    }

    public void captureCriticalException(Throwable throwable,
                                         HttpServletRequest request,
                                         Map<String, Object> extraContext) {
        Sentry.withScope(scope -> {
            scope.setLevel(SentryLevel.ERROR);
            scope.setTag("service", serviceTag);
            enrichFromRequest(scope, request);
            if (extraContext != null) {
                extraContext.forEach((key, value) -> scope.setExtra(key, String.valueOf(value)));
            }
            Sentry.captureException(throwable);
        });
    }

    public void captureUnhandledException(Throwable throwable, String source) {
        Sentry.withScope(scope -> {
            scope.setLevel(SentryLevel.FATAL);
            scope.setTag("service", serviceTag);
            scope.setTag("exception.source", source);

            String requestId = MDC.get(CorrelationIdFilter.REQUEST_ID_KEY);
            String correlationId = MDC.get(CorrelationIdFilter.CORRELATION_ID_KEY);
            if (requestId != null && !requestId.isBlank()) {
                scope.setTag("requestId", requestId);
            }
            if (correlationId != null && !correlationId.isBlank()) {
                scope.setTag("correlationId", correlationId);
            }

            Sentry.captureException(throwable);
        });
    }

    private void enrichFromRequest(IScope scope, HttpServletRequest request) {
        String requestId = MDC.get(CorrelationIdFilter.REQUEST_ID_KEY);
        String correlationId = MDC.get(CorrelationIdFilter.CORRELATION_ID_KEY);
        if (request != null) {
            if (requestId == null || requestId.isBlank()) {
                requestId = request.getHeader(CorrelationIdFilter.REQUEST_ID_HEADER);
            }
            if (correlationId == null || correlationId.isBlank()) {
                correlationId = request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER);
            }
            String userId = request.getHeader("X-User-Id");
            if (userId != null && !userId.isBlank()) {
                User user = new User();
                user.setId(userId);
                scope.setUser(user);
                scope.setTag("userId", userId);
            }
            scope.setTag("http.path", request.getRequestURI());
            scope.setTag("http.method", request.getMethod());
        }

        if (requestId != null && !requestId.isBlank()) {
            scope.setTag("requestId", requestId);
        }
        if (correlationId != null && !correlationId.isBlank()) {
            scope.setTag("correlationId", correlationId);
        }
    }
}
