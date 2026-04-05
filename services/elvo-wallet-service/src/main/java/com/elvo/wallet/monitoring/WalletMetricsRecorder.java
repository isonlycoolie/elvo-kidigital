package com.elvo.wallet.monitoring;

import java.math.BigDecimal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;

@Component
public class WalletMetricsRecorder {

    private static final String METRIC_TRANSACTIONS_TOTAL = "wallet_transactions_total";
    private static final String METRIC_BALANCE_CHANGE_AMOUNT = "wallet_balance_change_amount";
    private static final String METRIC_RESERVATIONS_TOTAL = "wallet_reservations_total";
    private static final String METRIC_FREEZE_ACTIONS_TOTAL = "wallet_freeze_actions_total";
    private static final String METRIC_SAGA_COMPENSATIONS_TOTAL = "wallet_saga_compensations_total";
    private static final String METRIC_EVENT_PUBLISH_TOTAL = "wallet_event_publish_total";

    private final MeterRegistry meterRegistry;
    private final String serviceTag;

    public WalletMetricsRecorder(MeterRegistry meterRegistry,
                                 @Value("${elvo.monitoring.sentry.service-tag:wallet-service}") String serviceTag) {
        this.meterRegistry = meterRegistry;
        this.serviceTag = serviceTag;
    }

    public void recordTransaction(String flow, boolean success) {
        Counter.builder(METRIC_TRANSACTIONS_TOTAL)
                .description("Wallet transaction flow executions grouped by outcome")
                .tag("service", serviceTag)
                .tag("flow", sanitize(flow))
                .tag("outcome", success ? "success" : "failure")
                .register(meterRegistry)
                .increment();
    }

    public void recordBalanceChange(String flow, String direction, BigDecimal amount) {
        if (amount == null) {
            return;
        }
        DistributionSummary.builder(METRIC_BALANCE_CHANGE_AMOUNT)
                .description("Absolute wallet balance change amounts")
                .baseUnit("currency_units")
                .tag("service", serviceTag)
                .tag("flow", sanitize(flow))
                .tag("direction", sanitize(direction))
                .register(meterRegistry)
                .record(amount.abs().doubleValue());
    }

    public void recordReservation(String action, boolean success) {
        Counter.builder(METRIC_RESERVATIONS_TOTAL)
                .description("Wallet reservation operations grouped by action and outcome")
                .tag("service", serviceTag)
                .tag("action", sanitize(action))
                .tag("outcome", success ? "success" : "failure")
                .register(meterRegistry)
                .increment();
    }

    public void recordFreezeAction(String action, boolean success) {
        Counter.builder(METRIC_FREEZE_ACTIONS_TOTAL)
                .description("Wallet freeze/unfreeze operations grouped by outcome")
                .tag("service", serviceTag)
                .tag("action", sanitize(action))
                .tag("outcome", success ? "success" : "failure")
                .register(meterRegistry)
                .increment();
    }

    public void recordSagaCompensation(String flow) {
        Counter.builder(METRIC_SAGA_COMPENSATIONS_TOTAL)
                .description("Wallet saga compensation invocations")
                .tag("service", serviceTag)
                .tag("flow", sanitize(flow))
                .register(meterRegistry)
                .increment();
    }

    public void recordEventPublish(String eventType, boolean success) {
        Counter.builder(METRIC_EVENT_PUBLISH_TOTAL)
                .description("Wallet event publish attempts grouped by outcome")
                .tag("service", serviceTag)
                .tag("event_type", sanitize(eventType))
                .tag("outcome", success ? "success" : "failure")
                .register(meterRegistry)
                .increment();
    }

    private String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.trim().toLowerCase().replace(' ', '_');
    }
}