package com.elvo.wallet.service.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.elvo.wallet.monitoring.SecurityAlertStreamingService;
import com.elvo.wallet.service.impl.WalletLedgerIntegrationService;
import com.elvo.wallet.service.model.WalletFlowResult;

@ExtendWith(MockitoExtension.class)
class WalletSagaOrchestratorTest {

    @Mock
    private WalletLedgerIntegrationService ledgerIntegrationService;

    @Mock
    private SecurityAlertStreamingService securityAlertStreamingService;

    @Test
    void compensateShouldCallLedgerReconciliation() {
        WalletSagaOrchestrator orchestrator = new WalletSagaOrchestrator(
            ledgerIntegrationService,
            null,
            null,
            securityAlertStreamingService,
            3,
            1);

        UUID walletId = UUID.randomUUID();
        WalletFlowResult result = orchestrator.compensate("deposit", walletId, new BigDecimal("10.00"), "ref-1", new IllegalStateException("boom"));

        verify(ledgerIntegrationService).reconcileEntry(eq("deposit"), eq(walletId), eq(new BigDecimal("10.00")), eq("ref-1"));
        assertThat(result.success()).isFalse();
        assertThat(result.eventType()).isEqualTo("wallet.deposit.failed");
    }

    @Test
    void compensateShouldEscalateAfterRetryExhaustion() {
        doThrow(new IllegalStateException("down"))
                .when(ledgerIntegrationService)
                .reconcileEntry(any(), any(), any(), any());

        WalletSagaOrchestrator orchestrator = new WalletSagaOrchestrator(
                ledgerIntegrationService,
                null,
                null,
                securityAlertStreamingService,
                2,
                1);

        UUID walletId = UUID.randomUUID();
        WalletFlowResult result = orchestrator.compensate("withdrawal", walletId, new BigDecimal("7.00"), "ref-2", new IllegalStateException("boom"));

        verify(ledgerIntegrationService, times(2))
                .reconcileEntry(eq("withdrawal"), eq(walletId), eq(new BigDecimal("7.00")), eq("ref-2"));
        verify(securityAlertStreamingService).stream(eq("wallet.saga.compensation.escalated"), eq("HIGH"), eq(walletId), any());
        assertThat(result.message()).contains("escalated");
    }
}
