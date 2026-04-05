package com.elvo.wallet.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class WalletLimitEnforcementService {

    public enum FlowType {
        DEPOSIT,
        WITHDRAWAL,
        TRANSFER,
        RESERVATION,
        ETC_REDEEM
    }

    private static final Logger AUDIT_LOG = LoggerFactory.getLogger("audit.wallet.limits");

    private static final BigDecimal DEFAULT_DAILY_LIMIT = new BigDecimal("5000.00");
    private static final BigDecimal DEFAULT_MONTHLY_LIMIT = new BigDecimal("50000.00");
    private static final BigDecimal DEFAULT_TRANSFER_LIMIT = new BigDecimal("2000.00");
    private static final BigDecimal DEFAULT_WITHDRAWAL_LIMIT = new BigDecimal("1000.00");
    private static final BigDecimal DEFAULT_DEPOSIT_LIMIT = new BigDecimal("10000.00");

    private final Map<String, BigDecimal> dailyUsage = new ConcurrentHashMap<>();
    private final Map<String, BigDecimal> monthlyUsage = new ConcurrentHashMap<>();

    public boolean validate(UUID walletId, FlowType flowType, BigDecimal amount) {
        if (walletId == null || flowType == null || amount == null || amount.signum() <= 0) {
            return false;
        }

        if (exceedsFlowSpecificLimit(flowType, amount)) {
            AUDIT_LOG.warn("wallet_limit_violation type={} walletId={} amount={} reason=flow_specific", flowType, walletId, amount);
            return false;
        }

        BigDecimal currentDaily = dailyUsage.getOrDefault(dailyKey(walletId, flowType), BigDecimal.ZERO);
        if (currentDaily.add(amount).compareTo(DEFAULT_DAILY_LIMIT) > 0) {
            AUDIT_LOG.warn("wallet_limit_violation type={} walletId={} amount={} reason=daily", flowType, walletId, amount);
            return false;
        }

        BigDecimal currentMonthly = monthlyUsage.getOrDefault(monthlyKey(walletId, flowType), BigDecimal.ZERO);
        if (currentMonthly.add(amount).compareTo(DEFAULT_MONTHLY_LIMIT) > 0) {
            AUDIT_LOG.warn("wallet_limit_violation type={} walletId={} amount={} reason=monthly", flowType, walletId, amount);
            return false;
        }

        return true;
    }

    public void record(UUID walletId, FlowType flowType, BigDecimal amount) {
        if (walletId == null || flowType == null || amount == null || amount.signum() <= 0) {
            return;
        }

        dailyUsage.merge(dailyKey(walletId, flowType), amount, BigDecimal::add);
        monthlyUsage.merge(monthlyKey(walletId, flowType), amount, BigDecimal::add);
    }

    private boolean exceedsFlowSpecificLimit(FlowType flowType, BigDecimal amount) {
        return switch (flowType) {
            case TRANSFER -> amount.compareTo(DEFAULT_TRANSFER_LIMIT) > 0;
            case WITHDRAWAL -> amount.compareTo(DEFAULT_WITHDRAWAL_LIMIT) > 0;
            case DEPOSIT -> amount.compareTo(DEFAULT_DEPOSIT_LIMIT) > 0;
            case RESERVATION, ETC_REDEEM -> false;
        };
    }

    private String dailyKey(UUID walletId, FlowType flowType) {
        return walletId + ":" + flowType + ":" + LocalDate.now();
    }

    private String monthlyKey(UUID walletId, FlowType flowType) {
        return walletId + ":" + flowType + ":" + YearMonth.now();
    }
}
