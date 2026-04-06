package com.elvo.wallet.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.elvo.wallet.client.AgentServiceClient;
import com.elvo.wallet.client.IdentityServiceClient;
import com.elvo.wallet.entity.Transaction;
import com.elvo.wallet.entity.Wallet;
import com.elvo.wallet.messaging.producer.WalletEventPublisher;
import com.elvo.wallet.repository.TransactionRepository;
import com.elvo.wallet.repository.WalletRepository;
import com.elvo.wallet.security.MobileMoneyCallbackSecurityService;
import com.elvo.wallet.security.StepUpAuthenticationService;
import com.elvo.wallet.security.TransactionSigningChallengeService;
import com.elvo.wallet.security.WalletFieldEncryptionService;
import com.elvo.wallet.security.WalletFraudVelocityService;
import com.elvo.wallet.service.EacReplayProtectionService;
import com.elvo.wallet.service.TransactionLifecycleService;
import com.elvo.wallet.service.model.DepositCommand;
import com.elvo.wallet.service.model.WalletChannel;
import com.elvo.wallet.service.model.WalletFlowResult;
import com.elvo.wallet.service.model.WithdrawalCommand;
import com.elvo.wallet.service.model.WithdrawalMode;
import com.elvo.wallet.service.orchestration.WalletSagaOrchestrator;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChaosResilienceSuiteTest {

    @Mock private WalletRepository walletRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private IdentityServiceClient identityServiceClient;
    @Mock private AgentServiceClient agentServiceClient;
    @Mock private WalletIdempotencyService idempotencyService;
    @Mock private WalletLedgerIntegrationService ledgerIntegrationService;
    @Mock private MobileMoneyCallbackSecurityService callbackSecurityService;
    @Mock private MobileCallbackReconciliationService callbackReconciliationService;
    @Mock private WalletLimitEnforcementService limitEnforcementService;
    @Mock private WalletSagaOrchestrator sagaOrchestrator;
    @Mock private WalletEventPublisher eventPublisher;
    @Mock private WalletFieldEncryptionService fieldEncryptionService;
    @Mock private TransactionLifecycleService transactionLifecycleService;

    @Mock private EacReplayProtectionService eacReplayProtectionService;
    @Mock private StepUpAuthenticationService stepUpAuthenticationService;
    @Mock private TransactionSigningChallengeService transactionSigningChallengeService;
    @Mock private WalletFraudVelocityService fraudVelocityService;

    private Wallet wallet;

    @BeforeEach
    void setUp() {
        wallet = new Wallet();
        wallet.setUserId(UUID.randomUUID());
        wallet.setBalance(new BigDecimal("100.00"));
        wallet.setReservedBalance(BigDecimal.ZERO);
        wallet.setStatus(Wallet.WalletStatus.ACTIVE);
        org.springframework.test.util.ReflectionTestUtils.setField(wallet, "id", UUID.randomUUID());

        lenient().when(identityServiceClient.isUserActive(any())).thenReturn(true);
        lenient().when(identityServiceClient.verifyEsp(any(), any())).thenReturn(true);
        lenient().when(identityServiceClient.verifyEac(any(), any())).thenReturn(true);
        when(idempotencyService.get(anyString(), anyString(), anyString(), anyString())).thenReturn(Optional.empty());
        when(fieldEncryptionService.encrypt(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionLifecycleService.initialize(any(), any(), any(), any())).thenAnswer(invocation -> {
            Transaction transaction = invocation.getArgument(0);
            transaction.setStatus(Transaction.TransactionStatus.INITIATED);
            if (transaction.getId() == null) {
                org.springframework.test.util.ReflectionTestUtils.setField(transaction, "id", UUID.randomUUID());
            }
            return transaction;
        });
        when(transactionLifecycleService.transition(any(), any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            Transaction transaction = invocation.getArgument(0);
            transaction.setStatus(invocation.getArgument(1));
            return transaction;
        });
        when(transactionLifecycleService.expire(any(), any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(limitEnforcementService.validate(any(), any(), any())).thenReturn(true);
    }

    @Test
    void chaosAuthAndCallbackFailureShouldAbortMobileDeposit() {
        when(walletRepository.findByIdForUpdate(any())).thenReturn(Optional.of(wallet));
        when(transactionRepository.existsByReference(anyString())).thenReturn(false);
        when(callbackSecurityService.isAuthenticatedCallback(anyString(), anyString(), any(), anyString())).thenReturn(false);

        DefaultDepositFlowService service = new DefaultDepositFlowService(
                walletRepository,
                transactionRepository,
                identityServiceClient,
                agentServiceClient,
                idempotencyService,
                ledgerIntegrationService,
                callbackSecurityService,
                callbackReconciliationService,
                limitEnforcementService,
                sagaOrchestrator,
                eventPublisher,
                fieldEncryptionService,
                transactionLifecycleService);

        WalletFlowResult result = service.process(new DepositCommand(
                wallet.getId(),
                wallet.getUserId(),
                new BigDecimal("10.00"),
                WalletChannel.MOBILE,
                "idem-chaos-auth",
                "ref-chaos-auth",
                true,
                "cb-chaos",
                "invalid-signature",
                System.currentTimeMillis() / 1000,
                "127.0.0.1"));

        assertThat(result.success()).isFalse();
        assertThat(result.message()).containsIgnoringCase("authentication failed");
    }

    @Test
    void chaosLedgerAndDatabaseFailureShouldFailGracefully() {
        when(walletRepository.findByIdForUpdate(any())).thenReturn(Optional.of(wallet));
        when(transactionRepository.existsByReference(anyString())).thenReturn(false);
        when(callbackSecurityService.isAuthenticatedCallback(anyString(), anyString(), any(), anyString())).thenReturn(true);
        when(callbackReconciliationService.consumeOnce(anyString(), any())).thenReturn(true);
        when(fraudVelocityService.isSuspicious(any(), any(), any())).thenReturn(false);
        when(stepUpAuthenticationService.requiresStepUpForWithdrawal(any(), any())).thenReturn(false);
        when(eacReplayProtectionService.validateAndConsume(any(), any(), any())).thenReturn(EacReplayProtectionService.EacValidationResult.allow());
        doThrow(new IllegalStateException("ledger unavailable"))
            .when(ledgerIntegrationService)
            .recordDoubleEntry(anyString(), any(), any(), anyString());

        DefaultWithdrawalFlowService service = new DefaultWithdrawalFlowService(
                walletRepository,
                transactionRepository,
                identityServiceClient,
                idempotencyService,
                ledgerIntegrationService,
                limitEnforcementService,
                sagaOrchestrator,
                eventPublisher,
                eacReplayProtectionService,
                stepUpAuthenticationService,
                transactionSigningChallengeService,
                fraudVelocityService,
                fieldEncryptionService,
                transactionLifecycleService);

        WalletFlowResult result = service.process(new WithdrawalCommand(
                wallet.getId(),
                wallet.getUserId(),
                new BigDecimal("10.00"),
                WithdrawalMode.REGISTERED_NUMBER,
                "+1234567890",
                "esp-1234",
                "eac-1234",
                "idem-chaos-ledger",
                "ref-chaos-ledger",
                null,
                null,
                null));

        assertThat(result.success()).isFalse();
        assertThat(result.message()).containsIgnoringCase("failed");

        when(walletRepository.findByIdForUpdate(any())).thenReturn(Optional.empty());
        WalletFlowResult databaseFailureResult = service.process(new WithdrawalCommand(
                wallet.getId(),
                wallet.getUserId(),
                new BigDecimal("10.00"),
                WithdrawalMode.REGISTERED_NUMBER,
                "+1234567890",
                "esp-1234",
                "eac-1234",
                "idem-chaos-db",
                "ref-chaos-db",
                null,
                null,
                null));

        assertThat(databaseFailureResult.success()).isFalse();
            assertThat(databaseFailureResult.message()).containsIgnoringCase("wallet not found");
    }
}
