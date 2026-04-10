package com.elvo.wallet.client;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AccountServiceClient {

    Optional<AccountSummary> findAccountByUserId(UUID userId);

    Optional<AccountSummary> findAccountByEan(String ean);

    AccountValidationResult validateTransfer(AccountValidationRequest request);

    AccountValidationResult validateWithdrawal(AccountValidationRequest request);

    AccountValidationResult validateReceive(AccountValidationRequest request);

        AccountLimitCheckResult checkLimit(AccountLimitCheckRequest request);

        enum AccountLimitScope {
                DAILY_TRANSFER,
                MONTHLY_TRANSFER,
                WITHDRAWAL,
                DEPOSIT,
                BILL_PAYMENT,
                MAX_SINGLE_TRANSACTION
        }

    record AccountSummary(
            UUID accountId,
            UUID userId,
            String ean,
            String accountType,
            String accountStatus,
            String kycStatus,
            UUID parentAccountId,
            Instant createdAt,
            Instant updatedAt,
            long version) {
    }

    record AccountValidationRequest(
            UUID sourceAccountId,
            UUID destinationAccountId,
            BigDecimal amount,
            String requestId,
            String correlationId,
            String sourceService,
            String sourceIp,
            String sourceUserAgent) {
    }

    record AccountValidationResult(
            boolean allowed,
            String reason,
            UUID accountId,
            String ean,
            String accountStatus,
            String kycStatus) {
    }

    record AccountLimitCheckRequest(
            UUID accountId,
            BigDecimal amount,
            AccountLimitScope limitScope,
            String requestId,
            String correlationId,
            String sourceService,
            String sourceIp,
            String sourceUserAgent) {
    }

    record AccountLimitCheckResult(
            boolean allowed,
            String reason,
            UUID accountId,
            String limitScope,
            BigDecimal amount) {
    }
}