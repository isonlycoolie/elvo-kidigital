package com.elvo.wallet.dto.response;

import java.math.BigDecimal;

public class BalanceResponseDto {

    private BigDecimal balance;
    private BigDecimal reservedBalance;
    private BigDecimal availableBalance;

    public BalanceResponseDto() {
    }

    public BalanceResponseDto(BigDecimal balance, BigDecimal reservedBalance) {
        this.balance = balance;
        this.reservedBalance = reservedBalance;
        this.availableBalance = balance.subtract(reservedBalance);
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public BigDecimal getReservedBalance() {
        return reservedBalance;
    }

    public void setReservedBalance(BigDecimal reservedBalance) {
        this.reservedBalance = reservedBalance;
    }

    public BigDecimal getAvailableBalance() {
        return availableBalance;
    }

    public void setAvailableBalance(BigDecimal availableBalance) {
        this.availableBalance = availableBalance;
    }
}
