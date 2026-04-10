package com.elvo.accountmanagement.service.impl;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.elvo.accountmanagement.contract.AccountContracts.AccountResponse;
import com.elvo.accountmanagement.contract.AccountContracts.CreateAccountRequest;
import com.elvo.accountmanagement.contract.AccountContracts.LifecycleRequest;
import com.elvo.accountmanagement.contract.AccountContracts.AdminActionApprovalRequest;
import com.elvo.accountmanagement.contract.AccountContracts.AdminActionRequest;
import com.elvo.accountmanagement.contract.AccountContracts.AdminActionWorkflowResponse;
import com.elvo.accountmanagement.contract.AccountContracts.LimitChangeActivationRequest;
import com.elvo.accountmanagement.contract.AccountContracts.LimitChangeRequest;
import com.elvo.accountmanagement.contract.AccountContracts.LimitChangeWorkflowResponse;
import com.elvo.accountmanagement.contract.AccountContracts.LimitCheckRequest;
import com.elvo.accountmanagement.contract.AccountContracts.LimitResponse;
import com.elvo.accountmanagement.contract.AccountContracts.PermissionCheckRequest;
import com.elvo.accountmanagement.contract.AccountContracts.PermissionChangeApprovalRequest;
import com.elvo.accountmanagement.contract.AccountContracts.PermissionChangeRequest;
import com.elvo.accountmanagement.contract.AccountContracts.PermissionChangeWorkflowResponse;
import com.elvo.accountmanagement.contract.AccountContracts.PermissionResponse;
import com.elvo.accountmanagement.contract.AccountContracts.RestrictionRequest;
import com.elvo.accountmanagement.contract.AccountContracts.RestrictionResponse;
import com.elvo.accountmanagement.contract.AccountContracts.ValidationRequest;
import com.elvo.accountmanagement.contract.AccountContracts.ValidationResponse;
import com.elvo.accountmanagement.entity.Account;
import com.elvo.accountmanagement.entity.AccountAdminActionRequest;
import com.elvo.accountmanagement.entity.AccountAuditLog;
import com.elvo.accountmanagement.entity.AccountLimit;
import com.elvo.accountmanagement.entity.AccountLimitChangeRequest.Status;
import com.elvo.accountmanagement.entity.AccountLimitChangeRequest;
import com.elvo.accountmanagement.entity.AccountPermissionChangeRequest;
import com.elvo.accountmanagement.entity.AccountPermission;
import com.elvo.accountmanagement.entity.AccountRelationship;
import com.elvo.accountmanagement.entity.AccountRestriction;
import com.elvo.accountmanagement.repository.AccountAuditLogRepository;
import com.elvo.accountmanagement.repository.AccountAdminActionRequestRepository;
import com.elvo.accountmanagement.repository.AccountLimitRepository;
import com.elvo.accountmanagement.repository.AccountLimitChangeRequestRepository;
import com.elvo.accountmanagement.repository.AccountPermissionRepository;
import com.elvo.accountmanagement.repository.AccountPermissionChangeRequestRepository;
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

    private static final Duration LIMIT_CHANGE_COOLING_PERIOD = Duration.ofHours(24);
        private static final Set<String> MAKER_CHECKER_ACTIONS = Set.of(
            "FREEZE",
            "SUSPEND",
            "CLOSE",
            "ARCHIVE",
            "RESTRICT",
            "REMOVE_RESTRICTION");

    private final AccountRepository accountRepository;
    private final AccountPermissionRepository permissionRepository;
    private final AccountLimitRepository limitRepository;
    private final AccountLimitChangeRequestRepository limitChangeRequestRepository;
    private final AccountPermissionChangeRequestRepository permissionChangeRequestRepository;
    private final AccountRestrictionRepository restrictionRepository;
    private final AccountRelationshipRepository relationshipRepository;
    private final AccountAuditLogRepository auditLogRepository;
    private final AccountAdminActionRequestRepository adminActionRequestRepository;
    private final EanGenerator eanGenerator;
    private final AccountAuditEventPublisher auditEventPublisher;
    private final AccountEventPublisher eventPublisher;

    public DefaultAccountManagementService(AccountRepository accountRepository,
                                           AccountPermissionRepository permissionRepository,
                                           AccountLimitRepository limitRepository,
                                           AccountLimitChangeRequestRepository limitChangeRequestRepository,
                                           AccountPermissionChangeRequestRepository permissionChangeRequestRepository,
                                           AccountRestrictionRepository restrictionRepository,
                                           AccountRelationshipRepository relationshipRepository,
                                           AccountAuditLogRepository auditLogRepository,
                                           AccountAdminActionRequestRepository adminActionRequestRepository,
                                           EanGenerator eanGenerator,
                                           AccountAuditEventPublisher auditEventPublisher,
                                           AccountEventPublisher eventPublisher) {
        this.accountRepository = accountRepository;
        this.permissionRepository = permissionRepository;
        this.limitRepository = limitRepository;
        this.limitChangeRequestRepository = limitChangeRequestRepository;
        this.permissionChangeRequestRepository = permissionChangeRequestRepository;
        this.restrictionRepository = restrictionRepository;
        this.relationshipRepository = relationshipRepository;
        this.auditLogRepository = auditLogRepository;
        this.adminActionRequestRepository = adminActionRequestRepository;
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
    public LimitChangeWorkflowResponse requestLimitChange(LimitChangeRequest request) {
        validateRequest(request.accountId(), "accountId");
        validateRequest(request.limitScope(), "limitScope");
        validateRequest(request.requestedBy(), "requestedBy");
        validateAmount(request.requestedAmount());

        Account account = findAccount(request.accountId());
        AccountLimit limit = limitRepository.findByAccountId(account.getAccountId())
                .orElseThrow(() -> new IllegalArgumentException("Account limits not configured"));

        BigDecimal previousAmount = resolveLimitThreshold(limit, request.limitScope());
        if (previousAmount == null) {
            throw new IllegalArgumentException("Limit scope is not supported");
        }

        AccountLimitChangeRequest changeRequest = new AccountLimitChangeRequest();
        changeRequest.setAccountId(account.getAccountId());
        changeRequest.setLimitScope(request.limitScope());
        changeRequest.setPreviousAmount(previousAmount);
        changeRequest.setRequestedAmount(request.requestedAmount());
        changeRequest.setReason(request.reason());
        changeRequest.setRequestedBy(request.requestedBy());
        changeRequest.setActivationAt(Instant.now().plus(LIMIT_CHANGE_COOLING_PERIOD));
        changeRequest.setStatus(Status.PENDING);

        AccountLimitChangeRequest savedRequest = limitChangeRequestRepository.save(changeRequest);

        String description = "Limit change requested for " + request.limitScope() + ": "
                + previousAmount + " -> " + request.requestedAmount()
                + ", activation at " + savedRequest.getActivationAt();
        audit(account.getAccountId(), "LIMIT_CHANGE_REQUESTED", description, request.requestId(), request.correlationId(), request.sourceService(), request.sourceIp(), request.sourceUserAgent(), request.requestedBy());
        eventPublisher.publishPolicy(account, "LIMIT_CHANGE_REQUESTED", description, request.requestId(), request.correlationId(), request.sourceService(), request.sourceIp(), request.sourceUserAgent(), request.requestedBy());

        return toLimitChangeWorkflowResponse(savedRequest);
    }

    @Override
    public LimitChangeWorkflowResponse activateLimitChange(LimitChangeActivationRequest request) {
        validateRequest(request.limitChangeRequestId(), "limitChangeRequestId");

        AccountLimitChangeRequest changeRequest = limitChangeRequestRepository.findById(request.limitChangeRequestId())
                .orElseThrow(() -> new IllegalArgumentException("Limit change request not found"));
        if (changeRequest.getStatus() != Status.PENDING) {
            return toLimitChangeWorkflowResponse(changeRequest);
        }

        Instant now = Instant.now();
        if (now.isBefore(changeRequest.getActivationAt())) {
            throw new IllegalStateException("Cooling period has not elapsed for limit change activation");
        }

        Account account = findAccount(changeRequest.getAccountId());
        AccountLimit limit = limitRepository.findByAccountId(account.getAccountId())
                .orElseThrow(() -> new IllegalArgumentException("Account limits not configured"));

        applyLimitThreshold(limit, changeRequest.getLimitScope(), changeRequest.getRequestedAmount());
        limitRepository.save(limit);

        changeRequest.setStatus(Status.ACTIVATED);
        changeRequest.setActivatedAt(now);
        AccountLimitChangeRequest savedRequest = limitChangeRequestRepository.save(changeRequest);

        String description = "Limit change activated for " + savedRequest.getLimitScope() + ": "
                + savedRequest.getPreviousAmount() + " -> " + savedRequest.getRequestedAmount();
        audit(account.getAccountId(), "LIMIT_CHANGE_ACTIVATED", description, request.requestId(), request.correlationId(), request.sourceService(), request.sourceIp(), request.sourceUserAgent(), request.activatedBy());
        eventPublisher.publishPolicy(account, "LIMIT_CHANGE_ACTIVATED", description, request.requestId(), request.correlationId(), request.sourceService(), request.sourceIp(), request.sourceUserAgent(), request.activatedBy());

        return toLimitChangeWorkflowResponse(savedRequest);
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
        public PermissionChangeWorkflowResponse requestPermissionChange(PermissionChangeRequest request) {
        validateRequest(request.accountId(), "accountId");
        validateRequest(request.permissionFlag(), "permissionFlag");
        validateRequest(request.requestedBy(), "requestedBy");

        Account account = findAccount(request.accountId());
        AccountPermission permission = permissionRepository.findByAccountId(account.getAccountId())
            .orElseThrow(() -> new IllegalArgumentException("Account permissions not configured"));
        boolean previousEnabled = isPermissionEnabled(permission, request.permissionFlag());

        AccountPermissionChangeRequest changeRequest = new AccountPermissionChangeRequest();
        changeRequest.setAccountId(account.getAccountId());
        changeRequest.setPermissionFlag(request.permissionFlag());
        changeRequest.setPreviousEnabled(previousEnabled);
        changeRequest.setRequestedEnabled(request.requestedEnabled());
        changeRequest.setStatus(AccountPermissionChangeRequest.Status.PENDING_APPROVAL);
        changeRequest.setReason(request.reason());
        changeRequest.setRequestedBy(request.requestedBy());

        AccountPermissionChangeRequest savedRequest = permissionChangeRequestRepository.save(changeRequest);

        String description = "Permission change requested for " + request.permissionFlag() + ": "
            + previousEnabled + " -> " + request.requestedEnabled();
        audit(account.getAccountId(), "PERMISSION_CHANGE_REQUESTED", description, request.requestId(), request.correlationId(), request.sourceService(), request.sourceIp(), request.sourceUserAgent(), request.requestedBy());
        eventPublisher.publishPolicy(account, "PERMISSION_CHANGE_REQUESTED", description, request.requestId(), request.correlationId(), request.sourceService(), request.sourceIp(), request.sourceUserAgent(), request.requestedBy());

        return toPermissionChangeWorkflowResponse(savedRequest);
        }

        @Override
        public PermissionChangeWorkflowResponse approvePermissionChange(PermissionChangeApprovalRequest request) {
        validateRequest(request.permissionChangeRequestId(), "permissionChangeRequestId");
        validateRequest(request.approvedBy(), "approvedBy");

        AccountPermissionChangeRequest changeRequest = permissionChangeRequestRepository.findById(request.permissionChangeRequestId())
            .orElseThrow(() -> new IllegalArgumentException("Permission change request not found"));
        if (changeRequest.getStatus() != AccountPermissionChangeRequest.Status.PENDING_APPROVAL) {
            return toPermissionChangeWorkflowResponse(changeRequest);
        }

        Account account = findAccount(changeRequest.getAccountId());
        AccountPermission permission = permissionRepository.findByAccountId(account.getAccountId())
            .orElseThrow(() -> new IllegalArgumentException("Account permissions not configured"));
        applyPermissionFlag(permission, changeRequest.getPermissionFlag(), changeRequest.isRequestedEnabled());
        permissionRepository.save(permission);

        changeRequest.setStatus(AccountPermissionChangeRequest.Status.APPROVED);
        changeRequest.setApprovedBy(request.approvedBy());
        changeRequest.setApprovalNote(request.approvalNote());
        changeRequest.setApprovedAt(Instant.now());
        AccountPermissionChangeRequest savedRequest = permissionChangeRequestRepository.save(changeRequest);

        String description = "Permission change approved for " + savedRequest.getPermissionFlag() + ": "
            + savedRequest.isPreviousEnabled() + " -> " + savedRequest.isRequestedEnabled();
        audit(account.getAccountId(), "PERMISSION_CHANGE_APPROVED", description, request.requestId(), request.correlationId(), request.sourceService(), request.sourceIp(), request.sourceUserAgent(), request.approvedBy());
        eventPublisher.publishPolicy(account, "PERMISSION_CHANGE_APPROVED", description, request.requestId(), request.correlationId(), request.sourceService(), request.sourceIp(), request.sourceUserAgent(), request.approvedBy());

        return toPermissionChangeWorkflowResponse(savedRequest);
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

    @Override
    public AdminActionWorkflowResponse requestAdminAction(AdminActionRequest request) {
        validateRequest(request.accountId(), "accountId");
        validateRequest(request.actionType(), "actionType");
        validateRequest(request.requestedBy(), "requestedBy");

        String normalizedAction = normalizeAdminAction(request.actionType());
        if (!MAKER_CHECKER_ACTIONS.contains(normalizedAction)) {
            throw new IllegalArgumentException("Action is not configured for maker-checker control");
        }
        if (("RESTRICT".equals(normalizedAction) || "REMOVE_RESTRICTION".equals(normalizedAction)) && request.restrictionType() == null) {
            throw new IllegalArgumentException("restrictionType is required for restriction actions");
        }

        Account account = findAccount(request.accountId());

        AccountAdminActionRequest adminActionRequest = new AccountAdminActionRequest();
        adminActionRequest.setAccountId(account.getAccountId());
        adminActionRequest.setActionType(normalizedAction);
        adminActionRequest.setRestrictionType(request.restrictionType());
        adminActionRequest.setStatus(AccountAdminActionRequest.Status.PENDING_APPROVAL);
        adminActionRequest.setReason(request.reason());
        adminActionRequest.setRequestedBy(request.requestedBy());

        AccountAdminActionRequest savedRequest = adminActionRequestRepository.save(adminActionRequest);

        String description = "Admin action request submitted: " + normalizedAction;
        audit(account.getAccountId(), "ADMIN_ACTION_REQUESTED", description, request.requestId(), request.correlationId(), request.sourceService(), request.sourceIp(), request.sourceUserAgent(), request.requestedBy());
        eventPublisher.publishPolicy(account, "ADMIN_ACTION_REQUESTED", description, request.requestId(), request.correlationId(), request.sourceService(), request.sourceIp(), request.sourceUserAgent(), request.requestedBy());

        return toAdminActionWorkflowResponse(savedRequest);
    }

    @Override
    public AdminActionWorkflowResponse approveAdminAction(AdminActionApprovalRequest request) {
        validateRequest(request.adminActionRequestId(), "adminActionRequestId");
        validateRequest(request.approvedBy(), "approvedBy");

        AccountAdminActionRequest adminActionRequest = adminActionRequestRepository.findById(request.adminActionRequestId())
                .orElseThrow(() -> new IllegalArgumentException("Admin action request not found"));
        if (adminActionRequest.getStatus() != AccountAdminActionRequest.Status.PENDING_APPROVAL) {
            return toAdminActionWorkflowResponse(adminActionRequest);
        }
        if (adminActionRequest.getRequestedBy() != null && adminActionRequest.getRequestedBy().equalsIgnoreCase(request.approvedBy())) {
            throw new IllegalStateException("Maker and checker must be different users");
        }

        executeApprovedAdminAction(adminActionRequest, request);

        adminActionRequest.setStatus(AccountAdminActionRequest.Status.APPROVED);
        adminActionRequest.setApprovedBy(request.approvedBy());
        adminActionRequest.setApprovalNote(request.approvalNote());
        adminActionRequest.setApprovedAt(Instant.now());
        AccountAdminActionRequest savedRequest = adminActionRequestRepository.save(adminActionRequest);

        Account account = findAccount(savedRequest.getAccountId());
        String description = "Admin action approved and applied: " + savedRequest.getActionType();
        audit(account.getAccountId(), "ADMIN_ACTION_APPROVED", description, request.requestId(), request.correlationId(), request.sourceService(), request.sourceIp(), request.sourceUserAgent(), request.approvedBy());
        eventPublisher.publishPolicy(account, "ADMIN_ACTION_APPROVED", description, request.requestId(), request.correlationId(), request.sourceService(), request.sourceIp(), request.sourceUserAgent(), request.approvedBy());

        return toAdminActionWorkflowResponse(savedRequest);
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
            String blocked = validateAccountForOutbound(source, actionType);
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

    private void executeApprovedAdminAction(AccountAdminActionRequest adminActionRequest,
                                            AdminActionApprovalRequest approvalRequest) {
        UUID accountId = adminActionRequest.getAccountId();
        String actionType = adminActionRequest.getActionType();
        String reason = adminActionRequest.getReason();
        if ("FREEZE".equals(actionType)) {
            freezeAccount(new LifecycleRequest(accountId, reason, approvalRequest.requestId(), approvalRequest.correlationId(), approvalRequest.sourceService(), approvalRequest.sourceIp(), approvalRequest.sourceUserAgent()));
            return;
        }
        if ("SUSPEND".equals(actionType)) {
            suspendAccount(new LifecycleRequest(accountId, reason, approvalRequest.requestId(), approvalRequest.correlationId(), approvalRequest.sourceService(), approvalRequest.sourceIp(), approvalRequest.sourceUserAgent()));
            return;
        }
        if ("CLOSE".equals(actionType)) {
            closeAccount(new LifecycleRequest(accountId, reason, approvalRequest.requestId(), approvalRequest.correlationId(), approvalRequest.sourceService(), approvalRequest.sourceIp(), approvalRequest.sourceUserAgent()));
            return;
        }
        if ("ARCHIVE".equals(actionType)) {
            archiveAccount(new LifecycleRequest(accountId, reason, approvalRequest.requestId(), approvalRequest.correlationId(), approvalRequest.sourceService(), approvalRequest.sourceIp(), approvalRequest.sourceUserAgent()));
            return;
        }
        if ("RESTRICT".equals(actionType)) {
            restrictAccount(new RestrictionRequest(accountId, adminActionRequest.getRestrictionType(), reason, approvalRequest.approvedBy(), approvalRequest.requestId(), approvalRequest.correlationId(), approvalRequest.sourceService(), approvalRequest.sourceIp(), approvalRequest.sourceUserAgent()));
            return;
        }
        if ("REMOVE_RESTRICTION".equals(actionType)) {
            removeRestriction(new RestrictionRequest(accountId, adminActionRequest.getRestrictionType(), reason, approvalRequest.approvedBy(), approvalRequest.requestId(), approvalRequest.correlationId(), approvalRequest.sourceService(), approvalRequest.sourceIp(), approvalRequest.sourceUserAgent()));
            return;
        }
        throw new IllegalArgumentException("Unsupported admin action: " + actionType);
    }

    private String normalizeAdminAction(String actionType) {
        return actionType.trim().toUpperCase(Locale.ROOT);
    }

    private String validateAccountForOutbound(Account account) {
        return validateAccountForOutbound(account, "TRANSFER");
    }

    private String validateAccountForOutbound(Account account, String actionType) {
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
        String kycBlockReason = validateKycCapability(account, actionType);
        if (kycBlockReason != null) {
            return kycBlockReason;
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
        String kycBlockReason = validateKycCapability(account, "RECEIVE");
        if (kycBlockReason != null) {
            return kycBlockReason;
        }
        return null;
    }

    private String validateKycCapability(Account account, String actionType) {
        int current = kycLevel(account.getKycStatus());
        int required = requiredKycLevel(actionType, account.getAccountType());
        if (current < required) {
            return "KYC level does not permit " + actionType;
        }
        return null;
    }

    private int kycLevel(Account.KycStatus status) {
        if (status == null) {
            return 0;
        }
        return switch (status) {
            case UNVERIFIED -> 0;
            case PARTIAL -> 1;
            case VERIFIED -> 2;
            case ENHANCED -> 3;
            case BLOCKED -> -1;
        };
    }

    private int requiredKycLevel(String actionType, Account.AccountType accountType) {
        if (actionType == null) {
            return 2;
        }
        String normalized = actionType.toUpperCase(Locale.ROOT);
        if (normalized.contains("RECEIVE") || normalized.contains("DEPOSIT")) {
            return 1;
        }
        if (normalized.contains("WITHDRAW") && (accountType == Account.AccountType.AGENT || accountType == Account.AccountType.MERCHANT)) {
            return 3;
        }
        if (normalized.contains("WITHDRAW") || normalized.contains("TRANSFER") || normalized.contains("BILL")) {
            return 2;
        }
        return 2;
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

    private void applyPermissionFlag(AccountPermission permission,
                                     Account.AccountPermissionFlag permissionFlag,
                                     boolean enabled) {
        switch (permissionFlag) {
            case CAN_RECEIVE_MONEY -> permission.setCanReceiveMoney(enabled);
            case CAN_SEND_MONEY -> permission.setCanSendMoney(enabled);
            case CAN_WITHDRAW -> permission.setCanWithdraw(enabled);
            case CAN_DEPOSIT -> permission.setCanDeposit(enabled);
            case CAN_USE_DELEGATED_ACCESS -> permission.setCanUseDelegatedAccess(enabled);
            case CAN_USE_AGENT_WITHDRAWAL -> permission.setCanUseAgentWithdrawal(enabled);
            case CAN_PERFORM_BILL_PAYMENT -> permission.setCanPerformBillPayment(enabled);
            case CAN_CREATE_SUB_ACCOUNTS -> permission.setCanCreateSubAccounts(enabled);
            default -> throw new IllegalArgumentException("Unsupported permission flag");
        }
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

    private void applyLimitThreshold(AccountLimit limit, Account.LimitScope scope, BigDecimal amount) {
        if (scope == null) {
            limit.setMaxSingleTransaction(amount);
            return;
        }
        switch (scope) {
            case DAILY_TRANSFER -> limit.setDailyTransferLimit(amount);
            case MONTHLY_TRANSFER -> limit.setMonthlyTransferLimit(amount);
            case WITHDRAWAL -> limit.setWithdrawalLimit(amount);
            case DEPOSIT -> limit.setDepositLimit(amount);
            case BILL_PAYMENT -> limit.setBillPaymentLimit(amount);
            case MAX_SINGLE_TRANSACTION -> limit.setMaxSingleTransaction(amount);
            default -> throw new IllegalArgumentException("Unsupported limit scope");
        }
    }

    private LimitChangeWorkflowResponse toLimitChangeWorkflowResponse(AccountLimitChangeRequest request) {
        return new LimitChangeWorkflowResponse(
                request.getLimitChangeRequestId(),
                request.getAccountId(),
                request.getLimitScope(),
                request.getPreviousAmount(),
                request.getRequestedAmount(),
                request.getStatus().name(),
                request.getRequestedAt(),
                request.getActivationAt(),
                request.getActivatedAt());
    }

    private PermissionChangeWorkflowResponse toPermissionChangeWorkflowResponse(AccountPermissionChangeRequest request) {
        return new PermissionChangeWorkflowResponse(
                request.getPermissionChangeRequestId(),
                request.getAccountId(),
                request.getPermissionFlag(),
                request.isPreviousEnabled(),
                request.isRequestedEnabled(),
                request.getStatus().name(),
                request.getReason(),
                request.getRequestedBy(),
                request.getApprovedBy(),
                request.getApprovalNote(),
                request.getRequestedAt(),
                request.getApprovedAt());
    }

    private AdminActionWorkflowResponse toAdminActionWorkflowResponse(AccountAdminActionRequest request) {
        return new AdminActionWorkflowResponse(
                request.getAdminActionRequestId(),
                request.getAccountId(),
                request.getActionType(),
                request.getStatus().name(),
                request.getReason(),
                request.getRequestedBy(),
                request.getApprovedBy(),
                request.getApprovalNote(),
                request.getRequestedAt(),
                request.getApprovedAt());
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
