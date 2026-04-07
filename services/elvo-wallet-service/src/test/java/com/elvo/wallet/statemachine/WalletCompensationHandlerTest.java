package com.elvo.wallet.statemachine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.elvo.wallet.messaging.producer.WalletEventPublisher;
import com.elvo.wallet.service.model.WalletFlowResult;

@ExtendWith(MockitoExtension.class)
class WalletCompensationHandlerTest {

    @Mock
    private WalletRetryMechanism retryMechanism;

    @Mock
    private WalletEventPublisher eventPublisher;

    @Test
    void shouldPublishReversedEventOnSuccessfulRollback() {
        WalletCompensationHandler handler = new WalletCompensationHandler(retryMechanism, eventPublisher);
        UUID reservationId = UUID.randomUUID();
        when(retryMechanism.rollbackWithRetry(eq(reservationId), eq("idem-1"))).thenReturn(
                WalletFlowResult.success("rolled-back", reservationId, reservationId, "wallet.transaction.reversed"));

        WalletFlowResult result = handler.compensateFailedBilling(reservationId, "idem-1", "billing_failed", "corr-1");

        assertThat(result.success()).isTrue();
        verify(eventPublisher).publish(eq("wallet.transaction.reversed"), anyMap());
    }

    @Test
    void shouldPublishFailedEventOnRollbackFailure() {
        WalletCompensationHandler handler = new WalletCompensationHandler(retryMechanism, eventPublisher);
        UUID reservationId = UUID.randomUUID();
        when(retryMechanism.rollbackWithRetry(eq(reservationId), eq("idem-2"))).thenReturn(
                WalletFlowResult.failure("rollback_failed", reservationId, "wallet.transaction.failed"));

        WalletFlowResult result = handler.compensateFailedBilling(reservationId, "idem-2", "billing_failed", "corr-2");

        assertThat(result.success()).isFalse();
        verify(eventPublisher).publish(eq("wallet.transaction.failed"), anyMap());
    }
}
