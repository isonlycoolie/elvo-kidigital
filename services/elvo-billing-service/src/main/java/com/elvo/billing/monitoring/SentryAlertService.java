package com.elvo.billing.monitoring;

import io.sentry.Sentry;
import io.sentry.SentryLevel;
import org.springframework.stereotype.Component;

@Component
public class SentryAlertService {

    private final SentryAlertProperties alertProperties;
    private final SentrySensitiveDataMasker sentrySensitiveDataMasker;

    public SentryAlertService(SentryAlertProperties alertProperties, SentrySensitiveDataMasker sentrySensitiveDataMasker) {
        this.alertProperties = alertProperties;
        this.sentrySensitiveDataMasker = sentrySensitiveDataMasker;
    }

    public void alertCriticalError(String source, String errorType, String message) {
        if (!alertProperties.isEnabled()) {
            return;
        }

        Sentry.withScope(scope -> {
            scope.setLevel(SentryLevel.ERROR);
            scope.setTag("alertType", "critical-error");
            scope.setTag("source", defaultTag(source));
            scope.setTag("errorType", defaultTag(errorType));
            scope.setTag("threshold", String.valueOf(alertProperties.getCriticalErrorThreshold()));
            Sentry.captureMessage(sentrySensitiveDataMasker.maskText(defaultTag(message)));
        });
    }

    public void alertAdapterFailure(String adapterName, int attempts, String message) {
        if (!alertProperties.isEnabled() || attempts < alertProperties.getAdapterFailureThreshold()) {
            return;
        }

        Sentry.withScope(scope -> {
            scope.setLevel(SentryLevel.ERROR);
            scope.setTag("alertType", "adapter-failure");
            scope.setTag("adapter", defaultTag(adapterName));
            scope.setTag("attempts", String.valueOf(attempts));
            Sentry.captureMessage(sentrySensitiveDataMasker.maskText(defaultTag(message)));
        });
    }

    public void alertAnomaly(String metric, long observedLatencyMs) {
        if (!alertProperties.isEnabled() || observedLatencyMs < alertProperties.getAnomalyLatencyThresholdMs()) {
            return;
        }

        Sentry.withScope(scope -> {
            scope.setLevel(SentryLevel.WARNING);
            scope.setTag("alertType", "performance-anomaly");
            scope.setTag("metric", defaultTag(metric));
            scope.setTag("thresholdMs", String.valueOf(alertProperties.getAnomalyLatencyThresholdMs()));
            scope.setTag("observedLatencyMs", String.valueOf(observedLatencyMs));
            Sentry.captureMessage("latency anomaly detected for " + defaultTag(metric));
        });
    }

    private static String defaultTag(String value) {
        return value == null || value.isBlank() ? "UNKNOWN" : value;
    }
}
