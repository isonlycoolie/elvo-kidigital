package com.elvo.wallet.service.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.elvo.wallet.service.impl.WalletLedgerIntegrationService;
import com.elvo.wallet.service.model.WalletFlowResult;

@ExtendWith(MockitoExtension.class)
class WalletSagaOrchestratorTest {

    @Mock
    private WalletLedgerIntegrationService ledgerIntegrationService;

    @Test
    void compensateShouldCallLedgerReconciliation() {
        WalletSagaOrchestrator orchestrator = new WalletSagaOrchestrator(ledgerIntegrationService);

        UUID walletId = UUID.randomUUID();
        WalletFlowResult result = orchestrator.compensate("deposit", walletId, new BigDecimal("10.00"), "ref-1", new IllegalStateException("boom"));

        verify(ledgerIntegrationService).reconcileEntry(eq("deposit"), eq(walletId), eq(new BigDecimal("10.00")), eq("ref-1"));
        assertThat(result.success()).isFalse();
        assertThat(result.eventType()).isEqualTo("wallet.deposit.failed");
    }
}
