package com.elvo.billing.monitoring;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "elvo.billing.sentry.alerts")
public class SentryAlertProperties {

    private boolean enabled = true;
    private int criticalErrorThreshold = 1;
    private int adapterFailureThreshold = 3;
    private long anomalyLatencyThresholdMs = 2000L;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getCriticalErrorThreshold() {
        return criticalErrorThreshold;
    }

    public void setCriticalErrorThreshold(int criticalErrorThreshold) {
        this.criticalErrorThreshold = criticalErrorThreshold;
    }

    public int getAdapterFailureThreshold() {
        return adapterFailureThreshold;
    }

    public void setAdapterFailureThreshold(int adapterFailureThreshold) {
        this.adapterFailureThreshold = adapterFailureThreshold;
    }

    public long getAnomalyLatencyThresholdMs() {
        return anomalyLatencyThresholdMs;
    }

    public void setAnomalyLatencyThresholdMs(long anomalyLatencyThresholdMs) {
        this.anomalyLatencyThresholdMs = anomalyLatencyThresholdMs;
    }
}
