package com.elvo.wallet.service.orchestration;

import java.math.BigDecimal;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.elvo.wallet.service.impl.WalletLedgerIntegrationService;
import com.elvo.wallet.service.model.WalletFlowResult;

@Component
public class WalletSagaOrchestrator {

    private static final Logger AUDIT_LOG = LoggerFactory.getLogger("audit.wallet.saga");

    private final WalletLedgerIntegrationService ledgerIntegrationService;

    public WalletSagaOrchestrator(WalletLedgerIntegrationService ledgerIntegrationService) {
        this.ledgerIntegrationService = ledgerIntegrationService;
    }

    public WalletFlowResult compensate(String flow,
                                       UUID walletId,
                                       BigDecimal amount,
                                       String reference,
                                       RuntimeException exception) {
        ledgerIntegrationService.reconcileEntry(flow, walletId, amount, reference);
        AUDIT_LOG.error("wallet_saga_compensation flow={} walletId={} amount={} reference={} reason={}",
                flow,
                walletId,
                amount,
                reference,
                exception.getMessage());
        return WalletFlowResult.failure("Saga compensation executed for " + flow, walletId, "wallet." + flow + ".failed");
    }

    public void begin(String flow, UUID walletId, BigDecimal amount, String reference) {
        AUDIT_LOG.info("wallet_saga_started flow={} walletId={} amount={} reference={}", flow, walletId, amount, reference);
    }

    public void complete(String flow, UUID walletId, BigDecimal amount, String reference) {
        AUDIT_LOG.info("wallet_saga_completed flow={} walletId={} amount={} reference={}", flow, walletId, amount, reference);
    }
}
