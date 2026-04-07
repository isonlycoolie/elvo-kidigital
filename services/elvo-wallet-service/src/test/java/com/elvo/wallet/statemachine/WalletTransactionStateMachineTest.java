package com.elvo.wallet.statemachine;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.elvo.wallet.entity.Transaction;
import com.elvo.wallet.service.TransactionLifecycleService;

@ExtendWith(MockitoExtension.class)
class WalletTransactionStateMachineTest {

    @Mock
    private TransactionLifecycleService lifecycleService;

    @Test
    void shouldTransitionToProcessing() {
        WalletTransactionStateMachine stateMachine = new WalletTransactionStateMachine(lifecycleService);
        Transaction tx = new Transaction();
        when(lifecycleService.transition(any(), any(), any(), any(), any(), any())).thenReturn(tx);

        stateMachine.moveToProcessing(tx, "processing", "corr-1");

        verify(lifecycleService).transition(
                eq(tx),
                eq(Transaction.TransactionStatus.PROCESSING),
                eq("processing"),
                eq("corr-1"),
                eq(null),
                eq(null));
    }
}
