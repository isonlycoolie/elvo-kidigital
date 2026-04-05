package com.elvo.wallet.dto.response;

import java.math.BigDecimal;

public class LimitsResponseDto {

    private BigDecimal dailyLimit;
    private BigDecimal monthlyLimit;
    private BigDecimal transferLimit;
    private BigDecimal withdrawalLimit;
    private BigDecimal depositLimit;
    private BigDecimal dailyUsed;
    private BigDecimal monthlyUsed;
    private BigDecimal dailyRemaining;
    private BigDecimal monthlyRemaining;

    public LimitsResponseDto() {
    }

    public LimitsResponseDto(BigDecimal dailyLimit, BigDecimal monthlyLimit, BigDecimal transferLimit,
                            BigDecimal withdrawalLimit, BigDecimal depositLimit, BigDecimal dailyUsed,
                            BigDecimal monthlyUsed) {
        this.dailyLimit = dailyLimit;
        this.monthlyLimit = monthlyLimit;
        this.transferLimit = transferLimit;
        this.withdrawalLimit = withdrawalLimit;
        this.depositLimit = depositLimit;
        this.dailyUsed = dailyUsed;
        this.monthlyUsed = monthlyUsed;
        this.dailyRemaining = dailyLimit.subtract(dailyUsed);
        this.monthlyRemaining = monthlyLimit.subtract(monthlyUsed);
    }

    public BigDecimal getDailyLimit() {
        return dailyLimit;
    }

    public void setDailyLimit(BigDecimal dailyLimit) {
        this.dailyLimit = dailyLimit;
    }

    public BigDecimal getMonthlyLimit() {
        return monthlyLimit;
    }

    public void setMonthlyLimit(BigDecimal monthlyLimit) {
        this.monthlyLimit = monthlyLimit;
    }

    public BigDecimal getTransferLimit() {
        return transferLimit;
    }

    public void setTransferLimit(BigDecimal transferLimit) {
        this.transferLimit = transferLimit;
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

    public BigDecimal getDailyUsed() {
        return dailyUsed;
    }

    public void setDailyUsed(BigDecimal dailyUsed) {
        this.dailyUsed = dailyUsed;
    }

    public BigDecimal getMonthlyUsed() {
        return monthlyUsed;
    }

    public void setMonthlyUsed(BigDecimal monthlyUsed) {
        this.monthlyUsed = monthlyUsed;
    }

    public BigDecimal getDailyRemaining() {
        return dailyRemaining;
    }

    public void setDailyRemaining(BigDecimal dailyRemaining) {
        this.dailyRemaining = dailyRemaining;
    }

    public BigDecimal getMonthlyRemaining() {
        return monthlyRemaining;
    }

    public void setMonthlyRemaining(BigDecimal monthlyRemaining) {
        this.monthlyRemaining = monthlyRemaining;
    }
}
