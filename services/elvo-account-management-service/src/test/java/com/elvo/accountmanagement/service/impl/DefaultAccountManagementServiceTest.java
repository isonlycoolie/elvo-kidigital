package com.elvo.accountmanagement.service.impl;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.elvo.accountmanagement.contract.AccountContracts.CreateAccountRequest;
import com.elvo.accountmanagement.contract.AccountContracts.LifecycleRequest;
import com.elvo.accountmanagement.contract.AccountContracts.AdminActionApprovalRequest;
import com.elvo.accountmanagement.contract.AccountContracts.AdminActionRequest;
import com.elvo.accountmanagement.contract.AccountContracts.LimitChangeActivationRequest;
import com.elvo.accountmanagement.contract.AccountContracts.LimitChangeRequest;
import com.elvo.accountmanagement.contract.AccountContracts.LimitCheckRequest;
import com.elvo.accountmanagement.contract.AccountContracts.PermissionChangeApprovalRequest;
import com.elvo.accountmanagement.contract.AccountContracts.PermissionChangeRequest;
import com.elvo.accountmanagement.contract.AccountContracts.ValidationRequest;
import com.elvo.accountmanagement.entity.Account;
import com.elvo.accountmanagement.entity.AccountAdminActionRequest;
import com.elvo.accountmanagement.entity.AccountLimit;
import com.elvo.accountmanagement.entity.AccountLimitChangeRequest;
import com.elvo.accountmanagement.entity.AccountPermissionChangeRequest;
import com.elvo.accountmanagement.entity.AccountPermission;
import com.elvo.accountmanagement.entity.AccountRelationship;
import com.elvo.accountmanagement.messaging.publisher.AccountAuditEventPublisher;
import com.elvo.accountmanagement.messaging.publisher.AccountEventPublisher;
import com.elvo.accountmanagement.repository.AccountAuditLogRepository;
import com.elvo.accountmanagement.repository.AccountAdminActionRequestRepository;
import com.elvo.accountmanagement.repository.AccountLimitRepository;
import com.elvo.accountmanagement.repository.AccountLimitChangeRequestRepository;
import com.elvo.accountmanagement.repository.AccountPermissionRepository;
import com.elvo.accountmanagement.repository.AccountPermissionChangeRequestRepository;
import com.elvo.accountmanagement.repository.AccountRelationshipRepository;
import com.elvo.accountmanagement.repository.AccountRepository;
import com.elvo.accountmanagement.repository.AccountRestrictionRepository;
import com.elvo.accountmanagement.util.EanGenerator;

@ExtendWith(MockitoExtension.class)
class DefaultAccountManagementServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AccountPermissionRepository permissionRepository;

    @Mock
    private AccountPermissionChangeRequestRepository permissionChangeRequestRepository;

    @Mock
    private AccountLimitRepository limitRepository;

    @Mock
    private AccountLimitChangeRequestRepository limitChangeRequestRepository;

    @Mock
    private AccountRestrictionRepository restrictionRepository;

    @Mock
    private AccountRelationshipRepository relationshipRepository;

    @Mock
    private AccountAuditLogRepository auditLogRepository;

    @Mock
    private AccountAdminActionRequestRepository adminActionRequestRepository;

    @Mock
    private AccountEventPublisher eventPublisher;

    @Mock
    private AccountAuditEventPublisher auditEventPublisher;

    private DefaultAccountManagementService service;

    @BeforeEach
    void setUp() {
        service = new DefaultAccountManagementService(
                accountRepository,
                permissionRepository,
                limitRepository,
                limitChangeRequestRepository,
                permissionChangeRequestRepository,
                restrictionRepository,
                relationshipRepository,
                auditLogRepository,
                adminActionRequestRepository,
                new EanGenerator(),
                auditEventPublisher,
                eventPublisher);
    }

    @Test
    void createAccountAssignsDefaultState() {
        UUID userId = UUID.randomUUID();
        when(accountRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(accountRepository.findByEan(org.mockito.ArgumentMatchers.anyString())).thenReturn(Optional.empty());
        when(accountRepository.save(org.mockito.ArgumentMatchers.any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(permissionRepository.save(org.mockito.ArgumentMatchers.any(AccountPermission.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(limitRepository.save(org.mockito.ArgumentMatchers.any(AccountLimit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.createAccount(new CreateAccountRequest(
                userId,
                null,
                Account.AccountType.WALLET,
                null,
                Account.KycStatus.UNVERIFIED,
                "req-1",
                "corr-1",
                "identity-service",
                "127.0.0.1",
                "identity"));

        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.accountStatus()).isEqualTo(Account.AccountStatus.PENDING);
        assertThat(response.ean()).hasSize(13);
        verify(auditLogRepository).save(org.mockito.ArgumentMatchers.any());
        verify(auditEventPublisher).publish(org.mockito.ArgumentMatchers.any());
        verify(eventPublisher).publishLifecycle(org.mockito.ArgumentMatchers.any(Account.class), org.mockito.ArgumentMatchers.eq("ACCOUNT_CREATED"), org.mockito.ArgumentMatchers.eq("Account created"), org.mockito.ArgumentMatchers.eq("req-1"), org.mockito.ArgumentMatchers.eq("corr-1"), org.mockito.ArgumentMatchers.eq("identity-service"), org.mockito.ArgumentMatchers.eq("127.0.0.1"), org.mockito.ArgumentMatchers.eq("identity"), org.mockito.ArgumentMatchers.isNull());
    }

    @Test
    void createChildAccountRequiresParentAccount() {
        UUID userId = UUID.randomUUID();
        when(accountRepository.findByUserId(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createAccount(new CreateAccountRequest(
                userId,
                null,
                Account.AccountType.CHILD,
                null,
                Account.KycStatus.UNVERIFIED,
                "req-1b",
                "corr-1b",
                "identity-service",
                "127.0.0.1",
                "identity")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("parentAccountId is required");
    }

    @Test
    void createEmployeeAccountRejectsNonBusinessParentType() {
        UUID userId = UUID.randomUUID();
        UUID parentAccountId = UUID.randomUUID();
        Account parent = new Account();
        parent.setUserId(UUID.randomUUID());
        parent.setEan("1234567890128");
        parent.setAccountType(Account.AccountType.WALLET);
        parent.setAccountStatus(Account.AccountStatus.ACTIVE);
        parent.setKycStatus(Account.KycStatus.VERIFIED);
        setAccountId(parent, parentAccountId);

        when(accountRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(accountRepository.findById(parentAccountId)).thenReturn(Optional.of(parent));

        assertThatThrownBy(() -> service.createAccount(new CreateAccountRequest(
                userId,
                null,
                Account.AccountType.EMPLOYEE,
                parentAccountId,
                Account.KycStatus.UNVERIFIED,
                "req-1c",
                "corr-1c",
                "identity-service",
                "127.0.0.1",
                "identity")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("EMPLOYEE accounts require BUSINESS, MERCHANT, or AGENT parent account");
    }

    @Test
    void createChildAccountPersistsParentChildRelationship() {
        UUID userId = UUID.randomUUID();
        UUID parentAccountId = UUID.randomUUID();

        Account parent = new Account();
        parent.setUserId(UUID.randomUUID());
        parent.setEan("2234567890128");
        parent.setAccountType(Account.AccountType.WALLET);
        parent.setAccountStatus(Account.AccountStatus.ACTIVE);
        parent.setKycStatus(Account.KycStatus.VERIFIED);
        setAccountId(parent, parentAccountId);

        when(accountRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(accountRepository.findById(parentAccountId)).thenReturn(Optional.of(parent));
        when(accountRepository.findByEan(org.mockito.ArgumentMatchers.anyString())).thenReturn(Optional.empty());
        when(accountRepository.save(org.mockito.ArgumentMatchers.any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(permissionRepository.save(org.mockito.ArgumentMatchers.any(AccountPermission.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(limitRepository.save(org.mockito.ArgumentMatchers.any(AccountLimit.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(relationshipRepository.save(org.mockito.ArgumentMatchers.any(AccountRelationship.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.createAccount(new CreateAccountRequest(
                userId,
                null,
                Account.AccountType.CHILD,
                parentAccountId,
                Account.KycStatus.UNVERIFIED,
                "req-1d",
                "corr-1d",
                "identity-service",
                "127.0.0.1",
                "identity"));

        assertThat(response.parentAccountId()).isEqualTo(parentAccountId);
        verify(relationshipRepository).save(org.mockito.ArgumentMatchers.any(AccountRelationship.class));
        verify(eventPublisher).publishPolicy(org.mockito.ArgumentMatchers.any(Account.class), org.mockito.ArgumentMatchers.eq("ACCOUNT_RELATIONSHIP_LINKED"), org.mockito.ArgumentMatchers.eq("PARENT_CHILD"), org.mockito.ArgumentMatchers.eq("req-1d"), org.mockito.ArgumentMatchers.eq("corr-1d"), org.mockito.ArgumentMatchers.eq("identity-service"), org.mockito.ArgumentMatchers.eq("127.0.0.1"), org.mockito.ArgumentMatchers.eq("identity"), org.mockito.ArgumentMatchers.isNull());
    }

    @Test
    void validateTransferRejectsFrozenAccount() {
        UUID sourceAccountId = UUID.randomUUID();
        UUID destinationAccountId = UUID.randomUUID();

        Account source = new Account();
        source.setUserId(UUID.randomUUID());
        source.setEan("1234567890128");
        source.setAccountStatus(Account.AccountStatus.FROZEN);
        source.setKycStatus(Account.KycStatus.VERIFIED);
        Account destination = new Account();
        destination.setUserId(UUID.randomUUID());
        destination.setEan("1234567890128");
        destination.setAccountStatus(Account.AccountStatus.ACTIVE);
        destination.setKycStatus(Account.KycStatus.VERIFIED);
        setAccountId(source, sourceAccountId);
        setAccountId(destination, destinationAccountId);

        when(accountRepository.findById(sourceAccountId)).thenReturn(Optional.of(source));
        when(accountRepository.findById(destinationAccountId)).thenReturn(Optional.of(destination));

        var response = service.validateTransfer(new ValidationRequest(
                sourceAccountId,
            destinationAccountId,
                new BigDecimal("10.00"),
                "req-2",
                "corr-2",
                "wallet-service",
                "127.0.0.1",
                "wallet"));

        assertThat(response.allowed()).isFalse();
        assertThat(response.reason()).contains("not allowed to send funds");
    }

    @Test
    void checkLimitRejectsOverLimitAmount() {
        UUID accountId = UUID.randomUUID();
        Account account = new Account();
        account.setUserId(UUID.randomUUID());
        account.setEan("1234567890128");
        account.setAccountStatus(Account.AccountStatus.ACTIVE);
        account.setKycStatus(Account.KycStatus.VERIFIED);
        setAccountId(account, accountId);

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(limitRepository.findByAccountId(accountId)).thenReturn(Optional.of(createLimit()));

        var response = service.checkLimit(new LimitCheckRequest(
                accountId,
                new BigDecimal("2000.00"),
                Account.LimitScope.MAX_SINGLE_TRANSACTION,
                "req-3",
                "corr-3",
                "wallet-service",
                "127.0.0.1",
                "wallet"));

        assertThat(response.allowed()).isFalse();
    }

    @Test
    void freezeAccountMovesToFrozenState() {
        UUID accountId = UUID.randomUUID();
        Account account = new Account();
        account.setUserId(UUID.randomUUID());
        account.setEan("1234567890128");
        account.setAccountStatus(Account.AccountStatus.ACTIVE);
        account.setKycStatus(Account.KycStatus.VERIFIED);
        setAccountId(account, accountId);

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(accountRepository.save(org.mockito.ArgumentMatchers.any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.freezeAccount(new LifecycleRequest(
                accountId,
                "manual freeze",
                "req-4",
                "corr-4",
                "ops-service",
                "127.0.0.1",
                "ops"));

        assertThat(response.accountStatus()).isEqualTo(Account.AccountStatus.FROZEN);
        verify(auditEventPublisher).publish(org.mockito.ArgumentMatchers.any());
    verify(eventPublisher).publishLifecycle(org.mockito.ArgumentMatchers.any(Account.class), org.mockito.ArgumentMatchers.eq("ACCOUNT_FROZEN"), org.mockito.ArgumentMatchers.eq("manual freeze"), org.mockito.ArgumentMatchers.eq("req-4"), org.mockito.ArgumentMatchers.eq("corr-4"), org.mockito.ArgumentMatchers.eq("ops-service"), org.mockito.ArgumentMatchers.eq("127.0.0.1"), org.mockito.ArgumentMatchers.eq("ops"), org.mockito.ArgumentMatchers.isNull());
    }

    @Test
    void activateAccountMovesPendingToActiveState() {
        UUID accountId = UUID.randomUUID();
        Account account = new Account();
        account.setUserId(UUID.randomUUID());
        account.setEan("1234567890128");
        account.setAccountStatus(Account.AccountStatus.PENDING);
        account.setKycStatus(Account.KycStatus.VERIFIED);
        setAccountId(account, accountId);

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(accountRepository.save(org.mockito.ArgumentMatchers.any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.activateAccount(new LifecycleRequest(
                accountId,
                "kyc approved",
                "req-5",
                "corr-5",
                "account-service",
                "127.0.0.1",
                "account"));

        assertThat(response.accountStatus()).isEqualTo(Account.AccountStatus.ACTIVE);
    }

    @Test
    void reopenAccountMovesClosedToActiveState() {
        UUID accountId = UUID.randomUUID();
        Account account = new Account();
        account.setUserId(UUID.randomUUID());
        account.setEan("1234567890128");
        account.setAccountStatus(Account.AccountStatus.CLOSED);
        account.setKycStatus(Account.KycStatus.VERIFIED);
        setAccountId(account, accountId);

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(accountRepository.save(org.mockito.ArgumentMatchers.any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.reopenAccount(new LifecycleRequest(
                accountId,
                "manual reopen",
                "req-6",
                "corr-6",
                "ops-service",
                "127.0.0.1",
                "ops"));

        assertThat(response.accountStatus()).isEqualTo(Account.AccountStatus.ACTIVE);
    }

    @Test
    void archiveAccountRequiresClosedState() {
        UUID accountId = UUID.randomUUID();
        Account account = new Account();
        account.setUserId(UUID.randomUUID());
        account.setEan("1234567890128");
        account.setAccountStatus(Account.AccountStatus.ACTIVE);
        account.setKycStatus(Account.KycStatus.VERIFIED);
        setAccountId(account, accountId);

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.archiveAccount(new LifecycleRequest(
                accountId,
                "archive request",
                "req-7",
                "corr-7",
                "ops-service",
                "127.0.0.1",
                "ops")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invalid lifecycle transition");
    }

    @Test
    void validateTransferRejectsUnverifiedKycForOutbound() {
        UUID sourceAccountId = UUID.randomUUID();
        UUID destinationAccountId = UUID.randomUUID();
        Account source = new Account();
        source.setUserId(UUID.randomUUID());
        source.setEan("2234567890128");
        source.setAccountStatus(Account.AccountStatus.ACTIVE);
        source.setKycStatus(Account.KycStatus.UNVERIFIED);
        Account destination = new Account();
        destination.setUserId(UUID.randomUUID());
        destination.setEan("3234567890128");
        destination.setAccountStatus(Account.AccountStatus.ACTIVE);
        destination.setKycStatus(Account.KycStatus.VERIFIED);
        setAccountId(source, sourceAccountId);
        setAccountId(destination, destinationAccountId);

        when(accountRepository.findById(sourceAccountId)).thenReturn(Optional.of(source));
        when(accountRepository.findById(destinationAccountId)).thenReturn(Optional.of(destination));

        var response = service.validateTransfer(new ValidationRequest(
                sourceAccountId,
                destinationAccountId,
                new BigDecimal("10.00"),
                "req-8",
                "corr-8",
                "wallet-service",
                "127.0.0.1",
                "wallet"));

        assertThat(response.allowed()).isFalse();
        assertThat(response.reason()).contains("KYC level does not permit TRANSFER");
    }

    @Test
    void validateReceiveAllowsPartialKycForInbound() {
        UUID destinationAccountId = UUID.randomUUID();
        Account destination = new Account();
        destination.setUserId(UUID.randomUUID());
        destination.setEan("4234567890128");
        destination.setAccountStatus(Account.AccountStatus.ACTIVE);
        destination.setKycStatus(Account.KycStatus.PARTIAL);
        setAccountId(destination, destinationAccountId);

        AccountPermission destinationPermission = new AccountPermission();
        destinationPermission.setCanReceiveMoney(true);

        when(accountRepository.findById(destinationAccountId)).thenReturn(Optional.of(destination));
        when(permissionRepository.findByAccountId(destinationAccountId)).thenReturn(Optional.of(destinationPermission));

        var response = service.validateReceive(new ValidationRequest(
                null,
                destinationAccountId,
                new BigDecimal("5.00"),
                "req-9",
                "corr-9",
                "wallet-service",
                "127.0.0.1",
                "wallet"));

        assertThat(response.allowed()).isTrue();
    }

        @Test
        void requestLimitChangeCreatesPendingRequestWithCoolingPeriod() {
        UUID accountId = UUID.randomUUID();
        Account account = new Account();
        account.setUserId(UUID.randomUUID());
        account.setEan("5234567890128");
        account.setAccountStatus(Account.AccountStatus.ACTIVE);
        account.setKycStatus(Account.KycStatus.VERIFIED);
        setAccountId(account, accountId);

        AccountLimit limit = createLimit();
        limit.setAccountId(accountId);

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(limitRepository.findByAccountId(accountId)).thenReturn(Optional.of(limit));
        when(limitChangeRequestRepository.save(org.mockito.ArgumentMatchers.any(AccountLimitChangeRequest.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.requestLimitChange(new LimitChangeRequest(
            accountId,
            Account.LimitScope.DAILY_TRANSFER,
            new BigDecimal("1500.00"),
            "temporary increase",
            "user-1",
            "req-10",
            "corr-10",
            "wallet-service",
            "127.0.0.1",
            "wallet"));

        assertThat(response.accountId()).isEqualTo(accountId);
        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.previousAmount()).isEqualByComparingTo("1000.00");
        assertThat(response.requestedAmount()).isEqualByComparingTo("1500.00");
        assertThat(response.activationAt()).isNotNull();
        assertThat(response.activatedAt()).isNull();
        }

        @Test
        void activateLimitChangeAppliesRequestedAmountAfterCoolingPeriod() {
        UUID accountId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();

        Account account = new Account();
        account.setUserId(UUID.randomUUID());
        account.setEan("6234567890128");
        account.setAccountStatus(Account.AccountStatus.ACTIVE);
        account.setKycStatus(Account.KycStatus.VERIFIED);
        setAccountId(account, accountId);

        AccountLimit limit = createLimit();
        limit.setAccountId(accountId);

        AccountLimitChangeRequest changeRequest = new AccountLimitChangeRequest();
        setLimitChangeRequestId(changeRequest, requestId);
        changeRequest.setAccountId(accountId);
        changeRequest.setLimitScope(Account.LimitScope.WITHDRAWAL);
        changeRequest.setPreviousAmount(new BigDecimal("500.00"));
        changeRequest.setRequestedAmount(new BigDecimal("800.00"));
        changeRequest.setStatus(AccountLimitChangeRequest.Status.PENDING);
        changeRequest.setActivationAt(Instant.now().minusSeconds(60));

        when(limitChangeRequestRepository.findById(requestId)).thenReturn(Optional.of(changeRequest));
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(limitRepository.findByAccountId(accountId)).thenReturn(Optional.of(limit));
        when(limitRepository.save(org.mockito.ArgumentMatchers.any(AccountLimit.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(limitChangeRequestRepository.save(org.mockito.ArgumentMatchers.any(AccountLimitChangeRequest.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.activateLimitChange(new LimitChangeActivationRequest(
            requestId,
            "req-11",
            "corr-11",
            "wallet-service",
            "127.0.0.1",
            "wallet",
            "ops-user"));

        assertThat(limit.getWithdrawalLimit()).isEqualByComparingTo("800.00");
        assertThat(response.status()).isEqualTo("ACTIVATED");
        assertThat(response.activatedAt()).isNotNull();
        }

        @Test
        void requestPermissionChangeCreatesPendingApprovalRequest() {
        UUID accountId = UUID.randomUUID();
        Account account = new Account();
        account.setUserId(UUID.randomUUID());
        account.setEan("7234567890128");
        account.setAccountStatus(Account.AccountStatus.ACTIVE);
        account.setKycStatus(Account.KycStatus.VERIFIED);
        setAccountId(account, accountId);

        AccountPermission permission = new AccountPermission();
        permission.setCanUseDelegatedAccess(false);

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(permissionRepository.findByAccountId(accountId)).thenReturn(Optional.of(permission));
        when(permissionChangeRequestRepository.save(org.mockito.ArgumentMatchers.any(AccountPermissionChangeRequest.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.requestPermissionChange(new PermissionChangeRequest(
            accountId,
            Account.AccountPermissionFlag.CAN_USE_DELEGATED_ACCESS,
            true,
            "need delegated support",
            "user-2",
            "req-12",
            "corr-12",
            "wallet-service",
            "127.0.0.1",
            "wallet"));

        assertThat(response.accountId()).isEqualTo(accountId);
        assertThat(response.status()).isEqualTo("PENDING_APPROVAL");
        assertThat(response.previousEnabled()).isFalse();
        assertThat(response.requestedEnabled()).isTrue();
        }

        @Test
        void approvePermissionChangeAppliesFlagAndMarksApproved() {
        UUID accountId = UUID.randomUUID();
        UUID permissionChangeRequestId = UUID.randomUUID();

        Account account = new Account();
        account.setUserId(UUID.randomUUID());
        account.setEan("8234567890128");
        account.setAccountStatus(Account.AccountStatus.ACTIVE);
        account.setKycStatus(Account.KycStatus.VERIFIED);
        setAccountId(account, accountId);

        AccountPermission permission = new AccountPermission();
        permission.setCanUseAgentWithdrawal(false);

        AccountPermissionChangeRequest changeRequest = new AccountPermissionChangeRequest();
        setPermissionChangeRequestId(changeRequest, permissionChangeRequestId);
        changeRequest.setAccountId(accountId);
        changeRequest.setPermissionFlag(Account.AccountPermissionFlag.CAN_USE_AGENT_WITHDRAWAL);
        changeRequest.setPreviousEnabled(false);
        changeRequest.setRequestedEnabled(true);
        changeRequest.setStatus(AccountPermissionChangeRequest.Status.PENDING_APPROVAL);
        changeRequest.setRequestedBy("user-3");

        when(permissionChangeRequestRepository.findById(permissionChangeRequestId)).thenReturn(Optional.of(changeRequest));
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(permissionRepository.findByAccountId(accountId)).thenReturn(Optional.of(permission));
        when(permissionRepository.save(org.mockito.ArgumentMatchers.any(AccountPermission.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(permissionChangeRequestRepository.save(org.mockito.ArgumentMatchers.any(AccountPermissionChangeRequest.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.approvePermissionChange(new PermissionChangeApprovalRequest(
            permissionChangeRequestId,
            "approved for pilot",
            "ops-approver",
            "req-13",
            "corr-13",
            "ops-service",
            "127.0.0.1",
            "ops"));

        assertThat(permission.isCanUseAgentWithdrawal()).isTrue();
        assertThat(response.status()).isEqualTo("APPROVED");
        assertThat(response.approvedBy()).isEqualTo("ops-approver");
        assertThat(response.approvedAt()).isNotNull();
        }

        @Test
        void requestAdminActionCreatesPendingMakerCheckerRecord() {
        UUID accountId = UUID.randomUUID();

        Account account = new Account();
        account.setUserId(UUID.randomUUID());
        account.setEan("9234567890128");
        account.setAccountStatus(Account.AccountStatus.ACTIVE);
        account.setKycStatus(Account.KycStatus.VERIFIED);
        setAccountId(account, accountId);

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(adminActionRequestRepository.save(org.mockito.ArgumentMatchers.any(AccountAdminActionRequest.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.requestAdminAction(new AdminActionRequest(
            accountId,
            "FREEZE",
            null,
            "risk signal",
            "maker-user",
            "req-14",
            "corr-14",
            "ops-service",
            "127.0.0.1",
            "ops"));

        assertThat(response.accountId()).isEqualTo(accountId);
        assertThat(response.actionType()).isEqualTo("FREEZE");
        assertThat(response.status()).isEqualTo("PENDING_APPROVAL");
        }

        @Test
        void approveAdminActionRequiresDifferentCheckerAndAppliesAction() {
        UUID accountId = UUID.randomUUID();
        UUID adminActionRequestId = UUID.randomUUID();

        Account account = new Account();
        account.setUserId(UUID.randomUUID());
        account.setEan("1034567890128");
        account.setAccountStatus(Account.AccountStatus.ACTIVE);
        account.setKycStatus(Account.KycStatus.VERIFIED);
        setAccountId(account, accountId);

        AccountAdminActionRequest adminActionRequest = new AccountAdminActionRequest();
        setAdminActionRequestId(adminActionRequest, adminActionRequestId);
        adminActionRequest.setAccountId(accountId);
        adminActionRequest.setActionType("FREEZE");
        adminActionRequest.setReason("investigation");
        adminActionRequest.setRequestedBy("maker-user");
        adminActionRequest.setStatus(AccountAdminActionRequest.Status.PENDING_APPROVAL);

        when(adminActionRequestRepository.findById(adminActionRequestId)).thenReturn(Optional.of(adminActionRequest));
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(accountRepository.save(org.mockito.ArgumentMatchers.any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(adminActionRequestRepository.save(org.mockito.ArgumentMatchers.any(AccountAdminActionRequest.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.approveAdminAction(new AdminActionApprovalRequest(
            adminActionRequestId,
            "approved",
            "checker-user",
            "req-15",
            "corr-15",
            "ops-service",
            "127.0.0.1",
            "ops"));

        assertThat(account.getAccountStatus()).isEqualTo(Account.AccountStatus.FROZEN);
        assertThat(response.status()).isEqualTo("APPROVED");
        assertThat(response.approvedBy()).isEqualTo("checker-user");
        }

    private static AccountLimit createLimit() {
        AccountLimit limit = new AccountLimit();
        limit.setMaxSingleTransaction(new BigDecimal("1000.00"));
        limit.setDailyTransferLimit(new BigDecimal("1000.00"));
        limit.setWithdrawalLimit(new BigDecimal("500.00"));
        return limit;
    }

    private static void setAccountId(Account account, UUID accountId) {
        try {
            var field = Account.class.getDeclaredField("accountId");
            field.setAccessible(true);
            field.set(account, accountId);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static void setLimitChangeRequestId(AccountLimitChangeRequest request, UUID requestId) {
        try {
            var field = AccountLimitChangeRequest.class.getDeclaredField("limitChangeRequestId");
            field.setAccessible(true);
            field.set(request, requestId);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static void setPermissionChangeRequestId(AccountPermissionChangeRequest request, UUID requestId) {
        try {
            var field = AccountPermissionChangeRequest.class.getDeclaredField("permissionChangeRequestId");
            field.setAccessible(true);
            field.set(request, requestId);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static void setAdminActionRequestId(AccountAdminActionRequest request, UUID requestId) {
        try {
            var field = AccountAdminActionRequest.class.getDeclaredField("adminActionRequestId");
            field.setAccessible(true);
            field.set(request, requestId);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
