package com.elvo.accountmanagement.entity;

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

@Entity
@Table(
        name = "account_permissions",
        indexes = @Index(name = "idx_account_permissions_account_id", columnList = "account_id")
)
public class AccountPermission {

    @Id
    @GeneratedValue(strategy = jakarta.persistence.GenerationType.UUID)
    @Column(name = "permission_id", nullable = false, updatable = false)
    private UUID permissionId;

    @Column(name = "account_id", nullable = false, unique = true)
    private UUID accountId;

    @Column(name = "can_receive_money", nullable = false)
    private boolean canReceiveMoney = true;

    @Column(name = "can_send_money", nullable = false)
    private boolean canSendMoney = true;

    @Column(name = "can_withdraw", nullable = false)
    private boolean canWithdraw = true;

    @Column(name = "can_deposit", nullable = false)
    private boolean canDeposit = true;

    @Column(name = "can_use_delegated_access", nullable = false)
    private boolean canUseDelegatedAccess;

    @Column(name = "can_use_agent_withdrawal", nullable = false)
    private boolean canUseAgentWithdrawal;

    @Column(name = "can_perform_bill_payment", nullable = false)
    private boolean canPerformBillPayment = true;

    @Column(name = "can_create_sub_accounts", nullable = false)
    private boolean canCreateSubAccounts;

    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom = Instant.now();

    @Column(name = "effective_to")
    private Instant effectiveTo;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UUID getPermissionId() {
        return permissionId;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public void setAccountId(UUID accountId) {
        this.accountId = accountId;
    }

    public boolean isCanReceiveMoney() {
        return canReceiveMoney;
    }

    public void setCanReceiveMoney(boolean canReceiveMoney) {
        this.canReceiveMoney = canReceiveMoney;
    }

    public boolean isCanSendMoney() {
        return canSendMoney;
    }

    public void setCanSendMoney(boolean canSendMoney) {
        this.canSendMoney = canSendMoney;
    }

    public boolean isCanWithdraw() {
        return canWithdraw;
    }

    public void setCanWithdraw(boolean canWithdraw) {
        this.canWithdraw = canWithdraw;
    }

    public boolean isCanDeposit() {
        return canDeposit;
    }

    public void setCanDeposit(boolean canDeposit) {
        this.canDeposit = canDeposit;
    }

    public boolean isCanUseDelegatedAccess() {
        return canUseDelegatedAccess;
    }

    public void setCanUseDelegatedAccess(boolean canUseDelegatedAccess) {
        this.canUseDelegatedAccess = canUseDelegatedAccess;
    }

    public boolean isCanUseAgentWithdrawal() {
        return canUseAgentWithdrawal;
    }

    public void setCanUseAgentWithdrawal(boolean canUseAgentWithdrawal) {
        this.canUseAgentWithdrawal = canUseAgentWithdrawal;
    }

    public boolean isCanPerformBillPayment() {
        return canPerformBillPayment;
    }

    public void setCanPerformBillPayment(boolean canPerformBillPayment) {
        this.canPerformBillPayment = canPerformBillPayment;
    }

    public boolean isCanCreateSubAccounts() {
        return canCreateSubAccounts;
    }

    public void setCanCreateSubAccounts(boolean canCreateSubAccounts) {
        this.canCreateSubAccounts = canCreateSubAccounts;
    }

    public Instant getEffectiveFrom() {
        return effectiveFrom;
    }

    public void setEffectiveFrom(Instant effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }

    public Instant getEffectiveTo() {
        return effectiveTo;
    }

    public void setEffectiveTo(Instant effectiveTo) {
        this.effectiveTo = effectiveTo;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
