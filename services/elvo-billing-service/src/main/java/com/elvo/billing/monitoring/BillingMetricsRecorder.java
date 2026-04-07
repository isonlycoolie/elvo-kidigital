package com.elvo.billing.monitoring;

import java.util.concurrent.atomic.AtomicLong;

import com.elvo.billing.entity.enums.LookupStatus;
import com.elvo.billing.entity.enums.PaymentStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class BillingMetricsRecorder {

    private final MeterRegistry meterRegistry;
    private final SentryAlertService sentryAlertService;
    private final AtomicLong pendingPaymentsGauge = new AtomicLong(0L);

    public BillingMetricsRecorder(MeterRegistry meterRegistry, SentryAlertService sentryAlertService) {
        this.meterRegistry = meterRegistry;
        this.sentryAlertService = sentryAlertService;
        meterRegistry.gauge("billing.payment.pending", pendingPaymentsGauge);
    }

    public void recordPaymentOutcome(PaymentStatus status, long durationNanos) {
        String statusTag = status == null ? "UNKNOWN" : status.name();
        Counter.builder("billing.payment.outcome.total")
                .tag("status", statusTag)
                .register(meterRegistry)
                .increment();

        Timer.builder("billing.payment.latency")
                .tag("status", statusTag)
                .register(meterRegistry)
                .record(durationNanos, java.util.concurrent.TimeUnit.NANOSECONDS);
        sentryAlertService.alertAnomaly("billing.payment.latency", java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(durationNanos));
    }

    public void recordLookupOutcome(LookupStatus status, long durationNanos) {
        String statusTag = status == null ? "UNKNOWN" : status.name();
        Counter.builder("billing.lookup.outcome.total")
                .tag("status", statusTag)
                .register(meterRegistry)
                .increment();

        Timer.builder("billing.lookup.latency")
                .tag("status", statusTag)
                .register(meterRegistry)
                .record(durationNanos, java.util.concurrent.TimeUnit.NANOSECONDS);
        sentryAlertService.alertAnomaly("billing.lookup.latency", java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(durationNanos));
    }

    public void recordRetry(String adapterName) {
        Counter.builder("billing.adapter.retry.total")
                .tag("adapter", adapterName == null ? "UNKNOWN" : adapterName)
                .register(meterRegistry)
                .increment();
    }

    public void recordPendingPayments(long pendingCount) {
        pendingPaymentsGauge.set(Math.max(0L, pendingCount));
    }
}