package com.elvo.wallet.service.impl;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.elvo.wallet.entity.Transaction;
import com.elvo.wallet.entity.TransactionStatusHistory;
import com.elvo.wallet.entity.Wallet;
import com.elvo.wallet.repository.TransactionRepository;
import com.elvo.wallet.repository.TransactionStatusHistoryRepository;

@ExtendWith(MockitoExtension.class)
class TransactionLifecycleServiceTests {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private TransactionStatusHistoryRepository historyRepository;

    private DefaultTransactionLifecycleService service;

    @BeforeEach
    void setUp() {
        service = new DefaultTransactionLifecycleService(transactionRepository, historyRepository, 30);
    }

    @Test
    void initializeShouldPersistInitiatedStateAndHistory() {
        Transaction transaction = newTransaction(Transaction.TransactionStatus.PENDING);
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(historyRepository.save(any(TransactionStatusHistory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Transaction persisted = service.initialize(transaction, "Deposit initiated", "corr-1", "ref-1");

        assertThat(persisted.getStatus()).isEqualTo(Transaction.TransactionStatus.INITIATED);
        assertThat(persisted.getPreviousStatus()).isNull();
        assertThat(persisted.getExpiresAt()).isNotNull();
        assertThat(persisted.getStatusReason()).isEqualTo("Deposit initiated");

        ArgumentCaptor<TransactionStatusHistory> historyCaptor = ArgumentCaptor.forClass(TransactionStatusHistory.class);
        verify(historyRepository).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getFromStatus()).isNull();
        assertThat(historyCaptor.getValue().getToStatus()).isEqualTo(Transaction.TransactionStatus.INITIATED);
        assertThat(historyCaptor.getValue().getCorrelationId()).isEqualTo("corr-1");
    }

    @Test
    void transitionShouldRejectInvalidTransitions() {
        Transaction transaction = newTransaction(Transaction.TransactionStatus.COMPLETED);

        assertThatThrownBy(() -> service.transition(
                transaction,
                Transaction.TransactionStatus.PROCESSING,
                "Reprocess", 
                "corr-2",
                null,
                null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invalid transaction transition from COMPLETED to PROCESSING");
    }

    @Test
    void shouldRejectInvalidStateMatrixTransitions() {
        assertThat(service.canTransition(Transaction.TransactionStatus.PENDING, Transaction.TransactionStatus.COMPLETED)).isFalse();
        assertThat(service.canTransition(Transaction.TransactionStatus.AWAITING_CONFIRMATION, Transaction.TransactionStatus.RESERVED)).isFalse();
        assertThat(service.canTransition(Transaction.TransactionStatus.RETRYING, Transaction.TransactionStatus.REVERSED)).isFalse();
        assertThat(service.canTransition(Transaction.TransactionStatus.RELEASED, Transaction.TransactionStatus.PROCESSING)).isFalse();
        assertThat(service.canTransition(Transaction.TransactionStatus.FAILED, Transaction.TransactionStatus.COMPLETED)).isFalse();
    }

    @Test
    void terminalStatesShouldRejectFurtherTransitions() {
        for (Transaction.TransactionStatus terminal : List.of(
                Transaction.TransactionStatus.COMPLETED,
                Transaction.TransactionStatus.FAILED,
                Transaction.TransactionStatus.REVERSED,
                Transaction.TransactionStatus.EXPIRED,
                Transaction.TransactionStatus.CANCELLED)) {
            Transaction transaction = newTransaction(terminal);
            assertThatThrownBy(() -> service.transition(
                    transaction,
                    Transaction.TransactionStatus.PROCESSING,
                    "invalid retry",
                    "corr-terminal",
                    null,
                    null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Invalid transaction transition from " + terminal + " to PROCESSING");
        }
    }

    @Test
    void transitionShouldPersistValidStateChange() {
        Transaction transaction = newTransaction(Transaction.TransactionStatus.INITIATED);
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(historyRepository.save(any(TransactionStatusHistory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Transaction transitioned = service.transition(
                transaction,
                Transaction.TransactionStatus.PROCESSING,
                "Posting",
                "corr-3",
                null,
                null);

        assertThat(transitioned.getPreviousStatus()).isEqualTo(Transaction.TransactionStatus.INITIATED);
        assertThat(transitioned.getStatus()).isEqualTo(Transaction.TransactionStatus.PROCESSING);
        assertThat(transitioned.getStatusReason()).isEqualTo("Posting");
        verify(historyRepository).save(any(TransactionStatusHistory.class));
    }

    @Test
    void shouldAllowValidTransitionsAcrossLifecycleFlows() {
        assertThat(service.canTransition(Transaction.TransactionStatus.INITIATED, Transaction.TransactionStatus.PENDING)).isTrue();
        assertThat(service.canTransition(Transaction.TransactionStatus.INITIATED, Transaction.TransactionStatus.PROCESSING)).isTrue();
        assertThat(service.canTransition(Transaction.TransactionStatus.INITIATED, Transaction.TransactionStatus.AWAITING_CONFIRMATION)).isTrue();
        assertThat(service.canTransition(Transaction.TransactionStatus.INITIATED, Transaction.TransactionStatus.RESERVED)).isTrue();

        assertThat(service.canTransition(Transaction.TransactionStatus.PENDING, Transaction.TransactionStatus.PROCESSING)).isTrue();
        assertThat(service.canTransition(Transaction.TransactionStatus.PENDING, Transaction.TransactionStatus.AWAITING_CONFIRMATION)).isTrue();

        assertThat(service.canTransition(Transaction.TransactionStatus.AWAITING_CONFIRMATION, Transaction.TransactionStatus.RETRYING)).isTrue();
        assertThat(service.canTransition(Transaction.TransactionStatus.AWAITING_CONFIRMATION, Transaction.TransactionStatus.PROCESSING)).isTrue();

        assertThat(service.canTransition(Transaction.TransactionStatus.RETRYING, Transaction.TransactionStatus.PENDING)).isTrue();
        assertThat(service.canTransition(Transaction.TransactionStatus.RETRYING, Transaction.TransactionStatus.PROCESSING)).isTrue();

        assertThat(service.canTransition(Transaction.TransactionStatus.RESERVED, Transaction.TransactionStatus.RELEASED)).isTrue();
        assertThat(service.canTransition(Transaction.TransactionStatus.RESERVED, Transaction.TransactionStatus.REVERSED)).isTrue();
        assertThat(service.canTransition(Transaction.TransactionStatus.RELEASED, Transaction.TransactionStatus.COMPLETED)).isTrue();

        assertThat(service.canTransition(Transaction.TransactionStatus.PROCESSING, Transaction.TransactionStatus.COMPLETED)).isTrue();
        assertThat(service.canTransition(Transaction.TransactionStatus.PROCESSING, Transaction.TransactionStatus.FAILED)).isTrue();
        assertThat(service.canTransition(Transaction.TransactionStatus.PROCESSING, Transaction.TransactionStatus.REVERSED)).isTrue();
    }

    @Test
    void expireOverdueTransactionsShouldExpireEligibleTransactions() {
        Transaction transaction = newTransaction(Transaction.TransactionStatus.PROCESSING);
        transaction.setExpiresAt(Instant.now().minusSeconds(60));
        when(transactionRepository.findByStatusInAndExpiresAtBefore(any(), any())).thenReturn(List.of(transaction));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(historyRepository.save(any(TransactionStatusHistory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        int expired = service.expireOverdueTransactions();

        assertThat(expired).isEqualTo(1);
        assertThat(transaction.getStatus()).isEqualTo(Transaction.TransactionStatus.EXPIRED);
        verify(historyRepository).save(any(TransactionStatusHistory.class));
    }

    @Test
    void expireShouldNotChangeTransactionBeforeTimeout() {
        Transaction transaction = newTransaction(Transaction.TransactionStatus.AWAITING_CONFIRMATION);
        transaction.setExpiresAt(Instant.now().plusSeconds(300));

        Transaction result = service.expire(transaction, "not expired yet", "corr-exp-1");

        assertThat(result.getStatus()).isEqualTo(Transaction.TransactionStatus.AWAITING_CONFIRMATION);
    }

    @Test
    void transitionShouldRejectAnyStateAfterExpired() {
        Transaction transaction = newTransaction(Transaction.TransactionStatus.EXPIRED);

        assertThatThrownBy(() -> service.transition(
                transaction,
                Transaction.TransactionStatus.PROCESSING,
                "continue after expiry",
                "corr-exp-2",
                null,
                null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invalid transaction transition from EXPIRED to PROCESSING");
    }

        @Test
        void mobileDepositStatesShouldSupportAwaitingAndRetryTransitions() {
        assertThat(service.canTransition(
            Transaction.TransactionStatus.AWAITING_CONFIRMATION,
            Transaction.TransactionStatus.RETRYING)).isTrue();
        assertThat(service.canTransition(
            Transaction.TransactionStatus.RETRYING,
            Transaction.TransactionStatus.PROCESSING)).isTrue();
        }

        @Test
        void transferStatesShouldAllowPendingToProcessing() {
        assertThat(service.canTransition(
            Transaction.TransactionStatus.INITIATED,
            Transaction.TransactionStatus.PENDING)).isTrue();
        assertThat(service.canTransition(
            Transaction.TransactionStatus.PENDING,
            Transaction.TransactionStatus.PROCESSING)).isTrue();
        }

            @Test
            void retryAndReversalPathsShouldNotConflict() {
            assertThat(service.canTransition(
                Transaction.TransactionStatus.RETRYING,
                Transaction.TransactionStatus.REVERSED)).isFalse();
            assertThat(service.canTransition(
                Transaction.TransactionStatus.REVERSED,
                Transaction.TransactionStatus.RETRYING)).isFalse();
            }

            @Test
            void transitionShouldRejectRetryAfterReversal() {
            Transaction transaction = newTransaction(Transaction.TransactionStatus.REVERSED);

            assertThatThrownBy(() -> service.transition(
                transaction,
                Transaction.TransactionStatus.RETRYING,
                "retry after reversal",
                "corr-r1",
                null,
                null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invalid transaction transition from REVERSED to RETRYING");
            }

    @Test
    void duplicateTransitionRequestShouldNotCorruptStatusHistory() {
        Transaction transaction = newTransaction(Transaction.TransactionStatus.PROCESSING);

        Transaction sameStatus = service.transition(
                transaction,
                Transaction.TransactionStatus.PROCESSING,
                "duplicate update",
                "corr-con-1",
                null,
                null);

        assertThat(sameStatus.getStatus()).isEqualTo(Transaction.TransactionStatus.PROCESSING);
        verify(transactionRepository, never()).save(any(Transaction.class));
        verify(historyRepository, never()).save(any(TransactionStatusHistory.class));
    }

    @Test
    void staleConcurrentUpdateShouldBeRejectedAfterCompletion() {
        Transaction transaction = newTransaction(Transaction.TransactionStatus.COMPLETED);

        assertThatThrownBy(() -> service.transition(
                transaction,
                Transaction.TransactionStatus.FAILED,
                "stale concurrent update",
                "corr-con-2",
                null,
                null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invalid transaction transition from COMPLETED to FAILED");
    }

    private Transaction newTransaction(Transaction.TransactionStatus status) {
        Transaction transaction = new Transaction();
        transaction.setStatus(status);
        transaction.setAmount(new BigDecimal("10.00"));
        transaction.setReference("ref");
        transaction.setWallet(new Wallet());
        try {
            java.lang.reflect.Field idField = Transaction.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(transaction, UUID.randomUUID());
        } catch (ReflectiveOperationException ignored) {
        }
        return transaction;
    }
}