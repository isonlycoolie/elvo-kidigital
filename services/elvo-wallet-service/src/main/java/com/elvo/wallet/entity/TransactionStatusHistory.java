package com.elvo.wallet.entity;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "transaction_status_history")
public class TransactionStatusHistory {

    private static final Logger AUDIT_LOG = LoggerFactory.getLogger("audit.wallet.entity");

    @Id
    @GeneratedValue(strategy = jakarta.persistence.GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    private Transaction transaction;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 32)
    private Transaction.TransactionStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 32)
    private Transaction.TransactionStatus toStatus;

    @Column(name = "reason", length = 512)
    private String reason;

    @Column(name = "correlation_id", length = 128)
    private String correlationId;

    @Column(name = "external_reference", length = 128)
    private String externalReference;

    @CreationTimestamp
    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    public TransactionStatusHistory() {
    }

    public TransactionStatusHistory(Transaction transaction,
                                    Transaction.TransactionStatus fromStatus,
                                    Transaction.TransactionStatus toStatus,
                                    String reason,
                                    String correlationId,
                                    String externalReference) {
        this.transaction = transaction;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.reason = reason;
        this.correlationId = correlationId;
        this.externalReference = externalReference;
    }

    @jakarta.persistence.PrePersist
    void onCreate() {
        AUDIT_LOG.info("transaction_status_history_created transactionId={} fromStatus={} toStatus={} reason={} correlationId={}",
                transaction != null ? transaction.getId() : null,
                fromStatus,
                toStatus,
                reason,
                correlationId);
    }

    public UUID getId() {
        return id;
    }

    public Transaction getTransaction() {
        return transaction;
    }

    public void setTransaction(Transaction transaction) {
        this.transaction = transaction;
    }

    public Transaction.TransactionStatus getFromStatus() {
        return fromStatus;
    }

    public void setFromStatus(Transaction.TransactionStatus fromStatus) {
        this.fromStatus = fromStatus;
    }

    public Transaction.TransactionStatus getToStatus() {
        return toStatus;
    }

    public void setToStatus(Transaction.TransactionStatus toStatus) {
        this.toStatus = toStatus;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public String getExternalReference() {
        return externalReference;
    }

    public void setExternalReference(String externalReference) {
        this.externalReference = externalReference;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }
}