package com.elvo.wallet.statemachine;

import org.springframework.stereotype.Component;

import com.elvo.wallet.entity.Transaction;
import com.elvo.wallet.service.TransactionLifecycleService;

@Component
public class WalletTransactionStateMachine {

    private final TransactionLifecycleService transactionLifecycleService;

    public WalletTransactionStateMachine(TransactionLifecycleService transactionLifecycleService) {
        this.transactionLifecycleService = transactionLifecycleService;
    }

    public Transaction initiate(Transaction transaction, String reason, String correlationId, String externalReference) {
        return transactionLifecycleService.initialize(transaction, reason, correlationId, externalReference);
    }

    public Transaction moveToPending(Transaction transaction, String reason, String correlationId) {
        return transition(transaction, Transaction.TransactionStatus.PENDING, reason, correlationId, null, null);
    }

    public Transaction moveToProcessing(Transaction transaction, String reason, String correlationId) {
        return transition(transaction, Transaction.TransactionStatus.PROCESSING, reason, correlationId, null, null);
    }

    public Transaction moveToSuccess(Transaction transaction, String reason, String correlationId) {
        return transition(transaction, Transaction.TransactionStatus.COMPLETED, reason, correlationId, null, null);
    }

    public Transaction moveToFailed(Transaction transaction,
                                    String reason,
                                    String correlationId,
                                    String failureCode,
                                    String failureMessage) {
        return transition(transaction, Transaction.TransactionStatus.FAILED, reason, correlationId, failureCode, failureMessage);
    }

    public Transaction moveToReversed(Transaction transaction,
                                      String reason,
                                      String correlationId,
                                      String failureCode,
                                      String failureMessage) {
        return transition(transaction, Transaction.TransactionStatus.REVERSED, reason, correlationId, failureCode, failureMessage);
    }

    private Transaction transition(Transaction transaction,
                                   Transaction.TransactionStatus nextStatus,
                                   String reason,
                                   String correlationId,
                                   String failureCode,
                                   String failureMessage) {
        return transactionLifecycleService.transition(
                transaction,
                nextStatus,
                reason,
                correlationId,
                failureCode,
                failureMessage);
    }
}
