package com.elvo.wallet.service.orchestration;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import com.elvo.wallet.monitoring.SentryExceptionReporter;
import com.elvo.wallet.monitoring.SecurityAlertStreamingService;
import com.elvo.wallet.monitoring.WalletMetricsRecorder;
import com.elvo.wallet.service.impl.WalletLedgerIntegrationService;
import com.elvo.wallet.service.model.WalletFlowResult;

@Component
public class WalletSagaOrchestrator {

    private static final Logger AUDIT_LOG = LoggerFactory.getLogger("audit.wallet.saga");

    private final WalletLedgerIntegrationService ledgerIntegrationService;
    private final SentryExceptionReporter sentryExceptionReporter;
    private final WalletMetricsRecorder metricsRecorder;
    private final SecurityAlertStreamingService securityAlertStreamingService;
    private final int maxCompensationAttempts;
    private final Duration baseBackoff;

    public WalletSagaOrchestrator(WalletLedgerIntegrationService ledgerIntegrationService) {
        this(ledgerIntegrationService, null, null, null, 3, 250);
    }

    @Autowired
    public WalletSagaOrchestrator(WalletLedgerIntegrationService ledgerIntegrationService,
                                  @Nullable SentryExceptionReporter sentryExceptionReporter,
                                  @Nullable WalletMetricsRecorder metricsRecorder,
                                  @Nullable SecurityAlertStreamingService securityAlertStreamingService,
                                  @Value("${elvo.security.saga.compensation.max-attempts:3}") int maxCompensationAttempts,
                                  @Value("${elvo.security.saga.compensation.base-backoff-ms:250}") long baseBackoffMs) {
        this.ledgerIntegrationService = ledgerIntegrationService;
        this.sentryExceptionReporter = sentryExceptionReporter;
        this.metricsRecorder = metricsRecorder;
        this.securityAlertStreamingService = securityAlertStreamingService;
        this.maxCompensationAttempts = Math.max(1, maxCompensationAttempts);
        this.baseBackoff = Duration.ofMillis(Math.max(1L, baseBackoffMs));
    }

    public WalletFlowResult compensate(String flow,
                                       UUID walletId,
                                       BigDecimal amount,
                                       String reference,
                                       RuntimeException exception) {
        if (metricsRecorder != null) {
            metricsRecorder.recordSagaCompensation(flow);
        }

        RuntimeException lastError = exception;
        for (int attempt = 1; attempt <= maxCompensationAttempts; attempt++) {
            try {
                ledgerIntegrationService.reconcileEntry(flow, walletId, amount, reference);
                AUDIT_LOG.error("wallet_saga_compensation flow={} walletId={} amount={} reference={} reason={} attempt={}",
                        flow,
                        walletId,
                        amount,
                        reference,
                        exception.getMessage(),
                        attempt);
                return WalletFlowResult.failure("Saga compensation executed for " + flow, walletId, "wallet." + flow + ".failed");
            } catch (RuntimeException ex) {
                lastError = ex;
                AUDIT_LOG.error("wallet_saga_compensation_attempt_failed flow={} walletId={} amount={} reference={} attempt={} maxAttempts={} reason={}",
                        flow,
                        walletId,
                        amount,
                        reference,
                        attempt,
                        maxCompensationAttempts,
                        ex.getMessage());
                if (attempt < maxCompensationAttempts) {
                    pause(backoffFor(attempt));
                }
            }
        }

        if (sentryExceptionReporter != null) {
            sentryExceptionReporter.captureCriticalException(
                    lastError,
                    null,
                    java.util.Map.of(
                            "flow", flow,
                            "walletId", String.valueOf(walletId),
                            "reference", String.valueOf(reference),
                            "amount", String.valueOf(amount),
                            "type", "saga-compensation-escalated"));
        }
        if (securityAlertStreamingService != null) {
            securityAlertStreamingService.stream(
                    "wallet.saga.compensation.escalated",
                    "HIGH",
                    walletId,
                    java.util.Map.of(
                            "flow", String.valueOf(flow),
                            "reference", String.valueOf(reference),
                            "attempts", maxCompensationAttempts,
                            "reason", lastError == null ? "unknown" : String.valueOf(lastError.getMessage())));
        }
        return WalletFlowResult.failure("Saga compensation escalated for " + flow, walletId, "wallet." + flow + ".failed");
    }

    private Duration backoffFor(int attempt) {
        long multiplier = 1L << Math.min(Math.max(0, attempt - 1), 6);
        return baseBackoff.multipliedBy(multiplier);
    }

    private void pause(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    public void begin(String flow, UUID walletId, BigDecimal amount, String reference) {
        AUDIT_LOG.info("wallet_saga_started flow={} walletId={} amount={} reference={}", flow, walletId, amount, reference);
    }

    public void complete(String flow, UUID walletId, BigDecimal amount, String reference) {
        AUDIT_LOG.info("wallet_saga_completed flow={} walletId={} amount={} reference={}", flow, walletId, amount, reference);
    }
}
