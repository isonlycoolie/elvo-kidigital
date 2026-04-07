package com.elvo.wallet.statemachine;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.elvo.wallet.messaging.producer.WalletEventPublisher;
import com.elvo.wallet.service.model.WalletFlowResult;

@ExtendWith(MockitoExtension.class)
class WalletEventSubscriberTest {

    @Mock
    private WalletEventPublisher eventPublisher;

    @Mock
    private WalletStateTransitionHandlers stateTransitionHandlers;

    @Test
    void shouldPublishFailedEventWhenPayloadMissingRequiredFields() {
        WalletEventSubscriber subscriber = new WalletEventSubscriber(eventPublisher, stateTransitionHandlers);

        subscriber.onBillingTransactionRequested(Map.of("payload", Map.of("transactionId", "tx-1")));

        verify(eventPublisher).publish(eq("wallet.transaction.failed"), anyMap());
    }

    @Test
    void shouldPublishReservedEventWhenPayloadIsValid() {
        WalletEventSubscriber subscriber = new WalletEventSubscriber(eventPublisher, stateTransitionHandlers);
        when(stateTransitionHandlers.reserveFunds(
            any(),
            any(),
            any(),
            any(),
            any())).thenReturn(
            WalletFlowResult.success("reserved", UUID.randomUUID(), UUID.randomUUID(), "wallet.transaction.reserved"));

        subscriber.onBillingTransactionRequested(Map.of(
                "correlationId", "corr-2",
                "payload", Map.of(
                        "transactionId", "tx-2",
                "walletId", UUID.randomUUID().toString(),
                "userId", UUID.randomUUID().toString(),
                "idempotencyKey", "idem-2",
                "amount", new BigDecimal("40.00"))));

        verify(eventPublisher).publish(eq("wallet.transaction.reserved"), anyMap());
    }
}
