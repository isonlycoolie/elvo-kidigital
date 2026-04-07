package com.elvo.billing.monitoring;

import io.sentry.Sentry;
import io.sentry.SentryLevel;
import org.springframework.stereotype.Component;

@Component
public class SentryErrorCapture {

    private final SentryAlertService sentryAlertService;
    private final SentrySensitiveDataMasker sentrySensitiveDataMasker;

    public SentryErrorCapture(SentryAlertService sentryAlertService, SentrySensitiveDataMasker sentrySensitiveDataMasker) {
        this.sentryAlertService = sentryAlertService;
        this.sentrySensitiveDataMasker = sentrySensitiveDataMasker;
    }

    public void capturePaymentFailure(String paymentCategory, String serviceCode, String referenceNumber, Throwable throwable) {
        String errorType = throwable == null ? "UNKNOWN" : throwable.getClass().getSimpleName();
        sentryAlertService.alertCriticalError(
                "payment",
                errorType,
                sentrySensitiveDataMasker.maskText(throwable == null ? "UNKNOWN" : throwable.getMessage()));
        Sentry.withScope(scope -> {
            scope.setLevel(SentryLevel.ERROR);
            scope.setTag("domain", "billing-payment");
            scope.setTag("paymentCategory", defaultTag(paymentCategory));
            scope.setTag("serviceCode", defaultTag(serviceCode));
            scope.setTag("referenceNumber", sentrySensitiveDataMasker.maskReference(referenceNumber));
            scope.setTag("errorType", defaultTag(errorType));
            scope.setTag("issueGroup", "payment:" + defaultTag(paymentCategory) + ":" + defaultTag(serviceCode) + ":" + defaultTag(errorType));
            Sentry.captureException(throwable);
        });
    }

    public void captureLookupFailure(String paymentCategory, String serviceCode, String referenceNumber, Throwable throwable) {
        String errorType = throwable == null ? "UNKNOWN" : throwable.getClass().getSimpleName();
        sentryAlertService.alertCriticalError(
                "lookup",
                errorType,
                sentrySensitiveDataMasker.maskText(throwable == null ? "UNKNOWN" : throwable.getMessage()));
        Sentry.withScope(scope -> {
            scope.setLevel(SentryLevel.ERROR);
            scope.setTag("domain", "billing-lookup");
            scope.setTag("paymentCategory", defaultTag(paymentCategory));
            scope.setTag("serviceCode", defaultTag(serviceCode));
            scope.setTag("referenceNumber", sentrySensitiveDataMasker.maskReference(referenceNumber));
            scope.setTag("errorType", defaultTag(errorType));
            scope.setTag("issueGroup", "lookup:" + defaultTag(paymentCategory) + ":" + defaultTag(serviceCode) + ":" + defaultTag(errorType));
            Sentry.captureException(throwable);
        });
    }

    public void captureAdapterRetryFailure(String adapterName, int attempt, Throwable throwable) {
        String errorType = throwable == null ? "UNKNOWN" : throwable.getClass().getSimpleName();
        sentryAlertService.alertAdapterFailure(
            adapterName,
            attempt,
            sentrySensitiveDataMasker.maskText(throwable == null ? "UNKNOWN" : throwable.getMessage()));
        Sentry.withScope(scope -> {
            scope.setLevel(SentryLevel.WARNING);
            scope.setTag("domain", "billing-adapter");
            scope.setTag("adapter", defaultTag(adapterName));
            scope.setTag("attempt", String.valueOf(attempt));
            scope.setTag("errorType", defaultTag(errorType));
            scope.setTag("issueGroup", "adapter-retry:" + defaultTag(adapterName) + ":" + defaultTag(errorType));
            Sentry.captureException(throwable);
        });
    }

    public void captureAdapterRetriesExhausted(String adapterName, int maxAttempts, Throwable throwable) {
        String errorType = throwable == null ? "UNKNOWN" : throwable.getClass().getSimpleName();
        sentryAlertService.alertCriticalError(
            "adapter",
            errorType,
            sentrySensitiveDataMasker.maskText(throwable == null ? "UNKNOWN" : throwable.getMessage()));
        Sentry.withScope(scope -> {
            scope.setLevel(SentryLevel.ERROR);
            scope.setTag("domain", "billing-adapter");
            scope.setTag("adapter", defaultTag(adapterName));
            scope.setTag("maxAttempts", String.valueOf(maxAttempts));
            scope.setTag("failureType", "retries-exhausted");
            scope.setTag("errorType", defaultTag(errorType));
            scope.setTag("issueGroup", "adapter-exhausted:" + defaultTag(adapterName) + ":" + defaultTag(errorType));
            Sentry.captureException(throwable);
        });
    }

    private static String defaultTag(String value) {
        return value == null || value.isBlank() ? "UNKNOWN" : value;
    }
}