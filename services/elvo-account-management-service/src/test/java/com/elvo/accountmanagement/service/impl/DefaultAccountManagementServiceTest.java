package com.elvo.accountmanagement.service.impl;

import java.math.BigDecimal;
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
import com.elvo.accountmanagement.contract.AccountContracts.LimitCheckRequest;
import com.elvo.accountmanagement.contract.AccountContracts.ValidationRequest;
import com.elvo.accountmanagement.entity.Account;
import com.elvo.accountmanagement.entity.AccountLimit;
import com.elvo.accountmanagement.entity.AccountPermission;
import com.elvo.accountmanagement.entity.AccountRelationship;
import com.elvo.accountmanagement.messaging.publisher.AccountAuditEventPublisher;
import com.elvo.accountmanagement.messaging.publisher.AccountEventPublisher;
import com.elvo.accountmanagement.repository.AccountAuditLogRepository;
import com.elvo.accountmanagement.repository.AccountLimitRepository;
import com.elvo.accountmanagement.repository.AccountPermissionRepository;
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
    private AccountLimitRepository limitRepository;

    @Mock
    private AccountRestrictionRepository restrictionRepository;

    @Mock
    private AccountRelationshipRepository relationshipRepository;

    @Mock
    private AccountAuditLogRepository auditLogRepository;

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
                restrictionRepository,
                relationshipRepository,
                auditLogRepository,
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
}
