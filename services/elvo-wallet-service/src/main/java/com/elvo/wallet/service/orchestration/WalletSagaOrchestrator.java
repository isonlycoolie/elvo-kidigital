package com.elvo.wallet.service.orchestration;

import java.math.BigDecimal;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import com.elvo.wallet.monitoring.SentryExceptionReporter;
import com.elvo.wallet.monitoring.WalletMetricsRecorder;
import com.elvo.wallet.service.impl.WalletLedgerIntegrationService;
import com.elvo.wallet.service.model.WalletFlowResult;

@Component
public class WalletSagaOrchestrator {

    private static final Logger AUDIT_LOG = LoggerFactory.getLogger("audit.wallet.saga");

    private final WalletLedgerIntegrationService ledgerIntegrationService;
    private final SentryExceptionReporter sentryExceptionReporter;
    private final WalletMetricsRecorder metricsRecorder;

    public WalletSagaOrchestrator(WalletLedgerIntegrationService ledgerIntegrationService) {
        this(ledgerIntegrationService, null, null);
    }

    @Autowired
    public WalletSagaOrchestrator(WalletLedgerIntegrationService ledgerIntegrationService,
                                  @Nullable SentryExceptionReporter sentryExceptionReporter,
                                  @Nullable WalletMetricsRecorder metricsRecorder) {
        this.ledgerIntegrationService = ledgerIntegrationService;
        this.sentryExceptionReporter = sentryExceptionReporter;
        this.metricsRecorder = metricsRecorder;
    }

    public WalletFlowResult compensate(String flow,
                                       UUID walletId,
                                       BigDecimal amount,
                                       String reference,
                                       RuntimeException exception) {
                        if (metricsRecorder != null) {
                            metricsRecorder.recordSagaCompensation(flow);
                        }
        ledgerIntegrationService.reconcileEntry(flow, walletId, amount, reference);
        AUDIT_LOG.error("wallet_saga_compensation flow={} walletId={} amount={} reference={} reason={}",
                flow,
                walletId,
                amount,
                reference,
                exception.getMessage());
                        if (sentryExceptionReporter != null) {
                            sentryExceptionReporter.captureCriticalException(
                                exception,
                                null,
                                java.util.Map.of(
                                    "flow", flow,
                                    "walletId", String.valueOf(walletId),
                                    "reference", String.valueOf(reference),
                                    "amount", String.valueOf(amount)));
                        }
        return WalletFlowResult.failure("Saga compensation executed for " + flow, walletId, "wallet." + flow + ".failed");
    }

    public void begin(String flow, UUID walletId, BigDecimal amount, String reference) {
        AUDIT_LOG.info("wallet_saga_started flow={} walletId={} amount={} reference={}", flow, walletId, amount, reference);
    }

    public void complete(String flow, UUID walletId, BigDecimal amount, String reference) {
        AUDIT_LOG.info("wallet_saga_completed flow={} walletId={} amount={} reference={}", flow, walletId, amount, reference);
    }
}
