package com.elvo.wallet.service.impl;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InOrder;
import org.mockito.Mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.elvo.wallet.client.AgentServiceClient;
import com.elvo.wallet.client.IdentityServiceClient;
import com.elvo.wallet.entity.Etc;
import com.elvo.wallet.entity.Reservation;
import com.elvo.wallet.entity.Transaction;
import com.elvo.wallet.entity.Wallet;
import com.elvo.wallet.messaging.producer.WalletEventPublisher;
import com.elvo.wallet.repository.EtcRepository;
import com.elvo.wallet.repository.ReservationRepository;
import com.elvo.wallet.repository.TransactionRepository;
import com.elvo.wallet.repository.WalletRepository;
import com.elvo.wallet.security.EtcBruteForceProtectionService;
import com.elvo.wallet.security.EtcCodePolicyService;
import com.elvo.wallet.security.EtcCodeSecurityService;
import com.elvo.wallet.security.MobileMoneyCallbackSecurityService;
import com.elvo.wallet.security.StepUpAuthenticationService;
import com.elvo.wallet.security.TransactionSigningChallengeService;
import com.elvo.wallet.security.WalletFieldEncryptionService;
import com.elvo.wallet.security.WalletFraudVelocityService;
import com.elvo.wallet.service.EacReplayProtectionService;
import com.elvo.wallet.service.TransactionLifecycleService;
import com.elvo.wallet.service.model.DepositCommand;
import com.elvo.wallet.service.model.EtcCommand;
import com.elvo.wallet.service.model.ReservationCommand;
import com.elvo.wallet.service.model.TransferCommand;
import com.elvo.wallet.service.model.WalletChannel;
import com.elvo.wallet.service.model.WalletFlowResult;
import com.elvo.wallet.service.model.WithdrawalCommand;
import com.elvo.wallet.service.model.WithdrawalMode;
import com.elvo.wallet.service.orchestration.WalletSagaOrchestrator;

@ExtendWith(MockitoExtension.class)
class WalletFlowServiceTests {

    @Mock private WalletRepository walletRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private EtcRepository etcRepository;
    @Mock private IdentityServiceClient identityServiceClient;
    @Mock private AgentServiceClient agentServiceClient;
    @Mock private WalletIdempotencyService idempotencyService;
    @Mock private WalletLedgerIntegrationService ledgerIntegrationService;
    @Mock private MobileCallbackReconciliationService callbackReconciliationService;
        @Mock private MobileMoneyCallbackSecurityService mobileMoneyCallbackSecurityService;
    @Mock private WalletLimitEnforcementService limitEnforcementService;
    @Mock private WalletEventPublisher eventPublisher;
    @Mock private WalletSagaOrchestrator sagaOrchestrator;
    @Mock private EacReplayProtectionService eacReplayProtectionService;
    @Mock private EtcCodeSecurityService etcCodeSecurityService;
    @Mock private EtcCodePolicyService etcCodePolicyService;
    @Mock private EtcBruteForceProtectionService etcBruteForceProtectionService;
    @Mock private StepUpAuthenticationService stepUpAuthenticationService;
    @Mock private TransactionSigningChallengeService transactionSigningChallengeService;
        @Mock private WalletFraudVelocityService fraudVelocityService;
        @Mock private WalletFieldEncryptionService fieldEncryptionService;
        @Mock private TransactionLifecycleService transactionLifecycleService;

    private Wallet wallet;

    @BeforeEach
    void setUp() {
        wallet = new Wallet();
        ReflectionTestUtils.setField(wallet, "id", UUID.randomUUID());
        wallet.setUserId(UUID.randomUUID());
        wallet.setBalance(new BigDecimal("100.00"));
        wallet.setReservedBalance(BigDecimal.ZERO);
        wallet.setStatus(Wallet.WalletStatus.ACTIVE);

        lenient().when(fieldEncryptionService.encrypt(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(fieldEncryptionService.decrypt(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(transactionLifecycleService.initialize(any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    Transaction transaction = invocation.getArgument(0);
                    transaction.setStatus(Transaction.TransactionStatus.INITIATED);
                    try {
                        java.lang.reflect.Field idField = Transaction.class.getDeclaredField("id");
                        idField.setAccessible(true);
                        if (idField.get(transaction) == null) {
                            idField.set(transaction, UUID.randomUUID());
                        }
                    } catch (ReflectiveOperationException ignored) {
                    }
                    return transaction;
                });
        lenient().when(transactionLifecycleService.transition(any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    Transaction transaction = invocation.getArgument(0);
                    Transaction.TransactionStatus nextStatus = invocation.getArgument(1);
                    transaction.setStatus(nextStatus);
                    return transaction;
                });
        lenient().when(transactionLifecycleService.expire(any(), anyString(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(transactionRepository.findByExternalReferenceAndStatusInForUpdate(anyString(), any()))
                .thenReturn(java.util.List.of());
        lenient().when(idempotencyService.get(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
    }

    @Test
    void depositShouldPersistTransactionAndPublishEvent() {
        UUID walletId = UUID.randomUUID();
        wallet.setBalance(new BigDecimal("100.00"));
        lenient().when(idempotencyService.get(anyString())).thenReturn(Optional.empty());
        when(walletRepository.findByIdForUpdate(walletId)).thenReturn(Optional.of(wallet));
        when(limitEnforcementService.validate(any(), any(), any())).thenReturn(true);
        when(transactionRepository.existsByReference(anyString())).thenReturn(false);
        when(mobileMoneyCallbackSecurityService.isAuthenticatedCallback(anyString(), anyString(), any(), anyString())).thenReturn(true);
        when(callbackReconciliationService.consumeOnce(anyString(), any())).thenReturn(true);
                lenient().when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction transaction = invocation.getArgument(0);
            try {
                java.lang.reflect.Field idField = Transaction.class.getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(transaction, UUID.randomUUID());
            } catch (ReflectiveOperationException ignored) {
            }
            return transaction;
        });

        DefaultDepositFlowService service = new DefaultDepositFlowService(
                walletRepository,
                transactionRepository,
                identityServiceClient,
                agentServiceClient,
                idempotencyService,
                ledgerIntegrationService,
                mobileMoneyCallbackSecurityService,
                callbackReconciliationService,
                limitEnforcementService,
                sagaOrchestrator,
                eventPublisher,
                fieldEncryptionService,
                transactionLifecycleService);

        WalletFlowResult result = service.process(new DepositCommand(
                walletId,
                wallet.getUserId(),
                new BigDecimal("20.00"),
                WalletChannel.MOBILE,
                "idem-1",
                "ref-1",
                true,
                "cb-1",
                "signed-callback",
                System.currentTimeMillis() / 1000,
                "127.0.0.1"));

        assertThat(result.success()).isTrue();
        verify(eventPublisher).publish(eq("wallet.deposit.completed"), any());
        verify(ledgerIntegrationService).recordDoubleEntry(anyString(), any(), any(), anyString());
        verify(callbackReconciliationService).consumeOnce(eq("cb-1"), any());
        verify(callbackReconciliationService).scheduleRetry("cb-1", wallet.getId(), new BigDecimal("20.00"));
        verify(callbackReconciliationService).markReconciled("cb-1");
    }

        @Test
        void mobileDepositShouldFollowLifecycleStateFlow() {
                UUID walletId = UUID.randomUUID();
                wallet.setBalance(new BigDecimal("100.00"));
                lenient().when(idempotencyService.get(anyString())).thenReturn(Optional.empty());
                when(walletRepository.findByIdForUpdate(walletId)).thenReturn(Optional.of(wallet));
                when(limitEnforcementService.validate(any(), any(), any())).thenReturn(true);
                when(transactionRepository.existsByReference(anyString())).thenReturn(false);
                when(mobileMoneyCallbackSecurityService.isAuthenticatedCallback(anyString(), anyString(), any(), anyString())).thenReturn(true);
                when(callbackReconciliationService.consumeOnce(anyString(), any())).thenReturn(true);
                lenient().when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

                DefaultDepositFlowService service = new DefaultDepositFlowService(
                                walletRepository,
                                transactionRepository,
                                identityServiceClient,
                                agentServiceClient,
                                idempotencyService,
                                ledgerIntegrationService,
                                mobileMoneyCallbackSecurityService,
                                callbackReconciliationService,
                                limitEnforcementService,
                                sagaOrchestrator,
                                eventPublisher,
                                fieldEncryptionService,
                                transactionLifecycleService);

                WalletFlowResult result = service.process(new DepositCommand(
                                walletId,
                                wallet.getUserId(),
                                new BigDecimal("20.00"),
                                WalletChannel.MOBILE,
                                "idem-mobile-flow",
                                "ref-mobile-flow",
                                true,
                                "cb-mobile-flow",
                                "signed-callback",
                                System.currentTimeMillis() / 1000,
                                "127.0.0.1"));

                assertThat(result.success()).isTrue();

                InOrder order = inOrder(transactionLifecycleService);
                order.verify(transactionLifecycleService).initialize(any(Transaction.class), anyString(), any(), any());
                order.verify(transactionLifecycleService).transition(any(Transaction.class), eq(Transaction.TransactionStatus.PENDING), anyString(), any(), any(), any());
                order.verify(transactionLifecycleService).transition(any(Transaction.class), eq(Transaction.TransactionStatus.AWAITING_CONFIRMATION), anyString(), any(), any(), any());
                order.verify(transactionLifecycleService).transition(any(Transaction.class), eq(Transaction.TransactionStatus.RETRYING), anyString(), any(), any(), any());
                order.verify(transactionLifecycleService).transition(any(Transaction.class), eq(Transaction.TransactionStatus.PROCESSING), anyString(), any(), any(), any());
                order.verify(transactionLifecycleService).transition(any(Transaction.class), eq(Transaction.TransactionStatus.COMPLETED), anyString(), any(), any(), any());
        }

    @Test
    void withdrawalShouldFailWhenEspVerificationFails() {
        lenient().when(idempotencyService.get(anyString())).thenReturn(Optional.empty());
        when(identityServiceClient.verifyEsp(any(), any())).thenReturn(false);

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
                UUID.randomUUID(),
                wallet.getUserId(),
                new BigDecimal("10.00"),
                WithdrawalMode.DEVICE_FREE,
                "0900000000",
                "esp",
                "eac",
                "idem-2",
                "ref-2",
                null,
                null,
                null));

        assertThat(result.success()).isFalse();
        verify(walletRepository, never()).findByIdForUpdate(any());
        verify(eventPublisher).publish(eq("wallet.withdrawal.failed"), any());
    }

    @Test
    void externalWithdrawalShouldFailWhenEacVerificationFails() {
        lenient().when(idempotencyService.get(anyString())).thenReturn(Optional.empty());
        when(identityServiceClient.verifyEsp(any(), any())).thenReturn(true);
        when(identityServiceClient.verifyEac(any(), any())).thenReturn(false);
                when(walletRepository.findByIdForUpdate(any())).thenReturn(Optional.of(wallet));
                when(limitEnforcementService.validate(any(), any(), any())).thenReturn(true);

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
                UUID.randomUUID(),
                wallet.getUserId(),
                new BigDecimal("10.00"),
                WithdrawalMode.OTHER_NUMBER,
                "0900000000",
                "esp",
                "eac",
                "idem-eac-fail",
                "ref-eac-fail",
                null,
                null,
                null));

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("ESP/EAC verification failed");
                verify(walletRepository).findByIdForUpdate(any());
    }

        @Test
        void registeredWithdrawalShouldFollowLifecycleStateFlow() {
                UUID walletId = UUID.randomUUID();
                wallet.setBalance(new BigDecimal("100.00"));
                lenient().when(idempotencyService.get(anyString())).thenReturn(Optional.empty());
                when(fraudVelocityService.isSuspicious(any(), any(), any())).thenReturn(false);
                when(identityServiceClient.verifyEsp(any(), any())).thenReturn(true);
                when(identityServiceClient.verifyEac(any(), any())).thenReturn(true);
                when(stepUpAuthenticationService.requiresStepUpForWithdrawal(any(), any())).thenReturn(false);
                when(eacReplayProtectionService.validateAndConsume(any(), anyString(), anyString()))
                                .thenReturn(EacReplayProtectionService.EacValidationResult.allow());
                when(walletRepository.findByIdForUpdate(walletId)).thenReturn(Optional.of(wallet));
                when(limitEnforcementService.validate(any(), any(), any())).thenReturn(true);

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
                                walletId,
                                wallet.getUserId(),
                                new BigDecimal("25.00"),
                                WithdrawalMode.REGISTERED_NUMBER,
                                null,
                                "esp",
                                "eac",
                                "idem-reg-withdraw",
                                "ref-reg-withdraw",
                                null,
                                null,
                                null));

                assertThat(result.success()).isTrue();

                InOrder order = inOrder(transactionLifecycleService);
                order.verify(transactionLifecycleService).initialize(any(Transaction.class), anyString(), any(), any());
                order.verify(transactionLifecycleService).transition(any(Transaction.class), eq(Transaction.TransactionStatus.PENDING), anyString(), any(), any(), any());
                order.verify(transactionLifecycleService).transition(any(Transaction.class), eq(Transaction.TransactionStatus.PROCESSING), anyString(), any(), any(), any());
                order.verify(transactionLifecycleService).transition(any(Transaction.class), eq(Transaction.TransactionStatus.COMPLETED), anyString(), any(), any(), any());
        }

        @Test
        void externalWithdrawalShouldFollowLifecycleStateFlow() {
                UUID walletId = UUID.randomUUID();
                wallet.setBalance(new BigDecimal("100.00"));
                lenient().when(idempotencyService.get(anyString())).thenReturn(Optional.empty());
                when(fraudVelocityService.isSuspicious(any(), any(), any())).thenReturn(false);
                when(identityServiceClient.verifyEsp(any(), any())).thenReturn(true);
                when(identityServiceClient.verifyEac(any(), any())).thenReturn(true);
                when(stepUpAuthenticationService.requiresStepUpForWithdrawal(any(), any())).thenReturn(false);
                when(eacReplayProtectionService.validateAndConsume(any(), anyString(), anyString()))
                                .thenReturn(EacReplayProtectionService.EacValidationResult.allow());
                when(walletRepository.findByIdForUpdate(walletId)).thenReturn(Optional.of(wallet));
                when(limitEnforcementService.validate(any(), any(), any())).thenReturn(true);

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
                                walletId,
                                wallet.getUserId(),
                                new BigDecimal("30.00"),
                                WithdrawalMode.OTHER_NUMBER,
                                "0900000000",
                                "esp",
                                "eac",
                                "idem-ext-withdraw",
                                "ref-ext-withdraw",
                                null,
                                null,
                                null));

                assertThat(result.success()).isTrue();

                InOrder order = inOrder(transactionLifecycleService);
                order.verify(transactionLifecycleService).initialize(any(Transaction.class), anyString(), any(), any());
                order.verify(transactionLifecycleService).transition(any(Transaction.class), eq(Transaction.TransactionStatus.PENDING), anyString(), any(), any(), any());
                order.verify(transactionLifecycleService).transition(any(Transaction.class), eq(Transaction.TransactionStatus.AWAITING_CONFIRMATION), anyString(), any(), any(), any());
                order.verify(transactionLifecycleService).transition(any(Transaction.class), eq(Transaction.TransactionStatus.PROCESSING), anyString(), any(), any(), any());
                order.verify(transactionLifecycleService).transition(any(Transaction.class), eq(Transaction.TransactionStatus.COMPLETED), anyString(), any(), any(), any());
        }

        @Test
        void externalWithdrawalShouldExpireWhenEacConfirmationTimesOut() {
                UUID walletId = UUID.randomUUID();
                wallet.setBalance(new BigDecimal("100.00"));
                lenient().when(idempotencyService.get(anyString())).thenReturn(Optional.empty());
                when(fraudVelocityService.isSuspicious(any(), any(), any())).thenReturn(false);
                when(identityServiceClient.verifyEsp(any(), any())).thenReturn(true);
                when(identityServiceClient.verifyEac(any(), any())).thenReturn(true);
                when(stepUpAuthenticationService.requiresStepUpForWithdrawal(any(), any())).thenReturn(false);
                when(eacReplayProtectionService.validateAndConsume(any(), anyString(), anyString()))
                                .thenReturn(EacReplayProtectionService.EacValidationResult.deny("EAC expired"));
                when(walletRepository.findByIdForUpdate(walletId)).thenReturn(Optional.of(wallet));
                when(limitEnforcementService.validate(any(), any(), any())).thenReturn(true);

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
                                walletId,
                                wallet.getUserId(),
                                new BigDecimal("30.00"),
                                WithdrawalMode.OTHER_NUMBER,
                                "0900000000",
                                "esp",
                                "eac",
                                "idem-ext-withdraw-expired",
                                "ref-ext-withdraw-expired",
                                null,
                                null,
                                null));

                assertThat(result.success()).isFalse();
                assertThat(result.message()).isEqualTo("Withdrawal confirmation expired");
                verify(transactionLifecycleService).transition(any(Transaction.class), eq(Transaction.TransactionStatus.EXPIRED), anyString(), any(), eq("WITHDRAWAL_EAC_EXPIRED"), eq("EAC expired"));
        }

            @Test
            void externalWithdrawalShouldMoveToRetryingOnTemporaryPostingFailure() {
                UUID walletId = UUID.randomUUID();
                wallet.setBalance(new BigDecimal("100.00"));
                lenient().when(idempotencyService.get(anyString())).thenReturn(Optional.empty());
                when(fraudVelocityService.isSuspicious(any(), any(), any())).thenReturn(false);
                when(identityServiceClient.verifyEsp(any(), any())).thenReturn(true);
                when(identityServiceClient.verifyEac(any(), any())).thenReturn(true);
                when(stepUpAuthenticationService.requiresStepUpForWithdrawal(any(), any())).thenReturn(false);
                when(eacReplayProtectionService.validateAndConsume(any(), anyString(), anyString()))
                        .thenReturn(EacReplayProtectionService.EacValidationResult.allow());
                when(walletRepository.findByIdForUpdate(walletId)).thenReturn(Optional.of(wallet));
                when(limitEnforcementService.validate(any(), any(), any())).thenReturn(true);
                doThrow(new RuntimeException("temporary telecom timeout"))
                        .when(ledgerIntegrationService)
                        .recordDoubleEntry(eq("withdrawal"), any(), eq(new BigDecimal("30.00")), anyString());

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
                        walletId,
                        wallet.getUserId(),
                        new BigDecimal("30.00"),
                        WithdrawalMode.OTHER_NUMBER,
                        "0900000000",
                        "esp",
                        "eac",
                        "idem-ext-withdraw-retry",
                        "ref-ext-withdraw-retry",
                        null,
                        null,
                        null));

                assertThat(result.success()).isFalse();
                assertThat(result.message()).isEqualTo("Withdrawal processing failed");
                verify(transactionLifecycleService).transition(any(Transaction.class), eq(Transaction.TransactionStatus.RETRYING), anyString(), any(), eq("WITHDRAWAL_RETRYING"), eq("temporary telecom timeout"));
            }

        @Test
        void deviceFreeWithdrawalShouldExpireWhenConfirmationTimesOut() {
                UUID walletId = UUID.randomUUID();
                wallet.setBalance(new BigDecimal("100.00"));
                lenient().when(idempotencyService.get(anyString())).thenReturn(Optional.empty());
                when(fraudVelocityService.isSuspicious(any(), any(), any())).thenReturn(false);
                when(identityServiceClient.verifyEsp(any(), any())).thenReturn(true);
                when(identityServiceClient.verifyEac(any(), any())).thenReturn(true);
                when(stepUpAuthenticationService.requiresStepUpForWithdrawal(any(), any())).thenReturn(false);
                when(eacReplayProtectionService.validateAndConsume(any(), anyString(), anyString()))
                                .thenReturn(EacReplayProtectionService.EacValidationResult.deny("EAC expired"));
                when(walletRepository.findByIdForUpdate(walletId)).thenReturn(Optional.of(wallet));
                when(limitEnforcementService.validate(any(), any(), any())).thenReturn(true);

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
                                walletId,
                                wallet.getUserId(),
                                new BigDecimal("25.00"),
                                WithdrawalMode.DEVICE_FREE,
                                "0900000000",
                                "esp",
                                "eac",
                                "idem-device-expired",
                                "ref-device-expired",
                                null,
                                null,
                                null));

                assertThat(result.success()).isFalse();
                assertThat(result.message()).isEqualTo("Device-free withdrawal expired");
                verify(transactionLifecycleService).transition(any(Transaction.class), eq(Transaction.TransactionStatus.EXPIRED), anyString(), any(), eq("WITHDRAWAL_DEVICE_EXPIRED"), eq("EAC expired"));
        }

            @Test
            void deviceFreeWithdrawalShouldTransitionToReversedWhenPostingFails() {
                UUID walletId = UUID.randomUUID();
                wallet.setBalance(new BigDecimal("100.00"));
                lenient().when(idempotencyService.get(anyString())).thenReturn(Optional.empty());
                when(fraudVelocityService.isSuspicious(any(), any(), any())).thenReturn(false);
                when(identityServiceClient.verifyEsp(any(), any())).thenReturn(true);
                when(identityServiceClient.verifyEac(any(), any())).thenReturn(true);
                when(stepUpAuthenticationService.requiresStepUpForWithdrawal(any(), any())).thenReturn(false);
                when(eacReplayProtectionService.validateAndConsume(any(), anyString(), anyString()))
                        .thenReturn(EacReplayProtectionService.EacValidationResult.allow());
                when(walletRepository.findByIdForUpdate(walletId)).thenReturn(Optional.of(wallet));
                when(limitEnforcementService.validate(any(), any(), any())).thenReturn(true);
                doThrow(new RuntimeException("payout failed"))
                        .when(ledgerIntegrationService)
                        .recordDoubleEntry(eq("withdrawal"), any(), eq(new BigDecimal("25.00")), anyString());

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
                        walletId,
                        wallet.getUserId(),
                        new BigDecimal("25.00"),
                        WithdrawalMode.DEVICE_FREE,
                        "0900000000",
                        "esp",
                        "eac",
                        "idem-device-reversed",
                        "ref-device-reversed",
                        null,
                        null,
                        null));

                assertThat(result.success()).isFalse();
                assertThat(result.message()).isEqualTo("Device-free withdrawal reversed");
                assertThat(wallet.getBalance()).isEqualByComparingTo(new BigDecimal("100.00"));
                assertThat(wallet.getReservedBalance()).isEqualByComparingTo(BigDecimal.ZERO);
                verify(transactionLifecycleService).transition(any(Transaction.class), eq(Transaction.TransactionStatus.REVERSED), anyString(), any(), eq("WITHDRAWAL_REVERSED"), eq("payout failed"));
            }

    @Test
    void withdrawalShouldFailWhenEacReplayDetected() {
        lenient().when(idempotencyService.get(anyString())).thenReturn(Optional.empty());
        when(identityServiceClient.verifyEsp(any(), any())).thenReturn(true);
        when(identityServiceClient.verifyEac(any(), any())).thenReturn(true);
        when(stepUpAuthenticationService.requiresStepUpForWithdrawal(any(), any())).thenReturn(false);
        when(eacReplayProtectionService.validateAndConsume(any(), anyString(), anyString()))
                .thenReturn(EacReplayProtectionService.EacValidationResult.deny("EAC replay detected"));

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
                UUID.randomUUID(),
                wallet.getUserId(),
                new BigDecimal("10.00"),
                WithdrawalMode.REGISTERED_NUMBER,
                null,
                "esp",
                "eac",
                "idem-22",
                "ref-22",
                null,
                null,
                null));

        assertThat(result.success()).isFalse();
        assertThat(result.message()).isEqualTo("EAC replay detected");
        verify(walletRepository, never()).findByIdForUpdate(any());
    }

        @Test
        void deviceFreeWithdrawalShouldFollowLifecycleStateFlow() {
                UUID walletId = UUID.randomUUID();
                wallet.setBalance(new BigDecimal("100.00"));
                lenient().when(idempotencyService.get(anyString())).thenReturn(Optional.empty());
                when(fraudVelocityService.isSuspicious(any(), any(), any())).thenReturn(false);
                when(identityServiceClient.verifyEsp(any(), any())).thenReturn(true);
                when(identityServiceClient.verifyEac(any(), any())).thenReturn(true);
                when(stepUpAuthenticationService.requiresStepUpForWithdrawal(any(), any())).thenReturn(false);
                when(eacReplayProtectionService.validateAndConsume(any(), anyString(), anyString()))
                                .thenReturn(EacReplayProtectionService.EacValidationResult.allow());
                when(walletRepository.findByIdForUpdate(walletId)).thenReturn(Optional.of(wallet));
                when(limitEnforcementService.validate(any(), any(), any())).thenReturn(true);

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
                                walletId,
                                wallet.getUserId(),
                                new BigDecimal("20.00"),
                                WithdrawalMode.DEVICE_FREE,
                                "0900000000",
                                "esp",
                                "eac",
                                "idem-device-withdraw",
                                "ref-device-withdraw",
                                null,
                                null,
                                null));

                assertThat(result.success()).isTrue();

                InOrder order = inOrder(transactionLifecycleService);
                order.verify(transactionLifecycleService).initialize(any(Transaction.class), anyString(), any(), any());
                order.verify(transactionLifecycleService).transition(any(Transaction.class), eq(Transaction.TransactionStatus.AWAITING_CONFIRMATION), anyString(), any(), any(), any());
                order.verify(transactionLifecycleService).transition(any(Transaction.class), eq(Transaction.TransactionStatus.RESERVED), anyString(), any(), any(), any());
                order.verify(transactionLifecycleService).transition(any(Transaction.class), eq(Transaction.TransactionStatus.PROCESSING), anyString(), any(), any(), any());
                order.verify(transactionLifecycleService).transition(any(Transaction.class), eq(Transaction.TransactionStatus.COMPLETED), anyString(), any(), any(), any());
        }

    @Test
    void transferShouldRejectSelfTransfer() {
        DefaultTransferFlowService service = new DefaultTransferFlowService(
                walletRepository,
                transactionRepository,
                idempotencyService,
                ledgerIntegrationService,
                limitEnforcementService,
                sagaOrchestrator,
                eventPublisher,
                stepUpAuthenticationService,
                transactionSigningChallengeService,
                fraudVelocityService,
                fieldEncryptionService,
                transactionLifecycleService);

        UUID walletId = UUID.randomUUID();
        WalletFlowResult result = service.process(new TransferCommand(
                walletId,
                walletId,
                wallet.getUserId(),
                new BigDecimal("10.00"),
                "idem-3",
                "ref-3",
                null,
                null,
                null));

        assertThat(result.success()).isFalse();
        verify(walletRepository, never()).findByIdForUpdate(any());
    }

        @Test
        void transferShouldFollowLifecycleStateFlow() {
                Wallet sourceWallet = new Wallet();
                ReflectionTestUtils.setField(sourceWallet, "id", UUID.randomUUID());
                sourceWallet.setUserId(UUID.randomUUID());
                sourceWallet.setBalance(new BigDecimal("100.00"));
                sourceWallet.setReservedBalance(BigDecimal.ZERO);
                sourceWallet.setStatus(Wallet.WalletStatus.ACTIVE);

                Wallet targetWallet = new Wallet();
                ReflectionTestUtils.setField(targetWallet, "id", UUID.randomUUID());
                targetWallet.setUserId(UUID.randomUUID());
                targetWallet.setBalance(new BigDecimal("50.00"));
                targetWallet.setReservedBalance(BigDecimal.ZERO);
                targetWallet.setStatus(Wallet.WalletStatus.ACTIVE);

                lenient().when(idempotencyService.get(anyString())).thenReturn(Optional.empty());
                when(fraudVelocityService.isSuspicious(any(), any(), any())).thenReturn(false);
                when(stepUpAuthenticationService.requiresStepUpForTransfer(any())).thenReturn(false);
                when(walletRepository.findByIdForUpdate(sourceWallet.getId())).thenReturn(Optional.of(sourceWallet));
                when(walletRepository.findByIdForUpdate(targetWallet.getId())).thenReturn(Optional.of(targetWallet));
                when(limitEnforcementService.validate(any(), any(), any())).thenReturn(true);
                when(transactionRepository.findByExternalReferenceAndStatusInForUpdate(anyString(), any())).thenReturn(java.util.List.of());
                when(transactionRepository.existsByReference(anyString())).thenReturn(false);

                DefaultTransferFlowService service = new DefaultTransferFlowService(
                                walletRepository,
                                transactionRepository,
                                idempotencyService,
                                ledgerIntegrationService,
                                limitEnforcementService,
                                sagaOrchestrator,
                                eventPublisher,
                                stepUpAuthenticationService,
                                transactionSigningChallengeService,
                                fraudVelocityService,
                                fieldEncryptionService,
                                transactionLifecycleService);

                WalletFlowResult result = service.process(new TransferCommand(
                                sourceWallet.getId(),
                                targetWallet.getId(),
                                sourceWallet.getUserId(),
                                new BigDecimal("20.00"),
                                "idem-transfer-flow",
                                "transfer-ref-flow",
                                null,
                                null,
                                null));

                assertThat(result.success()).isTrue();

                verify(transactionLifecycleService, org.mockito.Mockito.times(2)).initialize(any(Transaction.class), anyString(), any(), any());
                verify(transactionLifecycleService, org.mockito.Mockito.times(2)).transition(any(Transaction.class), eq(Transaction.TransactionStatus.PROCESSING), anyString(), any(), any(), any());
                verify(transactionLifecycleService, org.mockito.Mockito.times(2)).transition(any(Transaction.class), eq(Transaction.TransactionStatus.COMPLETED), anyString(), any(), any(), any());
                verify(transactionLifecycleService, never()).transition(any(Transaction.class), eq(Transaction.TransactionStatus.PENDING), anyString(), any(), any(), any());
        }

    @Test
    void withdrawalShouldFailWhenStepUpConfirmationMissingForDeviceFreeFlow() {
        lenient().when(idempotencyService.get(anyString())).thenReturn(Optional.empty());
        when(identityServiceClient.verifyEsp(any(), any())).thenReturn(true);
        when(identityServiceClient.verifyEac(any(), any())).thenReturn(true);
        when(stepUpAuthenticationService.requiresStepUpForWithdrawal(any(), any())).thenReturn(true);
        when(transactionSigningChallengeService.isValidChallenge(any(), any(), anyString(), any(), anyString())).thenReturn(true);
        when(stepUpAuthenticationService.isValidConfirmation(any(), any(), any())).thenReturn(false);

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
                UUID.randomUUID(),
                wallet.getUserId(),
                new BigDecimal("300.00"),
                WithdrawalMode.DEVICE_FREE,
                "0900000000",
                "esp",
                "eac",
                "idem-23",
                "ref-23",
                "PASSWORD",
                "invalid-token",
                "challenge-token"));

        assertThat(result.success()).isFalse();
        assertThat(result.message()).isEqualTo("Step-up authentication required");
        verify(walletRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void transferShouldFailWhenStepUpConfirmationMissingForHighValueTransfer() {
        lenient().when(idempotencyService.get(anyString())).thenReturn(Optional.empty());
        lenient().when(stepUpAuthenticationService.requiresStepUpForTransfer(any())).thenReturn(true);
        lenient().when(transactionSigningChallengeService.isValidChallenge(any(), any(), anyString(), any(), anyString())).thenReturn(true);

        DefaultTransferFlowService service = new DefaultTransferFlowService(
                walletRepository,
                transactionRepository,
                idempotencyService,
                ledgerIntegrationService,
                limitEnforcementService,
                sagaOrchestrator,
                eventPublisher,
                stepUpAuthenticationService,
                transactionSigningChallengeService,
                fraudVelocityService,
                fieldEncryptionService,
                transactionLifecycleService);

        WalletFlowResult result = service.process(new TransferCommand(
                UUID.randomUUID(),
                UUID.randomUUID(),
                wallet.getUserId(),
                new BigDecimal("1000.00"),
                "idem-33",
                "ref-33",
                "PIN",
                "bad-token",
                "challenge-token"));

        assertThat(result.success()).isFalse();
        assertThat(result.message()).isEqualTo("Step-up authentication required");
        verify(walletRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void transferShouldFailWhenTransactionChallengeMissingForHighValueTransfer() {
        lenient().when(idempotencyService.get(anyString())).thenReturn(Optional.empty());
        lenient().when(stepUpAuthenticationService.requiresStepUpForTransfer(any())).thenReturn(true);
        lenient().when(transactionSigningChallengeService.isValidChallenge(any(), any(), anyString(), any(), anyString())).thenReturn(false);

        DefaultTransferFlowService service = new DefaultTransferFlowService(
                walletRepository,
                transactionRepository,
                idempotencyService,
                ledgerIntegrationService,
                limitEnforcementService,
                sagaOrchestrator,
                eventPublisher,
                stepUpAuthenticationService,
                transactionSigningChallengeService,
                fraudVelocityService,
                fieldEncryptionService,
                transactionLifecycleService);

        WalletFlowResult result = service.process(new TransferCommand(
                UUID.randomUUID(),
                UUID.randomUUID(),
                wallet.getUserId(),
                new BigDecimal("1000.00"),
                "idem-34",
                "ref-34",
                "PIN",
                "step-up-token",
                null));

        assertThat(result.success()).isFalse();
        assertThat(result.message()).isEqualTo("Transaction challenge confirmation required");
        verify(walletRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void withdrawalShouldFailWhenTransactionChallengeMissingForHighRiskFlow() {
        lenient().when(idempotencyService.get(anyString())).thenReturn(Optional.empty());
        when(identityServiceClient.verifyEsp(any(), any())).thenReturn(true);
        when(identityServiceClient.verifyEac(any(), any())).thenReturn(true);
        when(stepUpAuthenticationService.requiresStepUpForWithdrawal(any(), any())).thenReturn(true);
        when(transactionSigningChallengeService.isValidChallenge(any(), any(), anyString(), any(), anyString())).thenReturn(false);

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
                UUID.randomUUID(),
                wallet.getUserId(),
                new BigDecimal("300.00"),
                WithdrawalMode.DEVICE_FREE,
                "0900000000",
                "esp",
                "eac",
                "idem-24",
                "ref-24",
                "PASSWORD",
                "step-up-token",
                null));

        assertThat(result.success()).isFalse();
        assertThat(result.message()).isEqualTo("Transaction challenge confirmation required");
        verify(walletRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void transferShouldFailWhenVelocityRiskDetected() {
        lenient().when(idempotencyService.get(anyString())).thenReturn(Optional.empty());
        when(fraudVelocityService.isSuspicious(any(), any(), any())).thenReturn(true);

        DefaultTransferFlowService service = new DefaultTransferFlowService(
                walletRepository,
                transactionRepository,
                idempotencyService,
                ledgerIntegrationService,
                limitEnforcementService,
                sagaOrchestrator,
                eventPublisher,
                stepUpAuthenticationService,
                transactionSigningChallengeService,
                fraudVelocityService,
                fieldEncryptionService,
                transactionLifecycleService);

        WalletFlowResult result = service.process(new TransferCommand(
                UUID.randomUUID(),
                UUID.randomUUID(),
                wallet.getUserId(),
                new BigDecimal("25.00"),
                "idem-velocity-1",
                "ref-velocity-1",
                null,
                null,
                null));

        assertThat(result.success()).isFalse();
        assertThat(result.message()).isEqualTo("Velocity risk detected");
    }

    @Test
    void transferShouldFailWhenAnotherTransferIsProcessing() {
        lenient().when(idempotencyService.get(anyString())).thenReturn(Optional.empty());
        when(fraudVelocityService.isSuspicious(any(), any(), any())).thenReturn(false);
        when(stepUpAuthenticationService.requiresStepUpForTransfer(any())).thenReturn(false);
        when(walletRepository.findByIdForUpdate(any())).thenReturn(Optional.of(wallet));
        when(limitEnforcementService.validate(any(), any(), any())).thenReturn(true);

        Transaction activeTx = new Transaction();
        activeTx.setStatus(Transaction.TransactionStatus.PROCESSING);
        when(transactionRepository.findByExternalReferenceAndStatusInForUpdate(anyString(), any())).thenReturn(java.util.List.of(activeTx));

        DefaultTransferFlowService service = new DefaultTransferFlowService(
                walletRepository,
                transactionRepository,
                idempotencyService,
                ledgerIntegrationService,
                limitEnforcementService,
                sagaOrchestrator,
                eventPublisher,
                stepUpAuthenticationService,
                transactionSigningChallengeService,
                fraudVelocityService,
                fieldEncryptionService,
                transactionLifecycleService);

        WalletFlowResult result = service.process(new TransferCommand(
                UUID.randomUUID(),
                UUID.randomUUID(),
                wallet.getUserId(),
                new BigDecimal("20.00"),
                "idem-transfer-lock",
                "transfer-ref-lock",
                null,
                null,
                null));

        assertThat(result.success()).isFalse();
        assertThat(result.message()).isEqualTo("Transfer is already processing");
    }

        @Test
        void transferShouldStayProcessingUntilBothLedgerEntriesAreRecorded() {
                Wallet sourceWallet = new Wallet();
                ReflectionTestUtils.setField(sourceWallet, "id", UUID.randomUUID());
                sourceWallet.setUserId(UUID.randomUUID());
                sourceWallet.setBalance(new BigDecimal("100.00"));
                sourceWallet.setReservedBalance(BigDecimal.ZERO);
                sourceWallet.setStatus(Wallet.WalletStatus.ACTIVE);

                Wallet targetWallet = new Wallet();
                ReflectionTestUtils.setField(targetWallet, "id", UUID.randomUUID());
                targetWallet.setUserId(UUID.randomUUID());
                targetWallet.setBalance(new BigDecimal("40.00"));
                targetWallet.setReservedBalance(BigDecimal.ZERO);
                targetWallet.setStatus(Wallet.WalletStatus.ACTIVE);

                lenient().when(idempotencyService.get(anyString())).thenReturn(Optional.empty());
                when(fraudVelocityService.isSuspicious(any(), any(), any())).thenReturn(false);
                when(stepUpAuthenticationService.requiresStepUpForTransfer(any())).thenReturn(false);
                when(walletRepository.findByIdForUpdate(sourceWallet.getId())).thenReturn(Optional.of(sourceWallet));
                when(walletRepository.findByIdForUpdate(targetWallet.getId())).thenReturn(Optional.of(targetWallet));
                when(limitEnforcementService.validate(any(), any(), any())).thenReturn(true);
                when(transactionRepository.findByExternalReferenceAndStatusInForUpdate(anyString(), any())).thenReturn(java.util.List.of());
                when(transactionRepository.existsByReference(anyString())).thenReturn(false);

                DefaultTransferFlowService service = new DefaultTransferFlowService(
                                walletRepository,
                                transactionRepository,
                                idempotencyService,
                                ledgerIntegrationService,
                                limitEnforcementService,
                                sagaOrchestrator,
                                eventPublisher,
                                stepUpAuthenticationService,
                                transactionSigningChallengeService,
                                fraudVelocityService,
                                fieldEncryptionService,
                                transactionLifecycleService);

                WalletFlowResult result = service.process(new TransferCommand(
                                sourceWallet.getId(),
                                targetWallet.getId(),
                                sourceWallet.getUserId(),
                                new BigDecimal("15.00"),
                                "idem-transfer-processing-lock",
                                "transfer-processing-lock-ref",
                                null,
                                null,
                                null));

                assertThat(result.success()).isTrue();

                InOrder flowOrder = inOrder(transactionLifecycleService, ledgerIntegrationService);
                flowOrder.verify(transactionLifecycleService, org.mockito.Mockito.times(2))
                        .transition(any(Transaction.class), eq(Transaction.TransactionStatus.PROCESSING), anyString(), any(), any(), any());
                flowOrder.verify(ledgerIntegrationService).recordDoubleEntry(eq("transfer"), eq(sourceWallet.getId()), eq(new BigDecimal("15.00")), anyString());
                flowOrder.verify(ledgerIntegrationService).recordDoubleEntry(eq("transfer"), eq(targetWallet.getId()), eq(new BigDecimal("15.00")), anyString());
                flowOrder.verify(transactionLifecycleService, org.mockito.Mockito.times(2))
                        .transition(any(Transaction.class), eq(Transaction.TransactionStatus.COMPLETED), anyString(), any(), any(), any());
        }

            @Test
            void transferShouldTransitionToReversedWhenPostingFails() {
                Wallet sourceWallet = new Wallet();
                ReflectionTestUtils.setField(sourceWallet, "id", UUID.randomUUID());
                sourceWallet.setUserId(UUID.randomUUID());
                sourceWallet.setBalance(new BigDecimal("100.00"));
                sourceWallet.setReservedBalance(BigDecimal.ZERO);
                sourceWallet.setStatus(Wallet.WalletStatus.ACTIVE);

                Wallet targetWallet = new Wallet();
                ReflectionTestUtils.setField(targetWallet, "id", UUID.randomUUID());
                targetWallet.setUserId(UUID.randomUUID());
                targetWallet.setBalance(new BigDecimal("30.00"));
                targetWallet.setReservedBalance(BigDecimal.ZERO);
                targetWallet.setStatus(Wallet.WalletStatus.ACTIVE);

                lenient().when(idempotencyService.get(anyString())).thenReturn(Optional.empty());
                when(fraudVelocityService.isSuspicious(any(), any(), any())).thenReturn(false);
                when(stepUpAuthenticationService.requiresStepUpForTransfer(any())).thenReturn(false);
                when(walletRepository.findByIdForUpdate(sourceWallet.getId())).thenReturn(Optional.of(sourceWallet));
                when(walletRepository.findByIdForUpdate(targetWallet.getId())).thenReturn(Optional.of(targetWallet));
                when(limitEnforcementService.validate(any(), any(), any())).thenReturn(true);
                when(transactionRepository.findByExternalReferenceAndStatusInForUpdate(anyString(), any())).thenReturn(java.util.List.of());
                when(transactionRepository.existsByReference(anyString())).thenReturn(false);
                doThrow(new RuntimeException("credit post failed"))
                        .when(ledgerIntegrationService)
                        .recordDoubleEntry(eq("transfer"), eq(targetWallet.getId()), eq(new BigDecimal("12.00")), anyString());

                DefaultTransferFlowService service = new DefaultTransferFlowService(
                        walletRepository,
                        transactionRepository,
                        idempotencyService,
                        ledgerIntegrationService,
                        limitEnforcementService,
                        sagaOrchestrator,
                        eventPublisher,
                        stepUpAuthenticationService,
                        transactionSigningChallengeService,
                        fraudVelocityService,
                        fieldEncryptionService,
                        transactionLifecycleService);

                WalletFlowResult result = service.process(new TransferCommand(
                        sourceWallet.getId(),
                        targetWallet.getId(),
                        sourceWallet.getUserId(),
                        new BigDecimal("12.00"),
                        "idem-transfer-reversed",
                        "transfer-reversed-ref",
                        null,
                        null,
                        null));

                assertThat(result.success()).isFalse();
                assertThat(result.message()).isEqualTo("Transfer processing failed");
                verify(transactionLifecycleService, org.mockito.Mockito.times(2))
                        .transition(any(Transaction.class), eq(Transaction.TransactionStatus.REVERSED), anyString(), any(), eq("TRANSFER_REVERSED"), any());
            }

            @Test
            void transferShouldPersistFailureCodeWhenBalanceIsInsufficient() {
                Wallet sourceWallet = new Wallet();
                ReflectionTestUtils.setField(sourceWallet, "id", UUID.randomUUID());
                sourceWallet.setUserId(UUID.randomUUID());
                sourceWallet.setBalance(new BigDecimal("10.00"));
                sourceWallet.setReservedBalance(BigDecimal.ZERO);
                sourceWallet.setStatus(Wallet.WalletStatus.ACTIVE);

                Wallet targetWallet = new Wallet();
                ReflectionTestUtils.setField(targetWallet, "id", UUID.randomUUID());
                targetWallet.setUserId(UUID.randomUUID());
                targetWallet.setBalance(new BigDecimal("80.00"));
                targetWallet.setReservedBalance(BigDecimal.ZERO);
                targetWallet.setStatus(Wallet.WalletStatus.ACTIVE);

                lenient().when(idempotencyService.get(anyString())).thenReturn(Optional.empty());
                when(fraudVelocityService.isSuspicious(any(), any(), any())).thenReturn(false);
                when(stepUpAuthenticationService.requiresStepUpForTransfer(any())).thenReturn(false);
                when(walletRepository.findByIdForUpdate(sourceWallet.getId())).thenReturn(Optional.of(sourceWallet));
                when(walletRepository.findByIdForUpdate(targetWallet.getId())).thenReturn(Optional.of(targetWallet));
                when(limitEnforcementService.validate(any(), any(), any())).thenReturn(true);
                when(transactionRepository.findByExternalReferenceAndStatusInForUpdate(anyString(), any())).thenReturn(java.util.List.of());
                when(transactionRepository.existsByReference(anyString())).thenReturn(false);

                DefaultTransferFlowService service = new DefaultTransferFlowService(
                        walletRepository,
                        transactionRepository,
                        idempotencyService,
                        ledgerIntegrationService,
                        limitEnforcementService,
                        sagaOrchestrator,
                        eventPublisher,
                        stepUpAuthenticationService,
                        transactionSigningChallengeService,
                        fraudVelocityService,
                        fieldEncryptionService,
                        transactionLifecycleService);

                WalletFlowResult result = service.process(new TransferCommand(
                        sourceWallet.getId(),
                        targetWallet.getId(),
                        sourceWallet.getUserId(),
                        new BigDecimal("20.00"),
                        "idem-transfer-insufficient",
                        "transfer-insufficient-ref",
                        null,
                        null,
                        null));

                assertThat(result.success()).isFalse();
                assertThat(result.message()).isEqualTo("Insufficient balance for transfer");
                verify(transactionLifecycleService).transition(any(Transaction.class), eq(Transaction.TransactionStatus.FAILED), anyString(), any(), eq("TRANSFER_INSUFFICIENT_BALANCE"), eq("Insufficient balance for transfer"));
            }

            @Test
            void transferShouldPersistFailureCodeWhenLimitValidationFails() {
                Wallet sourceWallet = new Wallet();
                ReflectionTestUtils.setField(sourceWallet, "id", UUID.randomUUID());
                sourceWallet.setUserId(UUID.randomUUID());
                sourceWallet.setBalance(new BigDecimal("200.00"));
                sourceWallet.setReservedBalance(BigDecimal.ZERO);
                sourceWallet.setStatus(Wallet.WalletStatus.ACTIVE);

                Wallet targetWallet = new Wallet();
                ReflectionTestUtils.setField(targetWallet, "id", UUID.randomUUID());
                targetWallet.setUserId(UUID.randomUUID());
                targetWallet.setBalance(new BigDecimal("40.00"));
                targetWallet.setReservedBalance(BigDecimal.ZERO);
                targetWallet.setStatus(Wallet.WalletStatus.ACTIVE);

                lenient().when(idempotencyService.get(anyString())).thenReturn(Optional.empty());
                when(fraudVelocityService.isSuspicious(any(), any(), any())).thenReturn(false);
                when(stepUpAuthenticationService.requiresStepUpForTransfer(any())).thenReturn(false);
                when(walletRepository.findByIdForUpdate(sourceWallet.getId())).thenReturn(Optional.of(sourceWallet));
                when(walletRepository.findByIdForUpdate(targetWallet.getId())).thenReturn(Optional.of(targetWallet));
                when(limitEnforcementService.validate(any(), any(), any())).thenReturn(false);

                DefaultTransferFlowService service = new DefaultTransferFlowService(
                        walletRepository,
                        transactionRepository,
                        idempotencyService,
                        ledgerIntegrationService,
                        limitEnforcementService,
                        sagaOrchestrator,
                        eventPublisher,
                        stepUpAuthenticationService,
                        transactionSigningChallengeService,
                        fraudVelocityService,
                        fieldEncryptionService,
                        transactionLifecycleService);

                WalletFlowResult result = service.process(new TransferCommand(
                        sourceWallet.getId(),
                        targetWallet.getId(),
                        sourceWallet.getUserId(),
                        new BigDecimal("25.00"),
                        "idem-transfer-limit",
                        "transfer-limit-ref",
                        null,
                        null,
                        null));

                assertThat(result.success()).isFalse();
                assertThat(result.message()).isEqualTo("Transfer limits exceeded");
                verify(transactionLifecycleService).transition(any(Transaction.class), eq(Transaction.TransactionStatus.FAILED), anyString(), any(), eq("TRANSFER_LIMIT_EXCEEDED"), eq("Transfer limits exceeded"));
            }

    @Test
    void mobileDepositShouldFailWhenCallbackReferenceIsMissing() {
        UUID walletId = UUID.randomUUID();
        lenient().when(idempotencyService.get(anyString())).thenReturn(Optional.empty());
        when(walletRepository.findByIdForUpdate(walletId)).thenReturn(Optional.of(wallet));
        when(limitEnforcementService.validate(any(), any(), any())).thenReturn(true);
        when(transactionRepository.existsByReference(anyString())).thenReturn(false);

        DefaultDepositFlowService service = new DefaultDepositFlowService(
                walletRepository,
                transactionRepository,
                identityServiceClient,
                agentServiceClient,
                idempotencyService,
                ledgerIntegrationService,
                mobileMoneyCallbackSecurityService,
                callbackReconciliationService,
                limitEnforcementService,
                sagaOrchestrator,
                eventPublisher,
                fieldEncryptionService,
                transactionLifecycleService);

        WalletFlowResult result = service.process(new DepositCommand(
                walletId,
                wallet.getUserId(),
                new BigDecimal("20.00"),
                WalletChannel.MOBILE,
                "idem-mobile-timeout",
                "dep-timeout",
                true,
                null,
                null,
                null,
                null));

        assertThat(result.success()).isFalse();
        assertThat(result.message()).isEqualTo("Mobile callback confirmation required");
                verify(transactionLifecycleService, never()).transition(any(Transaction.class), eq(Transaction.TransactionStatus.EXPIRED), anyString(), any(), any(), any());
        verify(callbackReconciliationService, never()).scheduleRetry(anyString(), any(), any());
    }

    @Test
    void mobileDepositShouldFailWhenCallbackAuthenticationFails() {
        UUID walletId = UUID.randomUUID();
        lenient().when(idempotencyService.get(anyString())).thenReturn(Optional.empty());
        when(walletRepository.findByIdForUpdate(walletId)).thenReturn(Optional.of(wallet));
        when(limitEnforcementService.validate(any(), any(), any())).thenReturn(true);
        when(transactionRepository.existsByReference(anyString())).thenReturn(false);
        when(mobileMoneyCallbackSecurityService.isAuthenticatedCallback(anyString(), anyString(), any(), anyString())).thenReturn(false);

        DefaultDepositFlowService service = new DefaultDepositFlowService(
                walletRepository,
                transactionRepository,
                identityServiceClient,
                agentServiceClient,
                idempotencyService,
                ledgerIntegrationService,
                mobileMoneyCallbackSecurityService,
                callbackReconciliationService,
                limitEnforcementService,
                sagaOrchestrator,
                eventPublisher,
                fieldEncryptionService,
                transactionLifecycleService);

        WalletFlowResult result = service.process(new DepositCommand(
                walletId,
                wallet.getUserId(),
                new BigDecimal("20.00"),
                WalletChannel.MOBILE,
                "idem-mobile-auth-fail",
                "dep-mobile-auth-fail",
                true,
                "cb-2",
                "invalid-signature",
                System.currentTimeMillis() / 1000,
                "10.0.0.1"));

        assertThat(result.success()).isFalse();
        assertThat(result.message()).isEqualTo("Mobile callback authentication failed");
        verify(callbackReconciliationService, never()).scheduleRetry(anyString(), any(), any());
    }

        @Test
        void mobileDepositShouldFailWhenCallbackReplayDetected() {
                UUID walletId = UUID.randomUUID();
                lenient().when(idempotencyService.get(anyString())).thenReturn(Optional.empty());
                when(walletRepository.findByIdForUpdate(walletId)).thenReturn(Optional.of(wallet));
                when(limitEnforcementService.validate(any(), any(), any())).thenReturn(true);
                when(transactionRepository.existsByReference(anyString())).thenReturn(false);
                when(mobileMoneyCallbackSecurityService.isAuthenticatedCallback(anyString(), anyString(), any(), anyString())).thenReturn(true);
                when(callbackReconciliationService.consumeOnce(anyString(), any())).thenReturn(false);

                DefaultDepositFlowService service = new DefaultDepositFlowService(
                                walletRepository,
                                transactionRepository,
                                identityServiceClient,
                                agentServiceClient,
                                idempotencyService,
                                ledgerIntegrationService,
                                mobileMoneyCallbackSecurityService,
                                callbackReconciliationService,
                                limitEnforcementService,
                                sagaOrchestrator,
                                eventPublisher,
                                fieldEncryptionService,
                                transactionLifecycleService);

                WalletFlowResult result = service.process(new DepositCommand(
                                walletId,
                                wallet.getUserId(),
                                new BigDecimal("20.00"),
                                WalletChannel.MOBILE,
                                "idem-mobile-replay",
                                "dep-mobile-replay",
                                true,
                                "cb-3",
                                "signed-callback",
                                System.currentTimeMillis() / 1000,
                                "10.0.0.2"));

                assertThat(result.success()).isFalse();
                assertThat(result.message()).isEqualTo("Mobile callback replay detected");
                verify(callbackReconciliationService).consumeOnce(eq("cb-3"), any());
                verify(callbackReconciliationService, never()).scheduleRetry(anyString(), any(), any());
        }

            @Test
            void mobileDepositShouldMoveToRetryingWhenCallbackReconciliationFailsTemporarily() {
                UUID walletId = UUID.randomUUID();
                lenient().when(idempotencyService.get(anyString())).thenReturn(Optional.empty());
                when(walletRepository.findByIdForUpdate(walletId)).thenReturn(Optional.of(wallet));
                when(limitEnforcementService.validate(any(), any(), any())).thenReturn(true);
                when(transactionRepository.existsByReference(anyString())).thenReturn(false);
                when(mobileMoneyCallbackSecurityService.isAuthenticatedCallback(anyString(), anyString(), any(), anyString())).thenReturn(true);
                when(callbackReconciliationService.consumeOnce(anyString(), any())).thenReturn(true);
                doThrow(new IllegalStateException("provider temporarily unavailable"))
                        .when(callbackReconciliationService).scheduleRetry(anyString(), any(), any());

                DefaultDepositFlowService service = new DefaultDepositFlowService(
                        walletRepository,
                        transactionRepository,
                        identityServiceClient,
                        agentServiceClient,
                        idempotencyService,
                        ledgerIntegrationService,
                        mobileMoneyCallbackSecurityService,
                        callbackReconciliationService,
                        limitEnforcementService,
                        sagaOrchestrator,
                        eventPublisher,
                        fieldEncryptionService,
                        transactionLifecycleService);

                WalletFlowResult result = service.process(new DepositCommand(
                        walletId,
                        wallet.getUserId(),
                        new BigDecimal("20.00"),
                        WalletChannel.MOBILE,
                        "idem-mobile-retry",
                        "dep-mobile-retry",
                        true,
                        "cb-retry",
                        "signed-callback",
                        System.currentTimeMillis() / 1000,
                        "10.0.0.5"));

                assertThat(result.success()).isFalse();
                assertThat(result.message()).isEqualTo("Mobile callback reconciliation retry scheduled");
                verify(transactionLifecycleService).transition(any(Transaction.class), eq(Transaction.TransactionStatus.RETRYING), anyString(), any(), any(), any());
            }

        @Test
        void mobileDepositShouldExpireWhenLifecycleMarksTransactionExpired() {
                UUID walletId = UUID.randomUUID();
                lenient().when(idempotencyService.get(anyString())).thenReturn(Optional.empty());
                when(walletRepository.findByIdForUpdate(walletId)).thenReturn(Optional.of(wallet));
                when(limitEnforcementService.validate(any(), any(), any())).thenReturn(true);
                when(transactionRepository.existsByReference(anyString())).thenReturn(false);
                lenient().when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));
                when(transactionLifecycleService.expire(any(Transaction.class), anyString(), any())).thenAnswer(invocation -> {
                        Transaction transaction = invocation.getArgument(0);
                        transaction.setStatus(Transaction.TransactionStatus.EXPIRED);
                        return transaction;
                });

                DefaultDepositFlowService service = new DefaultDepositFlowService(
                                walletRepository,
                                transactionRepository,
                                identityServiceClient,
                                agentServiceClient,
                                idempotencyService,
                                ledgerIntegrationService,
                                mobileMoneyCallbackSecurityService,
                                callbackReconciliationService,
                                limitEnforcementService,
                                sagaOrchestrator,
                                eventPublisher,
                                fieldEncryptionService,
                                transactionLifecycleService);

                WalletFlowResult result = service.process(new DepositCommand(
                                walletId,
                                wallet.getUserId(),
                                new BigDecimal("20.00"),
                                WalletChannel.MOBILE,
                                "idem-mobile-expired",
                                "dep-mobile-expired",
                                true,
                                "cb-expired",
                                "signed-callback",
                                System.currentTimeMillis() / 1000,
                                "10.0.0.6"));

                assertThat(result.success()).isFalse();
                assertThat(result.message()).isEqualTo("Mobile callback timed out");
                verify(transactionLifecycleService).expire(any(Transaction.class), eq("Mobile callback timed out"), any());
        }

            @Test
            void depositShouldTransitionToReversedWhenPostingFailsAfterBalanceApply() {
                UUID walletId = UUID.randomUUID();
                wallet.setBalance(new BigDecimal("100.00"));
                lenient().when(idempotencyService.get(anyString())).thenReturn(Optional.empty());
                when(agentServiceClient.hasAvailableFloat(any(), any())).thenReturn(true);
                when(walletRepository.findByIdForUpdate(walletId)).thenReturn(Optional.of(wallet));
                when(limitEnforcementService.validate(any(), any(), any())).thenReturn(true);
                when(transactionRepository.existsByReference(anyString())).thenReturn(false);
                lenient().when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));
                doThrow(new IllegalStateException("ledger unavailable"))
                        .when(ledgerIntegrationService).recordDoubleEntry(anyString(), any(), any(), anyString());

                DefaultDepositFlowService service = new DefaultDepositFlowService(
                        walletRepository,
                        transactionRepository,
                        identityServiceClient,
                        agentServiceClient,
                        idempotencyService,
                        ledgerIntegrationService,
                        mobileMoneyCallbackSecurityService,
                        callbackReconciliationService,
                        limitEnforcementService,
                        sagaOrchestrator,
                        eventPublisher,
                        fieldEncryptionService,
                        transactionLifecycleService);

                WalletFlowResult result = service.process(new DepositCommand(
                        walletId,
                        wallet.getUserId(),
                        new BigDecimal("20.00"),
                        WalletChannel.AGENT,
                        "idem-reversal",
                        "dep-reversal",
                        true,
                        null,
                        null,
                        null,
                        null));

                assertThat(result.success()).isFalse();
                assertThat(wallet.getBalance()).isEqualByComparingTo("100.00");
                verify(transactionLifecycleService).transition(any(Transaction.class), eq(Transaction.TransactionStatus.REVERSED), anyString(), any(), any(), any());
            }

    @Test
    void withdrawalShouldFailWhenVelocityRiskDetected() {
        lenient().when(idempotencyService.get(anyString())).thenReturn(Optional.empty());
        when(fraudVelocityService.isSuspicious(any(), any(), any())).thenReturn(true);

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
                UUID.randomUUID(),
                wallet.getUserId(),
                new BigDecimal("15.00"),
                WithdrawalMode.REGISTERED_NUMBER,
                null,
                "esp",
                "eac",
                "idem-velocity-2",
                "ref-velocity-2",
                null,
                null,
                null));

        assertThat(result.success()).isFalse();
        assertThat(result.message()).isEqualTo("Velocity risk detected");
    }

    @Test
    void reservationCreateShouldPersistReservation() {
        lenient().when(idempotencyService.get(anyString())).thenReturn(Optional.empty());
                when(walletRepository.findByIdForUpdate(wallet.getId())).thenReturn(Optional.of(wallet));
        when(limitEnforcementService.validate(any(), any(), any())).thenReturn(true);
        when(reservationRepository.createReservation(any(), any(), any())).thenAnswer(invocation -> {
            Reservation reservation = new Reservation();
            reservation.setWallet(wallet);
            reservation.setAmount(invocation.getArgument(1));
            reservation.setExpiryDate(invocation.getArgument(2));
            try {
                java.lang.reflect.Field idField = Reservation.class.getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(reservation, UUID.randomUUID());
            } catch (ReflectiveOperationException ignored) {
            }
            return reservation;
        });

        DefaultReservationFlowService service = new DefaultReservationFlowService(
                walletRepository,
                reservationRepository,
                idempotencyService,
                ledgerIntegrationService,
                eventPublisher,
                limitEnforcementService,
                transactionLifecycleService);

        WalletFlowResult result = service.create(new ReservationCommand(
                wallet.getId(),
                wallet.getUserId(),
                new BigDecimal("5.00"),
                Instant.now().plusSeconds(3600),
                "idem-4",
                "ref-4"));

        assertThat(result.success()).isTrue();
        verify(eventPublisher).publish(eq("wallet.reservation.created"), any());
    }

        @Test
        void reservationCreateShouldFollowLifecycleStateFlow() {
                lenient().when(idempotencyService.get(anyString())).thenReturn(Optional.empty());
                when(walletRepository.findByIdForUpdate(wallet.getId())).thenReturn(Optional.of(wallet));
                when(limitEnforcementService.validate(any(), any(), any())).thenReturn(true);
                when(reservationRepository.createReservation(any(), any(), any())).thenAnswer(invocation -> {
                        Reservation reservation = new Reservation();
                        reservation.setWallet(wallet);
                        reservation.setAmount(invocation.getArgument(1));
                        reservation.setExpiryDate(invocation.getArgument(2));
                        try {
                                java.lang.reflect.Field idField = Reservation.class.getDeclaredField("id");
                                idField.setAccessible(true);
                                idField.set(reservation, UUID.randomUUID());
                        } catch (ReflectiveOperationException ignored) {
                        }
                        return reservation;
                });

                DefaultReservationFlowService service = new DefaultReservationFlowService(
                                walletRepository,
                                reservationRepository,
                                idempotencyService,
                                ledgerIntegrationService,
                                eventPublisher,
                                limitEnforcementService,
                                transactionLifecycleService);

                WalletFlowResult result = service.create(new ReservationCommand(
                                wallet.getId(),
                                wallet.getUserId(),
                                new BigDecimal("7.00"),
                                Instant.now().plusSeconds(3600),
                                "idem-res-state",
                                "ref-res-state"));

                assertThat(result.success()).isTrue();
                InOrder order = inOrder(transactionLifecycleService);
                order.verify(transactionLifecycleService).initialize(any(Transaction.class), anyString(), any(), any());
                order.verify(transactionLifecycleService).transition(any(Transaction.class), eq(Transaction.TransactionStatus.RESERVED), anyString(), any(), any(), any());
        }

        @Test
        void reservationReleaseShouldTransitionToReleasedState() {
                UUID reservationId = UUID.randomUUID();
                lenient().when(idempotencyService.get(anyString())).thenReturn(Optional.empty());
                when(reservationRepository.releaseReservation(reservationId)).thenReturn(true);

                Reservation reservation = new Reservation();
                reservation.setWallet(wallet);
                reservation.setAmount(new BigDecimal("8.00"));
                when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));

                DefaultReservationFlowService service = new DefaultReservationFlowService(
                                walletRepository,
                                reservationRepository,
                                idempotencyService,
                                ledgerIntegrationService,
                                eventPublisher,
                                limitEnforcementService,
                                transactionLifecycleService);

                WalletFlowResult result = service.release(reservationId, "idem-res-release");

                assertThat(result.success()).isTrue();
                InOrder order = inOrder(transactionLifecycleService);
                order.verify(transactionLifecycleService).initialize(any(Transaction.class), anyString(), any(), any());
                order.verify(transactionLifecycleService).transition(any(Transaction.class), eq(Transaction.TransactionStatus.RESERVED), anyString(), any(), any(), any());
                order.verify(transactionLifecycleService).transition(any(Transaction.class), eq(Transaction.TransactionStatus.RELEASED), anyString(), any(), any(), any());
        }

        @Test
        void reservationConfirmShouldReverseOnPostingFailure() {
                UUID reservationId = UUID.randomUUID();
                lenient().when(idempotencyService.get(anyString())).thenReturn(Optional.empty());

                Reservation reservation = new Reservation();
                reservation.setWallet(wallet);
                reservation.setAmount(new BigDecimal("9.00"));
                when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));
                when(reservationRepository.confirmDebit(reservationId)).thenReturn(true);
                when(reservationRepository.releaseReservation(reservationId)).thenReturn(true);
                doThrow(new RuntimeException("confirm posting failed"))
                                .when(ledgerIntegrationService)
                                .recordDoubleEntry(eq("reservation.confirm"), eq(wallet.getId()), eq(new BigDecimal("9.00")), eq(String.valueOf(reservationId)));

                DefaultReservationFlowService service = new DefaultReservationFlowService(
                                walletRepository,
                                reservationRepository,
                                idempotencyService,
                                ledgerIntegrationService,
                                eventPublisher,
                                limitEnforcementService,
                                transactionLifecycleService);

                WalletFlowResult result = service.confirm(reservationId, "idem-res-confirm-reverse");

                assertThat(result.success()).isFalse();
                assertThat(result.message()).isEqualTo("Reservation reversed");
                verify(reservationRepository).releaseReservation(reservationId);
                verify(transactionLifecycleService).transition(any(Transaction.class), eq(Transaction.TransactionStatus.REVERSED), anyString(), any(), eq("RESERVATION_REVERSED"), eq("confirm posting failed"));
        }

    @Test
    void etcGenerateShouldPublishEvent() {
        lenient().when(idempotencyService.get(anyString())).thenReturn(Optional.empty());
                when(walletRepository.findByIdForUpdate(wallet.getId())).thenReturn(Optional.of(wallet));
        when(etcRepository.generateCode(any(), anyString(), any())).thenAnswer(invocation -> {
            Etc etc = new Etc();
            etc.setWallet(wallet);
            etc.setCodeHash(invocation.getArgument(1));
            etc.setExpiresAt(invocation.getArgument(2));
            try {
                java.lang.reflect.Field idField = Etc.class.getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(etc, UUID.randomUUID());
            } catch (ReflectiveOperationException ignored) {
            }
            return etc;
        });

        when(etcCodeSecurityService.hashCode(anyString())).thenReturn("hash-etc");
        when(etcCodeSecurityService.redact(anyString())).thenReturn("***C123");
        when(etcCodePolicyService.hasRequiredEntropy(anyString())).thenReturn(true);
        when(etcCodePolicyService.isExpiryWithinWindow(any(), any())).thenReturn(true);

        DefaultEtcFlowService service = new DefaultEtcFlowService(
                etcRepository,
                walletRepository,
                transactionRepository,
                idempotencyService,
                ledgerIntegrationService,
                limitEnforcementService,
                eventPublisher,
                etcCodeSecurityService,
                etcCodePolicyService,
                etcBruteForceProtectionService,
                transactionLifecycleService,
                5);
        WalletFlowResult result = service.generate(new EtcCommand(
                wallet.getId(),
                wallet.getUserId(),
                "ETC-10-ABC123",
                Instant.now().plusSeconds(3600),
                "idem-5"));

        assertThat(result.success()).isTrue();
        verify(eventPublisher).publish(eq("wallet.etc.generated"), any());
    }

        @Test
        void etcRedeemShouldFollowLifecycleStateFlow() {
                wallet.setBalance(new BigDecimal("100.00"));
                lenient().when(idempotencyService.get(anyString())).thenReturn(Optional.empty());
                when(etcCodeSecurityService.hashCode(anyString())).thenReturn("hash-etc");
                when(etcCodeSecurityService.redact(anyString())).thenReturn("***C123");
                when(etcBruteForceProtectionService.isBlocked(anyString(), anyString(), anyString())).thenReturn(false);

                Etc etc = new Etc();
                etc.setWallet(wallet);
                etc.setCodeHash("hash-etc");
                etc.setExpiresAt(Instant.now().plusSeconds(1200));
                when(etcRepository.findByCodeHashForUpdate("hash-etc")).thenReturn(Optional.of(etc));
                when(etcRepository.isCodeExpired(eq("hash-etc"), any())).thenReturn(false);
                when(etcRepository.redeemCode(eq("hash-etc"), any())).thenReturn(true);
                when(walletRepository.findByIdForUpdate(wallet.getId())).thenReturn(Optional.of(wallet));
                when(limitEnforcementService.validate(any(), any(), any())).thenReturn(true);

                DefaultEtcFlowService service = new DefaultEtcFlowService(
                                etcRepository,
                                walletRepository,
                                transactionRepository,
                                idempotencyService,
                                ledgerIntegrationService,
                                limitEnforcementService,
                                eventPublisher,
                                etcCodeSecurityService,
                                etcCodePolicyService,
                                etcBruteForceProtectionService,
                                transactionLifecycleService,
                                5);

                WalletFlowResult result = service.redeem("ETC-10-ABC123", "idem-etc-redeem-flow", "device-1", "10.0.0.1");

                assertThat(result.success()).isTrue();
                InOrder order = inOrder(transactionLifecycleService);
                order.verify(transactionLifecycleService).initialize(any(Transaction.class), anyString(), any(), any());
                order.verify(transactionLifecycleService).transition(any(Transaction.class), eq(Transaction.TransactionStatus.PENDING), anyString(), any(), any(), any());
                order.verify(transactionLifecycleService).transition(any(Transaction.class), eq(Transaction.TransactionStatus.PROCESSING), anyString(), any(), any(), any());
                order.verify(transactionLifecycleService).transition(any(Transaction.class), eq(Transaction.TransactionStatus.COMPLETED), anyString(), any(), any(), any());
        }

        @Test
        void etcRedeemShouldTransitionToExpiredWhenCodeHasTimedOut() {
                lenient().when(idempotencyService.get(anyString())).thenReturn(Optional.empty());
                when(etcCodeSecurityService.hashCode(anyString())).thenReturn("hash-etc");
                when(etcCodeSecurityService.redact(anyString())).thenReturn("***C123");
                when(etcBruteForceProtectionService.isBlocked(anyString(), anyString(), anyString())).thenReturn(false);
                when(etcBruteForceProtectionService.registerFailure(anyString(), anyString(), anyString())).thenReturn(false);
                when(etcRepository.registerFailedAttempt(anyString(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(1);

                Etc etc = new Etc();
                etc.setWallet(wallet);
                etc.setCodeHash("hash-etc");
                etc.setExpiresAt(Instant.now().minusSeconds(60));
                when(etcRepository.findByCodeHashForUpdate("hash-etc")).thenReturn(Optional.of(etc));
                when(etcRepository.isCodeExpired(eq("hash-etc"), any())).thenReturn(true);

                DefaultEtcFlowService service = new DefaultEtcFlowService(
                                etcRepository,
                                walletRepository,
                                transactionRepository,
                                idempotencyService,
                                ledgerIntegrationService,
                                limitEnforcementService,
                                eventPublisher,
                                etcCodeSecurityService,
                                etcCodePolicyService,
                                etcBruteForceProtectionService,
                                transactionLifecycleService,
                                5);

                WalletFlowResult result = service.redeem("ETC-10-ABC123", "idem-etc-expired", "device-1", "10.0.0.1");

                assertThat(result.success()).isFalse();
                assertThat(result.message()).isEqualTo("ETC code has expired");
                verify(transactionLifecycleService).transition(any(Transaction.class), eq(Transaction.TransactionStatus.EXPIRED), anyString(), any(), eq("ETC_EXPIRED"), eq("ETC code has expired"));
        }

        @Test
        void etcRedeemShouldPersistFailureCodeWhenCodeCannotBeRedeemed() {
                lenient().when(idempotencyService.get(anyString())).thenReturn(Optional.empty());
                when(etcCodeSecurityService.hashCode(anyString())).thenReturn("hash-etc");
                when(etcCodeSecurityService.redact(anyString())).thenReturn("***C123");
                when(etcBruteForceProtectionService.isBlocked(anyString(), anyString(), anyString())).thenReturn(false);
                when(etcBruteForceProtectionService.registerFailure(anyString(), anyString(), anyString())).thenReturn(false);
                when(etcRepository.registerFailedAttempt(anyString(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(1);

                Etc etc = new Etc();
                etc.setWallet(wallet);
                etc.setCodeHash("hash-etc");
                etc.setExpiresAt(Instant.now().plusSeconds(1200));
                when(etcRepository.findByCodeHashForUpdate("hash-etc")).thenReturn(Optional.of(etc));
                when(etcRepository.isCodeExpired(eq("hash-etc"), any())).thenReturn(false);
                when(etcRepository.redeemCode(eq("hash-etc"), any())).thenReturn(false);

                DefaultEtcFlowService service = new DefaultEtcFlowService(
                                etcRepository,
                                walletRepository,
                                transactionRepository,
                                idempotencyService,
                                ledgerIntegrationService,
                                limitEnforcementService,
                                eventPublisher,
                                etcCodeSecurityService,
                                etcCodePolicyService,
                                etcBruteForceProtectionService,
                                transactionLifecycleService,
                                5);

                WalletFlowResult result = service.redeem("ETC-10-ABC123", "idem-etc-failed", "device-1", "10.0.0.1");

                assertThat(result.success()).isFalse();
                assertThat(result.message()).isEqualTo("ETC code cannot be redeemed");
                verify(transactionLifecycleService).transition(any(Transaction.class), eq(Transaction.TransactionStatus.FAILED), anyString(), any(), eq("ETC_REUSED_OR_INVALID"), eq("ETC code cannot be redeemed"));
        }
}
