package com.elvo.billing.monitoring;

import io.sentry.Sentry;
import io.sentry.SentryLevel;
import org.springframework.stereotype.Component;

@Component
public class SentryErrorCapture {

    public void capturePaymentFailure(String serviceCode, String referenceNumber, Throwable throwable) {
        Sentry.withScope(scope -> {
            scope.setLevel(SentryLevel.ERROR);
            scope.setTag("domain", "billing-payment");
            scope.setTag("serviceCode", defaultTag(serviceCode));
            scope.setTag("referenceNumber", defaultTag(referenceNumber));
            Sentry.captureException(throwable);
        });
    }

    public void captureLookupFailure(String serviceCode, String referenceNumber, Throwable throwable) {
        Sentry.withScope(scope -> {
            scope.setLevel(SentryLevel.ERROR);
            scope.setTag("domain", "billing-lookup");
            scope.setTag("serviceCode", defaultTag(serviceCode));
            scope.setTag("referenceNumber", defaultTag(referenceNumber));
            Sentry.captureException(throwable);
        });
    }

    public void captureAdapterRetryFailure(String adapterName, int attempt, Throwable throwable) {
        Sentry.withScope(scope -> {
            scope.setLevel(SentryLevel.WARNING);
            scope.setTag("domain", "billing-adapter");
            scope.setTag("adapter", defaultTag(adapterName));
            scope.setTag("attempt", String.valueOf(attempt));
            Sentry.captureException(throwable);
        });
    }

    public void captureAdapterRetriesExhausted(String adapterName, int maxAttempts, Throwable throwable) {
        Sentry.withScope(scope -> {
            scope.setLevel(SentryLevel.ERROR);
            scope.setTag("domain", "billing-adapter");
            scope.setTag("adapter", defaultTag(adapterName));
            scope.setTag("maxAttempts", String.valueOf(maxAttempts));
            scope.setTag("failureType", "retries-exhausted");
            Sentry.captureException(throwable);
        });
    }

    private static String defaultTag(String value) {
        return value == null || value.isBlank() ? "UNKNOWN" : value;
    }
}