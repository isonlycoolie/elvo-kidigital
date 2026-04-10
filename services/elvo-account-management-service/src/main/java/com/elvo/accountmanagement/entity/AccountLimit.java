package com.elvo.accountmanagement.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(
        name = "account_limits",
        indexes = @Index(name = "idx_account_limits_account_id", columnList = "account_id")
)
public class AccountLimit {

    @Id
    @GeneratedValue(strategy = jakarta.persistence.GenerationType.UUID)
    @Column(name = "limit_id", nullable = false, updatable = false)
    private UUID limitId;

    @Column(name = "account_id", nullable = false, unique = true)
    private UUID accountId;

    @Column(name = "daily_transfer_limit", precision = 19, scale = 2)
    private BigDecimal dailyTransferLimit = new BigDecimal("1000.00");

    @Column(name = "monthly_transfer_limit", precision = 19, scale = 2)
    private BigDecimal monthlyTransferLimit = new BigDecimal("5000.00");

    @Column(name = "withdrawal_limit", precision = 19, scale = 2)
    private BigDecimal withdrawalLimit = new BigDecimal("500.00");

    @Column(name = "deposit_limit", precision = 19, scale = 2)
    private BigDecimal depositLimit = new BigDecimal("5000.00");

    @Column(name = "bill_payment_limit", precision = 19, scale = 2)
    private BigDecimal billPaymentLimit = new BigDecimal("5000.00");

    @Column(name = "max_single_transaction", precision = 19, scale = 2)
    private BigDecimal maxSingleTransaction = new BigDecimal("1000.00");

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public UUID getLimitId() {
        return limitId;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public void setAccountId(UUID accountId) {
        this.accountId = accountId;
    }

    public BigDecimal getDailyTransferLimit() {
        return dailyTransferLimit;
    }

    public void setDailyTransferLimit(BigDecimal dailyTransferLimit) {
        this.dailyTransferLimit = dailyTransferLimit;
    }

    public BigDecimal getMonthlyTransferLimit() {
        return monthlyTransferLimit;
    }

    public void setMonthlyTransferLimit(BigDecimal monthlyTransferLimit) {
        this.monthlyTransferLimit = monthlyTransferLimit;
    }

    public BigDecimal getWithdrawalLimit() {
        return withdrawalLimit;
    }

    public void setWithdrawalLimit(BigDecimal withdrawalLimit) {
        this.withdrawalLimit = withdrawalLimit;
    }

    public BigDecimal getDepositLimit() {
        return depositLimit;
    }

    public void setDepositLimit(BigDecimal depositLimit) {
        this.depositLimit = depositLimit;
    }

    public BigDecimal getBillPaymentLimit() {
        return billPaymentLimit;
    }

    public void setBillPaymentLimit(BigDecimal billPaymentLimit) {
        this.billPaymentLimit = billPaymentLimit;
    }

    public BigDecimal getMaxSingleTransaction() {
        return maxSingleTransaction;
    }

    public void setMaxSingleTransaction(BigDecimal maxSingleTransaction) {
        this.maxSingleTransaction = maxSingleTransaction;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}
