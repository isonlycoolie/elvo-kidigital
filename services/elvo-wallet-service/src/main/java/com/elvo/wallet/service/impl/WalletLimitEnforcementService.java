package com.elvo.wallet.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.elvo.wallet.client.AccountServiceClient;
import com.elvo.wallet.dto.response.LimitsResponseDto;
import com.elvo.wallet.repository.WalletRepository;

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

    private final BigDecimal dailyLimit;
    private final BigDecimal monthlyLimit;
    private final BigDecimal transferLimit;
    private final BigDecimal withdrawalLimit;
    private final BigDecimal depositLimit;
    private final WalletRepository walletRepository;
    private final AccountServiceClient accountServiceClient;

    private final Map<String, BigDecimal> dailyUsage = new ConcurrentHashMap<>();
    private final Map<String, BigDecimal> monthlyUsage = new ConcurrentHashMap<>();

    public WalletLimitEnforcementService() {
        this(new BigDecimal("5000.00"), new BigDecimal("50000.00"), new BigDecimal("2000.00"),
            new BigDecimal("1000.00"), new BigDecimal("10000.00"), null, null);
    }

    @Autowired
    public WalletLimitEnforcementService(
            @Value("${elvo.security.limits.daily:5000.00}") BigDecimal dailyLimit,
            @Value("${elvo.security.limits.monthly:50000.00}") BigDecimal monthlyLimit,
            @Value("${elvo.security.limits.transfer:2000.00}") BigDecimal transferLimit,
            @Value("${elvo.security.limits.withdrawal:1000.00}") BigDecimal withdrawalLimit,
            @Value("${elvo.security.limits.deposit:10000.00}") BigDecimal depositLimit,
            WalletRepository walletRepository,
            AccountServiceClient accountServiceClient) {
        this.dailyLimit = dailyLimit;
        this.monthlyLimit = monthlyLimit;
        this.transferLimit = transferLimit;
        this.withdrawalLimit = withdrawalLimit;
        this.depositLimit = depositLimit;
        this.walletRepository = walletRepository;
        this.accountServiceClient = accountServiceClient;
    }

    public boolean validate(UUID walletId, FlowType flowType, BigDecimal amount) {
        if (walletId == null || flowType == null || amount == null || amount.signum() <= 0) {
            return false;
        }

        if (accountServiceClient == null || walletRepository == null) {
            AUDIT_LOG.warn("wallet_limit_violation type={} walletId={} amount={} reason=account_policy_unavailable", flowType, walletId, amount);
            return false;
        }

        boolean allowed = validateViaAccountService(walletId, flowType, amount);
        if (!allowed) {
            AUDIT_LOG.warn("wallet_limit_violation type={} walletId={} amount={} reason=account_policy", flowType, walletId, amount);
        }
        return allowed;
    }

    private boolean validateViaAccountService(UUID walletId, FlowType flowType, BigDecimal amount) {
        return walletRepository.findById(walletId)
                .flatMap(wallet -> accountServiceClient.findAccountByUserId(wallet.getUserId()))
                .map(account -> accountServiceClient.checkLimit(
                        new AccountServiceClient.AccountLimitCheckRequest(
                                account.accountId(),
                                amount,
                                toAccountLimitScope(flowType),
                                "wallet-limit-" + walletId,
                                null,
                                "wallet-service",
                                null,
                                "wallet-limit-enforcement")))
                .map(AccountServiceClient.AccountLimitCheckResult::allowed)
                .orElse(false);
    }

    private AccountServiceClient.AccountLimitScope toAccountLimitScope(FlowType flowType) {
        return switch (flowType) {
            case WITHDRAWAL -> AccountServiceClient.AccountLimitScope.WITHDRAWAL;
            case DEPOSIT, ETC_REDEEM -> AccountServiceClient.AccountLimitScope.DEPOSIT;
            case TRANSFER, RESERVATION -> AccountServiceClient.AccountLimitScope.MAX_SINGLE_TRANSACTION;
        };
    }

    public void record(UUID walletId, FlowType flowType, BigDecimal amount) {
        if (walletId == null || flowType == null || amount == null || amount.signum() <= 0) {
            return;
        }

        dailyUsage.merge(dailyKey(walletId, flowType), amount, BigDecimal::add);
        monthlyUsage.merge(monthlyKey(walletId, flowType), amount, BigDecimal::add);
    }

    public LimitsResponseDto getLimits(UUID walletId) {
        BigDecimal dailyUsed = aggregateDailyUsage(walletId);
        BigDecimal monthlyUsed = aggregateMonthlyUsage(walletId);
        return new LimitsResponseDto(
            dailyLimit,
            monthlyLimit,
            transferLimit,
            withdrawalLimit,
            depositLimit,
            dailyUsed,
            monthlyUsed
        );
    }

    private BigDecimal aggregateDailyUsage(UUID walletId) {
        String prefix = walletId + ":";
        String suffix = ":" + LocalDate.now();
        return dailyUsage.entrySet().stream()
            .filter(entry -> entry.getKey().startsWith(prefix) && entry.getKey().endsWith(suffix))
            .map(Map.Entry::getValue)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal aggregateMonthlyUsage(UUID walletId) {
        String prefix = walletId + ":";
        String suffix = ":" + YearMonth.now();
        return monthlyUsage.entrySet().stream()
            .filter(entry -> entry.getKey().startsWith(prefix) && entry.getKey().endsWith(suffix))
            .map(Map.Entry::getValue)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String dailyKey(UUID walletId, FlowType flowType) {
        return walletId + ":" + flowType + ":" + LocalDate.now();
    }

    private String monthlyKey(UUID walletId, FlowType flowType) {
        return walletId + ":" + flowType + ":" + YearMonth.now();
    }
}
