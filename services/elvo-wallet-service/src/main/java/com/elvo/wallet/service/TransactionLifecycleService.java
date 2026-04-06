package com.elvo.wallet.service;

import java.util.List;

import com.elvo.wallet.entity.Transaction;

public interface TransactionLifecycleService {

    Transaction initialize(Transaction transaction, String reason, String correlationId, String externalReference);

    Transaction transition(Transaction transaction,
                           Transaction.TransactionStatus nextStatus,
                           String reason,
                           String correlationId,
                           String failureCode,
                           String failureMessage);

    Transaction expire(Transaction transaction, String reason, String correlationId);

    int expireOverdueTransactions();

    boolean canTransition(Transaction.TransactionStatus fromStatus, Transaction.TransactionStatus toStatus);

    List<Transaction.TransactionStatus> activeStatuses();
}