package com.elvo.wallet.service.impl;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.elvo.wallet.entity.Transaction;
import com.elvo.wallet.repository.TransactionRepository;
import com.elvo.wallet.service.TransactionLifecycleService;

@Service
public class DefaultTransactionLifecycleService implements TransactionLifecycleService {

    private static final Logger AUDIT_LOG = LoggerFactory.getLogger("audit.wallet.transaction.lifecycle");
    private static final EnumSet<Transaction.TransactionStatus> TERMINAL_STATUSES = EnumSet.of(
            Transaction.TransactionStatus.COMPLETED,
            Transaction.TransactionStatus.FAILED,
            Transaction.TransactionStatus.REVERSED,
            Transaction.TransactionStatus.EXPIRED,
            Transaction.TransactionStatus.CANCELLED);

    private final TransactionRepository transactionRepository;
    private static final long DEFAULT_EXPIRY_MINUTES = 30;

    public DefaultTransactionLifecycleService(TransactionRepository transactionRepository,
                                              com.elvo.wallet.repository.TransactionStatusHistoryRepository transactionStatusHistoryRepository,
                                              long expiryMinutes) {
        this.transactionRepository = transactionRepository;
    }

    @Override
    @Transactional
    public Transaction initialize(Transaction transaction, String reason, String correlationId, String externalReference) {
        if (transaction == null) {
            throw new IllegalArgumentException("Transaction is required");
        }

        Instant now = Instant.now();
        transaction.setPreviousStatus(null);
        transaction.setStatus(Transaction.TransactionStatus.INITIATED);
        transaction.setStatusReason(normalizeReason(reason, "Transaction initiated"));
        transaction.setStatusUpdatedAt(now);
        transaction.setCorrelationId(resolveCorrelationId(correlationId));
        transaction.setExternalReference(externalReference);
        if (transaction.getRetryCount() == null) {
            transaction.setRetryCount(0);
        }
        if (transaction.getExpiresAt() == null) {
            transaction.setExpiresAt(now.plusSeconds(DEFAULT_EXPIRY_MINUTES * 60));
        }

        Transaction persisted = transactionRepository.save(transaction);
        AUDIT_LOG.info("transaction_initialized transactionId={} status={} expiresAt={} correlationId={}",
                persisted.getId(), persisted.getStatus(), persisted.getExpiresAt(), persisted.getCorrelationId());
        return persisted;
    }

    @Override
    @Transactional
    public Transaction transition(Transaction transaction,
                                  Transaction.TransactionStatus nextStatus,
                                  String reason,
                                  String correlationId,
                                  String failureCode,
                                  String failureMessage) {
        if (transaction == null || nextStatus == null) {
            throw new IllegalArgumentException("Transaction and next status are required");
        }

        Transaction.TransactionStatus currentStatus = transaction.getStatus();
        if (currentStatus == nextStatus) {
            return transaction;
        }

        if (!canTransition(currentStatus, nextStatus)) {
            throw new IllegalStateException("Invalid transaction transition from " + currentStatus + " to " + nextStatus);
        }

        Instant now = Instant.now();
        transaction.setPreviousStatus(currentStatus);
        transaction.setStatus(nextStatus);
        transaction.setStatusReason(normalizeReason(reason, nextStatus.name()));
        transaction.setStatusUpdatedAt(now);
        transaction.setCorrelationId(resolveCorrelationId(correlationId));
        transaction.setFailureCode(failureCode);
        transaction.setFailureMessage(failureMessage);
        if (nextStatus == Transaction.TransactionStatus.RETRYING) {
            transaction.setRetryCount(transaction.getRetryCount() == null ? 1 : transaction.getRetryCount() + 1);
        }

        Transaction persisted = transactionRepository.save(transaction);
        AUDIT_LOG.info("transaction_transitioned transactionId={} fromStatus={} toStatus={} reason={} correlationId={}",
                persisted.getId(), currentStatus, nextStatus, persisted.getStatusReason(), persisted.getCorrelationId());
        return persisted;
    }

    @Override
    public boolean canTransition(Transaction.TransactionStatus fromStatus, Transaction.TransactionStatus toStatus) {
        if (fromStatus == null || toStatus == null) {
            return false;
        }

        if (TERMINAL_STATUSES.contains(fromStatus)) {
            return fromStatus == toStatus;
        }

        return switch (fromStatus) {
            case INITIATED -> EnumSet.of(
                    Transaction.TransactionStatus.PENDING,
                    Transaction.TransactionStatus.PROCESSING,
                    Transaction.TransactionStatus.RESERVED,
                    Transaction.TransactionStatus.AWAITING_CONFIRMATION,
                    Transaction.TransactionStatus.COMPLETED,
                    Transaction.TransactionStatus.FAILED,
                    Transaction.TransactionStatus.REVERSED,
                    Transaction.TransactionStatus.EXPIRED,
                    Transaction.TransactionStatus.CANCELLED,
                    Transaction.TransactionStatus.RETRYING).contains(toStatus);
            case PENDING -> EnumSet.of(
                    Transaction.TransactionStatus.PROCESSING,
                    Transaction.TransactionStatus.RESERVED,
                    Transaction.TransactionStatus.AWAITING_CONFIRMATION,
                    Transaction.TransactionStatus.FAILED,
                    Transaction.TransactionStatus.REVERSED,
                    Transaction.TransactionStatus.EXPIRED,
                    Transaction.TransactionStatus.CANCELLED,
                    Transaction.TransactionStatus.RETRYING).contains(toStatus);
            case PROCESSING -> EnumSet.of(
                    Transaction.TransactionStatus.COMPLETED,
                    Transaction.TransactionStatus.FAILED,
                    Transaction.TransactionStatus.REVERSED,
                    Transaction.TransactionStatus.EXPIRED,
                    Transaction.TransactionStatus.RETRYING).contains(toStatus);
            case RESERVED -> EnumSet.of(
                    Transaction.TransactionStatus.RELEASED,
                    Transaction.TransactionStatus.PROCESSING,
                    Transaction.TransactionStatus.REVERSED,
                    Transaction.TransactionStatus.EXPIRED,
                    Transaction.TransactionStatus.FAILED,
                    Transaction.TransactionStatus.CANCELLED).contains(toStatus);
            case AWAITING_CONFIRMATION -> EnumSet.of(
                    Transaction.TransactionStatus.PROCESSING,
                    Transaction.TransactionStatus.FAILED,
                    Transaction.TransactionStatus.EXPIRED,
                    Transaction.TransactionStatus.CANCELLED,
                    Transaction.TransactionStatus.RETRYING).contains(toStatus);
            case RELEASED -> EnumSet.of(
                    Transaction.TransactionStatus.COMPLETED,
                    Transaction.TransactionStatus.REVERSED).contains(toStatus);
            case RETRYING -> EnumSet.of(
                    Transaction.TransactionStatus.PENDING,
                    Transaction.TransactionStatus.PROCESSING,
                    Transaction.TransactionStatus.FAILED,
                    Transaction.TransactionStatus.CANCELLED).contains(toStatus);
            case FAILED, COMPLETED, REVERSED, EXPIRED, CANCELLED -> false;
        };
    }

    @Override
    public List<Transaction.TransactionStatus> activeStatuses() {
        return List.of(
                Transaction.TransactionStatus.INITIATED,
                Transaction.TransactionStatus.PENDING,
                Transaction.TransactionStatus.PROCESSING,
                Transaction.TransactionStatus.RESERVED,
                Transaction.TransactionStatus.AWAITING_CONFIRMATION,
                Transaction.TransactionStatus.RETRYING,
                Transaction.TransactionStatus.RELEASED);
    }

    private String normalizeReason(String reason, String fallback) {
        return reason == null || reason.isBlank() ? fallback : reason;
    }

    private String resolveCorrelationId(String correlationId) {
        if (correlationId != null && !correlationId.isBlank()) {
            return correlationId;
        }

        String mdcCorrelationId = MDC.get("correlationId");
        if (mdcCorrelationId != null && !mdcCorrelationId.isBlank()) {
            return mdcCorrelationId;
        }

        return null;
    }
}