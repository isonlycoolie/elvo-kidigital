package com.elvo.accountmanagement.service.impl;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.elvo.accountmanagement.contract.AccountContracts.AccountResponse;
import com.elvo.accountmanagement.contract.AccountContracts.CreateAccountRequest;
import com.elvo.accountmanagement.contract.AccountContracts.LifecycleRequest;
import com.elvo.accountmanagement.contract.AccountContracts.LimitCheckRequest;
import com.elvo.accountmanagement.contract.AccountContracts.LimitResponse;
import com.elvo.accountmanagement.contract.AccountContracts.PermissionCheckRequest;
import com.elvo.accountmanagement.contract.AccountContracts.PermissionResponse;
import com.elvo.accountmanagement.contract.AccountContracts.RestrictionRequest;
import com.elvo.accountmanagement.contract.AccountContracts.RestrictionResponse;
import com.elvo.accountmanagement.contract.AccountContracts.ValidationRequest;
import com.elvo.accountmanagement.contract.AccountContracts.ValidationResponse;
import com.elvo.accountmanagement.entity.Account;
import com.elvo.accountmanagement.entity.AccountAuditLog;
import com.elvo.accountmanagement.entity.AccountLimit;
import com.elvo.accountmanagement.entity.AccountPermission;
import com.elvo.accountmanagement.entity.AccountRelationship;
import com.elvo.accountmanagement.entity.AccountRestriction;
import com.elvo.accountmanagement.repository.AccountAuditLogRepository;
import com.elvo.accountmanagement.repository.AccountLimitRepository;
import com.elvo.accountmanagement.repository.AccountPermissionRepository;
import com.elvo.accountmanagement.repository.AccountRelationshipRepository;
import com.elvo.accountmanagement.repository.AccountRepository;
import com.elvo.accountmanagement.repository.AccountRestrictionRepository;
import com.elvo.accountmanagement.messaging.publisher.AccountAuditEventPublisher;
import com.elvo.accountmanagement.messaging.publisher.AccountEventPublisher;
import com.elvo.accountmanagement.service.AccountManagementService;
import com.elvo.accountmanagement.util.EanGenerator;

@Service
@Transactional
public class DefaultAccountManagementService implements AccountManagementService {

    private final AccountRepository accountRepository;
    private final AccountPermissionRepository permissionRepository;
    private final AccountLimitRepository limitRepository;
    private final AccountRestrictionRepository restrictionRepository;
    private final AccountRelationshipRepository relationshipRepository;
    private final AccountAuditLogRepository auditLogRepository;
    private final EanGenerator eanGenerator;
    private final AccountAuditEventPublisher auditEventPublisher;
    private final AccountEventPublisher eventPublisher;

    public DefaultAccountManagementService(AccountRepository accountRepository,
                                           AccountPermissionRepository permissionRepository,
                                           AccountLimitRepository limitRepository,
                                           AccountRestrictionRepository restrictionRepository,
                                           AccountRelationshipRepository relationshipRepository,
                                           AccountAuditLogRepository auditLogRepository,
                                           EanGenerator eanGenerator,
                                           AccountAuditEventPublisher auditEventPublisher,
                                           AccountEventPublisher eventPublisher) {
        this.accountRepository = accountRepository;
        this.permissionRepository = permissionRepository;
        this.limitRepository = limitRepository;
        this.restrictionRepository = restrictionRepository;
        this.relationshipRepository = relationshipRepository;
        this.auditLogRepository = auditLogRepository;
        this.eanGenerator = eanGenerator;
        this.auditEventPublisher = auditEventPublisher;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public AccountResponse createAccount(CreateAccountRequest request) {
        validateRequest(request.userId(), "userId");

        accountRepository.findByUserId(request.userId()).ifPresent(existing -> {
            throw new IllegalArgumentException("Account already exists for user");
        });

        Account.AccountType accountType = request.accountType() == null ? Account.AccountType.WALLET : request.accountType();
        RelationshipPolicyDecision relationshipDecision = resolveRelationshipPolicy(accountType, request.parentAccountId());

        Account account = new Account();
        account.setUserId(request.userId());
        account.setEan(hasText(request.ean()) ? request.ean().trim() : generateUniqueEan());
        account.setAccountType(accountType);
        account.setKycStatus(request.kycStatus() == null ? Account.KycStatus.UNVERIFIED : request.kycStatus());
        account.setParentAccountId(relationshipDecision.parentAccountId());
        account.setAccountStatus(Account.AccountStatus.PENDING);

        Account savedAccount = accountRepository.save(account);
        permissionRepository.save(createDefaultPermission(savedAccount.getAccountId()));
        limitRepository.save(createDefaultLimit(savedAccount.getAccountId()));
        persistRelationshipIfNeeded(savedAccount, relationshipDecision, request);
        audit(savedAccount.getAccountId(), "ACCOUNT_CREATED", "Account created", request.requestId(), request.correlationId(), request.sourceService(), request.sourceIp(), request.sourceUserAgent(), null);
        eventPublisher.publishLifecycle(savedAccount, "ACCOUNT_CREATED", "Account created", request.requestId(), request.correlationId(), request.sourceService(), request.sourceIp(), request.sourceUserAgent(), null);
        return toResponse(savedAccount);
    }

    @Override
    @Transactional(readOnly = true)
    public AccountResponse getAccountById(UUID accountId) {
        return toResponse(findAccount(accountId));
    }

    @Override
    @Transactional(readOnly = true)
    public AccountResponse getAccountByUserId(UUID userId) {
        return toResponse(accountRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found")));
    }

    @Override
    @Transactional(readOnly = true)
    public AccountResponse getAccountByEan(String ean) {
        validateRequest(ean, "ean");
        return toResponse(accountRepository.findByEan(ean.trim())
                .orElseThrow(() -> new IllegalArgumentException("Account not found")));
    }

    @Override
    @Transactional(readOnly = true)
    public ValidationResponse validateTransfer(ValidationRequest request) {
        return validateMovement(request, true, true, "TRANSFER");
    }

    @Override
    @Transactional(readOnly = true)
    public ValidationResponse validateWithdrawal(ValidationRequest request) {
        return validateMovement(request, true, false, "WITHDRAWAL");
    }

    @Override
    @Transactional(readOnly = true)
    public ValidationResponse validateReceive(ValidationRequest request) {
        return validateMovement(request, false, true, "RECEIVE");
    }

    @Override
    @Transactional(readOnly = true)
    public LimitResponse checkLimit(LimitCheckRequest request) {
        validateRequest(request.accountId(), "accountId");
        validateAmount(request.amount());

        Account account = findAccount(request.accountId());
        AccountLimit limit = limitRepository.findByAccountId(account.getAccountId())
                .orElseThrow(() -> new IllegalArgumentException("Account limits not configured"));

        BigDecimal threshold = resolveLimitThreshold(limit, request.limitScope());
        boolean allowed = threshold == null || request.amount().compareTo(threshold) <= 0;
        String reason = allowed ? "Allowed" : "Amount exceeds limit";
        return new LimitResponse(allowed, reason, account.getAccountId(), request.limitScope(), request.amount());
    }

    @Override
    @Transactional(readOnly = true)
    public PermissionResponse checkPermission(PermissionCheckRequest request) {
        validateRequest(request.accountId(), "accountId");
        Account account = findAccount(request.accountId());
        AccountPermission permission = permissionRepository.findByAccountId(account.getAccountId())
                .orElseThrow(() -> new IllegalArgumentException("Account permissions not configured"));

        boolean allowed = isPermissionEnabled(permission, request.permissionFlag());
        String reason = allowed ? "Allowed" : "Permission disabled";
        return new PermissionResponse(allowed, reason, account.getAccountId(), request.permissionFlag());
    }

    @Override
    public AccountResponse activateAccount(LifecycleRequest request) {
        return changeStatus(request, Account.AccountStatus.ACTIVE, "ACCOUNT_ACTIVATED");
    }

    @Override
    public AccountResponse freezeAccount(LifecycleRequest request) {
        return changeStatus(request, Account.AccountStatus.FROZEN, "ACCOUNT_FROZEN");
    }

    @Override
    public AccountResponse unfreezeAccount(LifecycleRequest request) {
        return changeStatus(request, Account.AccountStatus.ACTIVE, "ACCOUNT_UNFROZEN");
    }

    @Override
    public AccountResponse suspendAccount(LifecycleRequest request) {
        return changeStatus(request, Account.AccountStatus.SUSPENDED, "ACCOUNT_SUSPENDED");
    }

    @Override
    public AccountResponse closeAccount(LifecycleRequest request) {
        return changeStatus(request, Account.AccountStatus.CLOSED, "ACCOUNT_CLOSED");
    }

    @Override
    public AccountResponse reopenAccount(LifecycleRequest request) {
        return changeStatus(request, Account.AccountStatus.ACTIVE, "ACCOUNT_REOPENED");
    }

    @Override
    public AccountResponse archiveAccount(LifecycleRequest request) {
        return changeStatus(request, Account.AccountStatus.ARCHIVED, "ACCOUNT_ARCHIVED");
    }

    @Override
    public AccountResponse restrictAccount(RestrictionRequest request) {
        Account account = findAccount(request.accountId());
        AccountRestriction restriction = new AccountRestriction();
        restriction.setAccountId(account.getAccountId());
        restriction.setRestrictionType(request.restrictionType());
        restriction.setReason(request.reason());
        restriction.setCreatedBy(request.createdBy());
        restrictionRepository.save(restriction);
        account.setAccountStatus(Account.AccountStatus.RESTRICTED);
        Account saved = accountRepository.save(account);
        audit(saved.getAccountId(), "ACCOUNT_RESTRICTED", request.reason(), request.requestId(), request.correlationId(), request.sourceService(), request.sourceIp(), request.sourceUserAgent(), request.createdBy());
        eventPublisher.publishPolicy(saved, "ACCOUNT_RESTRICTED", request.reason(), request.requestId(), request.correlationId(), request.sourceService(), request.sourceIp(), request.sourceUserAgent(), request.createdBy());
        return toResponse(saved);
    }

    @Override
    public AccountResponse removeRestriction(RestrictionRequest request) {
        Account account = findAccount(request.accountId());
        List<AccountRestriction> activeRestrictions = restrictionRepository.findByAccountIdAndEndDateIsNull(account.getAccountId());
        Instant now = Instant.now();
        activeRestrictions.stream()
                .filter(restriction -> request.restrictionType() == null || restriction.getRestrictionType() == request.restrictionType())
                .forEach(restriction -> restriction.setEndDate(now));
        account.setAccountStatus(Account.AccountStatus.ACTIVE);
        Account saved = accountRepository.save(account);
        audit(saved.getAccountId(), "ACCOUNT_RESTRICTION_REMOVED", request.reason(), request.requestId(), request.correlationId(), request.sourceService(), request.sourceIp(), request.sourceUserAgent(), request.createdBy());
        eventPublisher.publishPolicy(saved, "ACCOUNT_RESTRICTION_REMOVED", request.reason(), request.requestId(), request.correlationId(), request.sourceService(), request.sourceIp(), request.sourceUserAgent(), request.createdBy());
        return toResponse(saved);
    }

    @Override
    public RestrictionResponse createRestrictionRecord(RestrictionRequest request) {
        AccountRestriction restriction = new AccountRestriction();
        restriction.setAccountId(request.accountId());
        restriction.setRestrictionType(request.restrictionType());
        restriction.setReason(request.reason());
        restriction.setCreatedBy(request.createdBy());
        AccountRestriction saved = restrictionRepository.save(restriction);
        Account account = findAccount(request.accountId());
        eventPublisher.publishPolicy(account, "ACCOUNT_RESTRICTION_CREATED", request.reason(), request.requestId(), request.correlationId(), request.sourceService(), request.sourceIp(), request.sourceUserAgent(), request.createdBy());
        return new RestrictionResponse(saved.getRestrictionId(), saved.getAccountId(), saved.getRestrictionType(), saved.getReason(), saved.getStartDate(), saved.getEndDate());
    }

    private AccountResponse changeStatus(LifecycleRequest request, Account.AccountStatus targetStatus, String actionType) {
        Account account = findAccount(request.accountId());
        validateLifecycleTransition(account.getAccountStatus(), targetStatus, actionType);
        account.setAccountStatus(targetStatus);
        Account saved = accountRepository.save(account);
        audit(saved.getAccountId(), actionType, request.reason(), request.requestId(), request.correlationId(), request.sourceService(), request.sourceIp(), request.sourceUserAgent(), null);
        eventPublisher.publishLifecycle(saved, actionType, request.reason(), request.requestId(), request.correlationId(), request.sourceService(), request.sourceIp(), request.sourceUserAgent(), null);
        return toResponse(saved);
    }

    private ValidationResponse validateMovement(ValidationRequest request,
                                                boolean senderRequired,
                                                boolean recipientRequired,
                                                String actionType) {
        validateAmount(request.amount());
        Account source = senderRequired ? findAccount(request.sourceAccountId()) : null;
        Account destination = recipientRequired ? findAccount(request.destinationAccountId()) : null;

        if (source != null) {
            String blocked = validateAccountForOutbound(source);
            if (blocked != null) {
                return new ValidationResponse(false, blocked, source.getAccountId(), source.getEan(), source.getAccountStatus(), source.getKycStatus());
            }
            AccountPermission permission = permissionRepository.findByAccountId(source.getAccountId()).orElse(null);
            if (permission == null || !permission.isCanSendMoney()) {
                return new ValidationResponse(false, "Sending not permitted", source.getAccountId(), source.getEan(), source.getAccountStatus(), source.getKycStatus());
            }
        }

        if (destination != null) {
            String blocked = validateAccountForInbound(destination);
            if (blocked != null) {
                return new ValidationResponse(false, blocked, destination.getAccountId(), destination.getEan(), destination.getAccountStatus(), destination.getKycStatus());
            }
            AccountPermission permission = permissionRepository.findByAccountId(destination.getAccountId()).orElse(null);
            if (permission == null || !permission.isCanReceiveMoney()) {
                return new ValidationResponse(false, "Receiving not permitted", destination.getAccountId(), destination.getEan(), destination.getAccountStatus(), destination.getKycStatus());
            }
        }

        Account primary = source != null ? source : destination;
        if (primary == null) {
            throw new IllegalStateException("Account could not be resolved for validation");
        }
        AccountLimit limit = limitRepository.findByAccountId(primary.getAccountId()).orElse(null);
        if (limit != null) {
            BigDecimal threshold = resolveLimitThreshold(limit, resolveLimitScope(actionType));
            if (threshold != null && request.amount().compareTo(threshold) > 0) {
                return new ValidationResponse(false, "Amount exceeds limit", primary.getAccountId(), primary.getEan(), primary.getAccountStatus(), primary.getKycStatus());
            }
        }

        return new ValidationResponse(true, "Allowed", primary.getAccountId(), primary.getEan(), primary.getAccountStatus(), primary.getKycStatus());
    }

    private void validateLifecycleTransition(Account.AccountStatus current,
                                             Account.AccountStatus target,
                                             String actionType) {
        if (current == Account.AccountStatus.ARCHIVED) {
            throw new IllegalStateException("Archived account cannot transition");
        }

        if (target == current) {
            return;
        }

        if (target == Account.AccountStatus.FROZEN && EnumSet.of(Account.AccountStatus.ACTIVE, Account.AccountStatus.SUSPENDED, Account.AccountStatus.RESTRICTED).contains(current)) {
            return;
        }

        if (target == Account.AccountStatus.SUSPENDED && EnumSet.of(Account.AccountStatus.ACTIVE, Account.AccountStatus.FROZEN, Account.AccountStatus.RESTRICTED).contains(current)) {
            return;
        }

        if (target == Account.AccountStatus.CLOSED && current != Account.AccountStatus.CLOSED) {
            return;
        }

        if (target == Account.AccountStatus.ARCHIVED && current == Account.AccountStatus.CLOSED) {
            return;
        }

        if (target == Account.AccountStatus.ACTIVE) {
            if ("ACCOUNT_UNFROZEN".equals(actionType) && current == Account.AccountStatus.FROZEN) {
                return;
            }
            if ("ACCOUNT_REOPENED".equals(actionType) && current == Account.AccountStatus.CLOSED) {
                return;
            }
            if ("ACCOUNT_ACTIVATED".equals(actionType) && EnumSet.of(Account.AccountStatus.PENDING, Account.AccountStatus.SUSPENDED, Account.AccountStatus.RESTRICTED).contains(current)) {
                return;
            }
        }

        throw new IllegalStateException("Invalid lifecycle transition from " + current + " to " + target);
    }

    private String validateAccountForOutbound(Account account) {
        if (account.getKycStatus() == Account.KycStatus.BLOCKED) {
            return "Account is KYC blocked";
        }
        if (account.getAccountStatus() == Account.AccountStatus.FROZEN || account.getAccountStatus() == Account.AccountStatus.SUSPENDED
                || account.getAccountStatus() == Account.AccountStatus.CLOSED || account.getAccountStatus() == Account.AccountStatus.ARCHIVED) {
            return "Account is not allowed to send funds";
        }
        if (hasActiveRestriction(account.getAccountId(), Account.RestrictionType.SEND_BLOCKED) || hasActiveRestriction(account.getAccountId(), Account.RestrictionType.WITHDRAWAL_BLOCKED)) {
            return "Account is restricted from sending funds";
        }
        return null;
    }

    private String validateAccountForInbound(Account account) {
        if (account.getKycStatus() == Account.KycStatus.BLOCKED) {
            return "Account is KYC blocked";
        }
        if (account.getAccountStatus() == Account.AccountStatus.FROZEN || account.getAccountStatus() == Account.AccountStatus.SUSPENDED
                || account.getAccountStatus() == Account.AccountStatus.CLOSED || account.getAccountStatus() == Account.AccountStatus.ARCHIVED) {
            return "Account is not allowed to receive funds";
        }
        if (hasActiveRestriction(account.getAccountId(), Account.RestrictionType.RECEIVE_BLOCKED)) {
            return "Account is restricted from receiving funds";
        }
        return null;
    }

    private boolean hasActiveRestriction(UUID accountId, Account.RestrictionType restrictionType) {
        return restrictionRepository.findByAccountIdAndEndDateIsNull(accountId).stream()
                .anyMatch(restriction -> restriction.getRestrictionType() == restrictionType || restriction.getRestrictionType() == Account.RestrictionType.FRAUD_HOLD || restriction.getRestrictionType() == Account.RestrictionType.TEMPORARY_LOCK);
    }

    private boolean isPermissionEnabled(AccountPermission permission, Account.AccountPermissionFlag permissionFlag) {
        return switch (permissionFlag) {
            case CAN_RECEIVE_MONEY -> permission.isCanReceiveMoney();
            case CAN_SEND_MONEY -> permission.isCanSendMoney();
            case CAN_WITHDRAW -> permission.isCanWithdraw();
            case CAN_DEPOSIT -> permission.isCanDeposit();
            case CAN_USE_DELEGATED_ACCESS -> permission.isCanUseDelegatedAccess();
            case CAN_USE_AGENT_WITHDRAWAL -> permission.isCanUseAgentWithdrawal();
            case CAN_PERFORM_BILL_PAYMENT -> permission.isCanPerformBillPayment();
            case CAN_CREATE_SUB_ACCOUNTS -> permission.isCanCreateSubAccounts();
        };
    }

    private BigDecimal resolveLimitThreshold(AccountLimit limit, Account.LimitScope scope) {
        if (scope == null) {
            return limit.getMaxSingleTransaction();
        }
        return switch (scope) {
            case DAILY_TRANSFER -> limit.getDailyTransferLimit();
            case MONTHLY_TRANSFER -> limit.getMonthlyTransferLimit();
            case WITHDRAWAL -> limit.getWithdrawalLimit();
            case DEPOSIT -> limit.getDepositLimit();
            case BILL_PAYMENT -> limit.getBillPaymentLimit();
            case MAX_SINGLE_TRANSACTION -> limit.getMaxSingleTransaction();
        };
    }

    private Account.LimitScope resolveLimitScope(String actionType) {
        if (actionType == null) {
            return Account.LimitScope.MAX_SINGLE_TRANSACTION;
        }
        String normalized = actionType.toUpperCase(Locale.ROOT);
        if (normalized.contains("WITHDRAW")) {
            return Account.LimitScope.WITHDRAWAL;
        }
        if (normalized.contains("RECEIVE")) {
            return Account.LimitScope.DEPOSIT;
        }
        return Account.LimitScope.DAILY_TRANSFER;
    }

    private Account findAccount(UUID accountId) {
        validateRequest(accountId, "accountId");
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
    }

    private AccountResponse toResponse(Account account) {
        return new AccountResponse(
                account.getAccountId(),
                account.getUserId(),
                account.getEan(),
                account.getAccountType(),
                account.getAccountStatus(),
                account.getKycStatus(),
                account.getParentAccountId(),
                account.getCreatedAt(),
                account.getUpdatedAt(),
                account.getVersion());
    }

    private AccountPermission createDefaultPermission(UUID accountId) {
        AccountPermission permission = new AccountPermission();
        permission.setAccountId(accountId);
        permission.setCanUseDelegatedAccess(false);
        permission.setCanUseAgentWithdrawal(false);
        permission.setCanCreateSubAccounts(false);
        return permission;
    }

    private AccountLimit createDefaultLimit(UUID accountId) {
        AccountLimit limit = new AccountLimit();
        limit.setAccountId(accountId);
        return limit;
    }

    private String generateUniqueEan() {
        for (int attempt = 0; attempt < 10; attempt++) {
            String candidate = eanGenerator.generate();
            if (accountRepository.findByEan(candidate).isEmpty()) {
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to generate a unique EAN");
    }

    private void audit(UUID accountId,
                       String actionType,
                       String description,
                       String requestId,
                       String correlationId,
                       String sourceService,
                       String sourceIp,
                       String sourceUserAgent,
                       String createdBy) {
        AccountAuditLog auditLog = new AccountAuditLog();
        auditLog.setAccountId(accountId);
        auditLog.setActionType(actionType);
        auditLog.setDescription(description);
        auditLog.setRequestId(requestId);
        auditLog.setCorrelationId(correlationId);
        auditLog.setSourceService(sourceService);
        auditLog.setSourceIp(sourceIp);
        auditLog.setSourceUserAgent(sourceUserAgent);
        auditLog.setCreatedBy(createdBy);
        AccountAuditLog savedAuditLog = auditLogRepository.save(auditLog);
        auditEventPublisher.publish(savedAuditLog);
    }

    private void validateRequest(Object value, String field) {
        if (value == null || (value instanceof String text && text.isBlank())) {
            throw new IllegalArgumentException(field + " is required");
        }
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private RelationshipPolicyDecision resolveRelationshipPolicy(Account.AccountType accountType, UUID parentAccountId) {
        if (accountType == Account.AccountType.CHILD || accountType == Account.AccountType.EMPLOYEE) {
            if (parentAccountId == null) {
                throw new IllegalArgumentException("parentAccountId is required for " + accountType + " accounts");
            }
        }

        if (parentAccountId == null) {
            return RelationshipPolicyDecision.none();
        }

        Account parentAccount = findAccount(parentAccountId);
        if (parentAccount.getAccountStatus() != Account.AccountStatus.ACTIVE) {
            throw new IllegalStateException("Parent account must be ACTIVE");
        }

        if (accountType == Account.AccountType.CHILD) {
            if (parentAccount.getAccountType() == Account.AccountType.CHILD || parentAccount.getAccountType() == Account.AccountType.EMPLOYEE) {
                throw new IllegalStateException("CHILD accounts cannot be nested under CHILD or EMPLOYEE parents");
            }
            return new RelationshipPolicyDecision(parentAccountId, Account.RelationshipType.PARENT_CHILD);
        }

        if (accountType == Account.AccountType.EMPLOYEE) {
            if (!EnumSet.of(Account.AccountType.BUSINESS, Account.AccountType.MERCHANT, Account.AccountType.AGENT)
                    .contains(parentAccount.getAccountType())) {
                throw new IllegalStateException("EMPLOYEE accounts require BUSINESS, MERCHANT, or AGENT parent account");
            }
            return new RelationshipPolicyDecision(parentAccountId, Account.RelationshipType.EMPLOYER_EMPLOYEE);
        }

        throw new IllegalArgumentException("parentAccountId is only supported for CHILD or EMPLOYEE account types");
    }

    private void persistRelationshipIfNeeded(Account childAccount,
                                             RelationshipPolicyDecision relationshipDecision,
                                             CreateAccountRequest request) {
        if (relationshipDecision.relationshipType() == null || relationshipDecision.parentAccountId() == null) {
            return;
        }

        AccountRelationship relationship = new AccountRelationship();
        relationship.setParentAccountId(relationshipDecision.parentAccountId());
        relationship.setChildAccountId(childAccount.getAccountId());
        relationship.setRelationshipType(relationshipDecision.relationshipType());
        relationship.setStatus(Account.RelationshipStatus.ACTIVE);
        relationshipRepository.save(relationship);

        audit(childAccount.getAccountId(),
                "ACCOUNT_RELATIONSHIP_LINKED",
                relationshipDecision.relationshipType().name(),
                request.requestId(),
                request.correlationId(),
                request.sourceService(),
                request.sourceIp(),
                request.sourceUserAgent(),
                null);
        eventPublisher.publishPolicy(childAccount,
            "ACCOUNT_RELATIONSHIP_LINKED",
            relationshipDecision.relationshipType().name(),
            request.requestId(),
            request.correlationId(),
            request.sourceService(),
            request.sourceIp(),
            request.sourceUserAgent(),
            null);
    }

    private record RelationshipPolicyDecision(UUID parentAccountId, Account.RelationshipType relationshipType) {
        static RelationshipPolicyDecision none() {
            return new RelationshipPolicyDecision(null, null);
        }
    }
}
