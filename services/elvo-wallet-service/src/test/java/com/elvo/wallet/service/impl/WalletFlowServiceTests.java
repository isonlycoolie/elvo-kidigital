package com.elvo.wallet.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
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
import com.elvo.wallet.security.StepUpAuthenticationService;
import com.elvo.wallet.service.EacReplayProtectionService;
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
    @Mock private WalletLimitEnforcementService limitEnforcementService;
    @Mock private WalletEventPublisher eventPublisher;
    @Mock private WalletSagaOrchestrator sagaOrchestrator;
    @Mock private EacReplayProtectionService eacReplayProtectionService;
    @Mock private EtcCodeSecurityService etcCodeSecurityService;
    @Mock private EtcCodePolicyService etcCodePolicyService;
    @Mock private EtcBruteForceProtectionService etcBruteForceProtectionService;
    @Mock private StepUpAuthenticationService stepUpAuthenticationService;

    private Wallet wallet;

    @BeforeEach
    void setUp() {
        wallet = new Wallet();
        ReflectionTestUtils.setField(wallet, "id", UUID.randomUUID());
        wallet.setUserId(UUID.randomUUID());
        wallet.setBalance(new BigDecimal("100.00"));
        wallet.setReservedBalance(BigDecimal.ZERO);
        wallet.setStatus(Wallet.WalletStatus.ACTIVE);
    }

    @Test
    void depositShouldPersistTransactionAndPublishEvent() {
        UUID walletId = UUID.randomUUID();
        wallet.setBalance(new BigDecimal("100.00"));
        when(idempotencyService.get(anyString())).thenReturn(Optional.empty());
        when(identityServiceClient.isUserActive(any())).thenReturn(true);
        when(walletRepository.findByIdForUpdate(walletId)).thenReturn(Optional.of(wallet));
        when(limitEnforcementService.validate(any(), any(), any())).thenReturn(true);
        when(transactionRepository.existsByReference(anyString())).thenReturn(false);
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
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
                callbackReconciliationService,
                limitEnforcementService,
                sagaOrchestrator,
                eventPublisher);

        WalletFlowResult result = service.process(new DepositCommand(walletId, wallet.getUserId(), new BigDecimal("20.00"), WalletChannel.MOBILE, "idem-1", "ref-1", true, "cb-1"));

        assertThat(result.success()).isTrue();
        verify(eventPublisher).publish(eq("wallet.deposit.completed"), any());
        verify(ledgerIntegrationService).recordDoubleEntry(anyString(), any(), any(), anyString());
        verify(callbackReconciliationService).scheduleRetry("cb-1", wallet.getId(), new BigDecimal("20.00"));
        verify(callbackReconciliationService).markReconciled("cb-1");
    }

    @Test
    void withdrawalShouldFailWhenEspVerificationFails() {
        when(idempotencyService.get(anyString())).thenReturn(Optional.empty());
        when(identityServiceClient.isUserActive(any())).thenReturn(true);
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
                stepUpAuthenticationService);

        WalletFlowResult result = service.process(new WithdrawalCommand(UUID.randomUUID(), wallet.getUserId(), new BigDecimal("10.00"), WithdrawalMode.DEVICE_FREE, "0900000000", "esp", "eac", "idem-2", "ref-2", null, null));

        assertThat(result.success()).isFalse();
        verify(walletRepository, never()).findByIdForUpdate(any());
        verify(eventPublisher).publish(eq("wallet.withdrawal.failed"), any());
    }

        @Test
        void withdrawalShouldFailWhenEacReplayDetected() {
        when(idempotencyService.get(anyString())).thenReturn(Optional.empty());
        when(identityServiceClient.isUserActive(any())).thenReturn(true);
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
            stepUpAuthenticationService);

        WalletFlowResult result = service.process(new WithdrawalCommand(
            UUID.randomUUID(),
            wallet.getUserId(),
            new BigDecimal("10.00"),
            WithdrawalMode.DEVICE_FREE,
            "0900000000",
            "esp",
            "eac",
            "idem-22",
            "ref-22",
            null,
            null
        ));

        assertThat(result.success()).isFalse();
        assertThat(result.message()).isEqualTo("EAC replay detected");
        verify(walletRepository, never()).findByIdForUpdate(any());
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
            stepUpAuthenticationService);

        UUID walletId = UUID.randomUUID();
        WalletFlowResult result = service.process(new TransferCommand(walletId, walletId, wallet.getUserId(), new BigDecimal("10.00"), "idem-3", "ref-3", null, null));

        assertThat(result.success()).isFalse();
        verify(walletRepository, never()).findByIdForUpdate(any());
    }

        @Test
        void withdrawalShouldFailWhenStepUpConfirmationMissingForDeviceFreeFlow() {
        when(idempotencyService.get(anyString())).thenReturn(Optional.empty());
        when(identityServiceClient.isUserActive(any())).thenReturn(true);
        when(identityServiceClient.verifyEsp(any(), any())).thenReturn(true);
        when(identityServiceClient.verifyEac(any(), any())).thenReturn(true);
        when(stepUpAuthenticationService.requiresStepUpForWithdrawal(any(), any())).thenReturn(true);
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
            stepUpAuthenticationService);

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
            "invalid-token"
        ));

        assertThat(result.success()).isFalse();
        assertThat(result.message()).isEqualTo("Step-up authentication required");
        verify(walletRepository, never()).findByIdForUpdate(any());
        }

        @Test
        void transferShouldFailWhenStepUpConfirmationMissingForHighValueTransfer() {
        lenient().when(idempotencyService.get(anyString())).thenReturn(Optional.empty());
        lenient().when(stepUpAuthenticationService.requiresStepUpForTransfer(any())).thenReturn(true);

        DefaultTransferFlowService service = new DefaultTransferFlowService(
            walletRepository,
            transactionRepository,
            idempotencyService,
            ledgerIntegrationService,
            limitEnforcementService,
            sagaOrchestrator,
            eventPublisher,
            stepUpAuthenticationService);

        WalletFlowResult result = service.process(new TransferCommand(
            UUID.randomUUID(),
            UUID.randomUUID(),
            wallet.getUserId(),
            new BigDecimal("1000.00"),
            "idem-33",
            "ref-33",
            "PIN",
            "bad-token"
        ));

        assertThat(result.success()).isFalse();
        assertThat(result.message()).isEqualTo("Step-up authentication required");
        verify(walletRepository, never()).findByIdForUpdate(any());
        }

    @Test
    void reservationCreateShouldPersistReservation() {
        when(idempotencyService.get(anyString())).thenReturn(Optional.empty());
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
                reservationRepository,
                idempotencyService,
                ledgerIntegrationService,
                eventPublisher,
                limitEnforcementService);

        WalletFlowResult result = service.create(new ReservationCommand(wallet.getId(), wallet.getUserId(), new BigDecimal("5.00"), Instant.now().plusSeconds(3600), "idem-4", "ref-4"));

        assertThat(result.success()).isTrue();
        verify(eventPublisher).publish(eq("wallet.reservation.created"), any());
    }

    @Test
    void etcGenerateShouldPublishEvent() {
        when(idempotencyService.get(anyString())).thenReturn(Optional.empty());
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
                5);

        WalletFlowResult result = service.generate(new EtcCommand(wallet.getId(), wallet.getUserId(), "ETC-10-ABC123", Instant.now().plusSeconds(3600), "idem-5"));

        assertThat(result.success()).isTrue();
        verify(eventPublisher).publish(eq("wallet.etc.generated"), any());
    }
}
