package com.elvo.wallet.statemachine;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.elvo.wallet.messaging.producer.WalletEventPublisher;

@ExtendWith(MockitoExtension.class)
class WalletEventSubscriberTest {

    @Mock
    private WalletEventPublisher eventPublisher;

    @Test
    void shouldPublishFailedEventWhenPayloadMissingRequiredFields() {
        WalletEventSubscriber subscriber = new WalletEventSubscriber(eventPublisher);

        subscriber.onBillingTransactionRequested(Map.of("payload", Map.of("transactionId", "tx-1")));

        verify(eventPublisher).publish(eq("wallet.transaction.failed"), anyMap());
    }

    @Test
    void shouldPublishReservedEventWhenPayloadIsValid() {
        WalletEventSubscriber subscriber = new WalletEventSubscriber(eventPublisher);

        subscriber.onBillingTransactionRequested(Map.of(
                "correlationId", "corr-2",
                "payload", Map.of(
                        "transactionId", "tx-2",
                        "walletId", "wallet-1",
                        "amount", new BigDecimal("40.00"))));

        verify(eventPublisher).publish(eq("wallet.transaction.reserved"), anyMap());
    }
}
