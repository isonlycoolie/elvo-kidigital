package com.elvo.billing.monitoring;

import com.elvo.billing.exception.DuplicatePaymentException;
import com.elvo.billing.exception.PaymentValidationException;
import io.sentry.Sentry;
import io.sentry.SentryLevel;
import org.springframework.stereotype.Component;

@Component
public class SentryExceptionMapper {

    public void capture(Exception exception, String errorCode) {
        String errorType = exception == null ? "UNKNOWN" : exception.getClass().getSimpleName();
        Sentry.withScope(scope -> {
            scope.setTag("domain", "billing");
            scope.setTag("errorCode", defaultTag(errorCode));
            scope.setTag("exceptionType", defaultTag(errorType));
            scope.setTag("errorType", defaultTag(errorType));
            scope.setTag("issueGroup", "domain:" + defaultTag(errorCode) + ":" + defaultTag(errorType));

            if (exception instanceof PaymentValidationException paymentValidationException) {
                scope.setLevel(SentryLevel.WARNING);
                if (paymentValidationException.getFieldErrors() != null && !paymentValidationException.getFieldErrors().isEmpty()) {
                    scope.setContexts("fieldErrors", paymentValidationException.getFieldErrors());
                }
            } else if (exception instanceof DuplicatePaymentException) {
                scope.setLevel(SentryLevel.INFO);
                scope.setTag("failureType", "duplicate-request");
            } else {
                scope.setLevel(SentryLevel.ERROR);
            }

            Sentry.captureException(exception);
        });
    }

    private static String defaultTag(String value) {
        return value == null || value.isBlank() ? "UNKNOWN" : value;
    }
}