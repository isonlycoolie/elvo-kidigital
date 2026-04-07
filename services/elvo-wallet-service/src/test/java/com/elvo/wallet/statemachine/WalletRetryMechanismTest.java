package com.elvo.wallet.statemachine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.elvo.wallet.service.model.WalletFlowResult;

@ExtendWith(MockitoExtension.class)
class WalletRetryMechanismTest {

    @Mock
    private WalletStateTransitionHandlers handlers;

    @Test
    void shouldRetryAndSucceedWithinConfiguredAttempts() {
        WalletRetryMechanism retryMechanism = new WalletRetryMechanism(handlers, 3, 15);
        UUID walletId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(handlers.reserveFunds(any(), any(), any(), anyString(), anyString()))
                .thenReturn(WalletFlowResult.failure("temporary", walletId, "wallet.transaction.failed"))
                .thenReturn(WalletFlowResult.success("reserved", walletId, UUID.randomUUID(), "wallet.transaction.reserved"));

        WalletFlowResult result = retryMechanism.reserveWithRetry(
                walletId,
                userId,
                new BigDecimal("12.00"),
                "idem-1",
                "ref-1");

        assertThat(result.success()).isTrue();
        verify(handlers, times(2)).reserveFunds(any(), any(), any(), anyString(), anyString());
    }

    @Test
    void shouldReturnFailureWhenCircuitIsOpen() {
        WalletRetryMechanism retryMechanism = new WalletRetryMechanism(handlers, 1, 60);
        UUID reservationId = UUID.randomUUID();

        when(handlers.commitFunds(any(), anyString()))
                .thenReturn(WalletFlowResult.failure("failed", reservationId, "wallet.transaction.failed"));

        WalletFlowResult first = retryMechanism.commitWithRetry(reservationId, "idem-2");
        WalletFlowResult second = retryMechanism.commitWithRetry(reservationId, "idem-2");

        assertThat(first.success()).isFalse();
        assertThat(second.success()).isFalse();
        assertThat(second.message()).contains("Circuit open");
        verify(handlers, times(1)).commitFunds(any(), anyString());
    }
}
