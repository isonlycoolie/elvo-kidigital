package com.elvo.accountmanagement.contract;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.elvo.accountmanagement.entity.Account;

public final class AccountContracts {

    private AccountContracts() {
    }

    public record ApiResponse<T>(boolean success, String message, T data) {
        public static <T> ApiResponse<T> ok(String message, T data) {
            return new ApiResponse<>(true, message, data);
        }

        public static <T> ApiResponse<T> error(String message) {
            return new ApiResponse<>(false, message, null);
        }
    }

    public record CreateAccountRequest(
            UUID userId,
            String ean,
            Account.AccountType accountType,
            UUID parentAccountId,
            Account.KycStatus kycStatus,
            String requestId,
            String correlationId,
            String sourceService,
            String sourceIp,
            String sourceUserAgent) {
    }

    public record LifecycleRequest(
            UUID accountId,
            String reason,
            String requestId,
            String correlationId,
            String sourceService,
            String sourceIp,
            String sourceUserAgent) {
    }

    public record ValidationRequest(
            UUID sourceAccountId,
            UUID destinationAccountId,
            BigDecimal amount,
            String requestId,
            String correlationId,
            String sourceService,
            String sourceIp,
            String sourceUserAgent) {
    }

    public record LimitCheckRequest(
            UUID accountId,
            BigDecimal amount,
            Account.LimitScope limitScope,
            String requestId,
            String correlationId,
            String sourceService,
            String sourceIp,
            String sourceUserAgent) {
    }

    public record LimitChangeRequest(
            UUID accountId,
            Account.LimitScope limitScope,
            BigDecimal requestedAmount,
            String reason,
            String requestedBy,
            String requestId,
            String correlationId,
            String sourceService,
            String sourceIp,
            String sourceUserAgent) {
    }

    public record LimitChangeActivationRequest(
            UUID limitChangeRequestId,
            String requestId,
            String correlationId,
            String sourceService,
            String sourceIp,
            String sourceUserAgent,
            String activatedBy) {
    }

    public record PermissionCheckRequest(
            UUID accountId,
            Account.AccountPermissionFlag permissionFlag,
            String requestId,
            String correlationId,
            String sourceService,
            String sourceIp,
            String sourceUserAgent) {
    }

    public record RestrictionRequest(
            UUID accountId,
            Account.RestrictionType restrictionType,
            String reason,
            String createdBy,
            String requestId,
            String correlationId,
            String sourceService,
            String sourceIp,
            String sourceUserAgent) {
    }

    public record AccountResponse(
            UUID accountId,
            UUID userId,
            String ean,
            Account.AccountType accountType,
            Account.AccountStatus accountStatus,
            Account.KycStatus kycStatus,
            UUID parentAccountId,
            Instant createdAt,
            Instant updatedAt,
            long version) {
    }

    public record ValidationResponse(
            boolean allowed,
            String reason,
            UUID accountId,
            String ean,
            Account.AccountStatus accountStatus,
            Account.KycStatus kycStatus) {
    }

    public record PermissionResponse(
            boolean allowed,
            String reason,
            UUID accountId,
            Account.AccountPermissionFlag permissionFlag) {
    }

    public record LimitResponse(
            boolean allowed,
            String reason,
            UUID accountId,
            Account.LimitScope limitScope,
            BigDecimal amount) {
    }

    public record LimitChangeWorkflowResponse(
            UUID limitChangeRequestId,
            UUID accountId,
            Account.LimitScope limitScope,
            BigDecimal previousAmount,
            BigDecimal requestedAmount,
            String status,
            Instant requestedAt,
            Instant activationAt,
            Instant activatedAt) {
    }

    public record RestrictionResponse(
            UUID restrictionId,
            UUID accountId,
            Account.RestrictionType restrictionType,
            String reason,
            Instant startDate,
            Instant endDate) {
    }
}
