package com.elvo.wallet.controller;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.elvo.wallet.entity.Wallet;
import com.elvo.wallet.exception.GlobalExceptionHandler;
import com.elvo.wallet.mapper.WalletMapper;
import com.elvo.wallet.monitoring.SecurityAlertStreamingService;
import com.elvo.wallet.monitoring.WalletMetricsRecorder;
import com.elvo.wallet.repository.EtcRepository;
import com.elvo.wallet.repository.ReservationRepository;
import com.elvo.wallet.repository.TransactionRepository;
import com.elvo.wallet.repository.WalletRepository;
import com.elvo.wallet.security.DestinationRiskService;
import com.elvo.wallet.security.DeviceLocationRiskService;
import com.elvo.wallet.security.EtcCodePolicyService;
import com.elvo.wallet.security.FraudRulesEngine;
import com.elvo.wallet.security.IpGeovelocityRiskService;
import com.elvo.wallet.security.MakerCheckerApprovalService;
import com.elvo.wallet.security.ApiAbuseProtectionService;
import com.elvo.wallet.security.AmlCaseWorkflowService;
import com.elvo.wallet.security.EmergencyControlService;
import com.elvo.wallet.security.SanctionsScreeningService;
import com.elvo.wallet.security.SecretManagerService;
import com.elvo.wallet.security.UserJwtPrincipal;
import com.elvo.wallet.security.WalletFieldEncryptionService;
import com.elvo.wallet.security.WalletOperationRateLimitService;
import com.elvo.wallet.service.WalletService;
import com.elvo.wallet.service.impl.WalletLimitEnforcementService;
import com.elvo.wallet.service.model.WalletFlowResult;
import com.elvo.wallet.service.model.WithdrawalCommand;
import com.elvo.wallet.service.model.WithdrawalMode;

@WebMvcTest(WalletController.class)
@Import({WalletMapper.class, GlobalExceptionHandler.class, WalletControllerTest.WalletControllerTestConfig.class})
@TestPropertySource(properties = {
    "elvo.security.correlation.signature-secret=test-wallet-correlation-signature-secret"
})
class WalletControllerTest {

    private static final UUID AUTHENTICATED_USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WalletService walletService;

    @MockBean
    private WalletRepository walletRepository;

    @MockBean
    private TransactionRepository transactionRepository;

    @MockBean
    private ReservationRepository reservationRepository;

    @MockBean
    private EtcRepository etcRepository;

    @MockBean
    private EtcCodePolicyService etcCodePolicyService;

    @MockBean
    private WalletOperationRateLimitService operationRateLimitService;

    @MockBean
    private DeviceLocationRiskService deviceLocationRiskService;

    @MockBean
    private DestinationRiskService destinationRiskService;

    @MockBean
    private IpGeovelocityRiskService ipGeovelocityRiskService;

    @MockBean
    private FraudRulesEngine fraudRulesEngine;

    @MockBean
    private MakerCheckerApprovalService makerCheckerApprovalService;

    @MockBean
    private SecurityAlertStreamingService securityAlertStreamingService;

    @MockBean
    private ApiAbuseProtectionService apiAbuseProtectionService;

    @MockBean
    private EmergencyControlService emergencyControlService;

    @MockBean
    private SanctionsScreeningService sanctionsScreeningService;

    @MockBean
    private AmlCaseWorkflowService amlCaseWorkflowService;
    
    @MockBean
    private WalletFieldEncryptionService fieldEncryptionService;

    @MockBean
    private WalletMetricsRecorder walletMetricsRecorder;

    @MockBean
    private WalletLimitEnforcementService walletLimitEnforcementService;

    @BeforeEach
    void setUpAuthenticatedUser() {
        UserJwtPrincipal principal = new UserJwtPrincipal(AUTHENTICATED_USER_ID, "ELVO-UNIT-123456", java.util.List.of("wallet:read", "wallet:write"));
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, "N/A", java.util.List.of(() -> "ROLE_USER"));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        when(ipGeovelocityRiskService.evaluate(any(), any(), any()))
            .thenReturn(new IpGeovelocityRiskService.RiskDecision(false, false, null));
        when(fraudRulesEngine.evaluate(any(), any(), any(), any()))
            .thenReturn(FraudRulesEngine.FraudDecision.allow());
        when(makerCheckerApprovalService.evaluate(any(), any(), any(), any()))
            .thenReturn(MakerCheckerApprovalService.ApprovalDecision.allow());
        when(apiAbuseProtectionService.evaluate(any(), any(), any()))
            .thenReturn(ApiAbuseProtectionService.AbuseDecision.allow());
        when(emergencyControlService.isGlobalKillSwitchEnabled()).thenReturn(false);
        when(emergencyControlService.isWalletEmergencyFrozen(any())).thenReturn(false);
        when(sanctionsScreeningService.evaluate(any(), any()))
            .thenReturn(SanctionsScreeningService.ScreeningDecision.allow());
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @TestConfiguration
    static class WalletControllerTestConfig {
        @Bean
        SecretManagerService secretManagerService(Environment environment) {
            return new SecretManagerService(environment);
        }
    }

    @Test
    void depositEndpointShouldReturnCreated() throws Exception {
        Wallet wallet = new Wallet();
        wallet.setBalance(new BigDecimal("100.00"));
        when(walletRepository.findByUserId(any())).thenReturn(java.util.Optional.of(wallet));
        when(walletService.processDeposit(any())).thenReturn(WalletFlowResult.success("Deposit completed", wallet.getId(), UUID.randomUUID(), "wallet.deposit.completed"));

        mockMvc.perform(post("/wallets/deposits")
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                    {"amount":20.00,"channel":"MOBILE","idempotencyKey":"idem-0001","reference":"ref-1","mobileCallbackReference":"cb-1"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.eventType").value("wallet.deposit.completed"));
    }

    @Test
    void depositEndpointShouldRejectInvalidPayload() throws Exception {
        when(walletRepository.findByUserId(any())).thenReturn(java.util.Optional.of(new Wallet()));

        mockMvc.perform(post("/wallets/deposits")
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"amount":0,"channel":"BAD","idempotencyKey":"x"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void transferShouldRejectBlockedIpAddress() throws Exception {
        Wallet sourceWallet = new Wallet();
        ReflectionTestUtils.setField(sourceWallet, "id", UUID.randomUUID());
        Wallet targetWallet = new Wallet();
        ReflectionTestUtils.setField(targetWallet, "id", UUID.randomUUID());

        when(walletRepository.findByUserId(AUTHENTICATED_USER_ID)).thenReturn(java.util.Optional.of(sourceWallet));
        when(operationRateLimitService.enforce(any(), any(), any(), any(), any()))
            .thenReturn(WalletOperationRateLimitService.RateLimitResult.allow());
        when(ipGeovelocityRiskService.evaluate(any(), any(), any()))
            .thenReturn(new IpGeovelocityRiskService.RiskDecision(true, true, "IP reputation policy blocked this request"));

        String payload = String.format(
            "{\"targetWalletId\":\"%s\",\"amount\":20.00,\"idempotencyKey\":\"idem-transfer-1\"}",
            targetWallet.getId());

        mockMvc.perform(post("/wallets/transfers")
                .with(csrf())
                .contentType("application/json")
                .content(payload))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("IP reputation policy blocked this request"));

        verify(walletService, never()).processTransfer(any());
    }

    @Test
    void withdrawalShouldRequireAdditionalVerificationOnLocationAnomaly() throws Exception {
        Wallet wallet = new Wallet();
        ReflectionTestUtils.setField(wallet, "id", UUID.randomUUID());
        when(walletRepository.findByUserId(AUTHENTICATED_USER_ID)).thenReturn(java.util.Optional.of(wallet));
        when(operationRateLimitService.enforce(any(), any(), any(), any(), any()))
            .thenReturn(WalletOperationRateLimitService.RateLimitResult.allow());
        when(ipGeovelocityRiskService.evaluate(any(), any(), any()))
            .thenReturn(new IpGeovelocityRiskService.RiskDecision(false, true, "Location anomaly detected; additional verification required"));

        mockMvc.perform(post("/wallets/withdrawals")
                .with(csrf())
                .contentType("application/json")
                .content("""
                    {
                      "amount":20.00,
                      "mode":"REGISTERED_NUMBER",
                      "targetNumber":"+1234567890",
                      "espCode":"esp-1234",
                      "eacCode":"eac-1234",
                      "idempotencyKey":"idem-withdraw-1"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("STEP_UP_REQUIRED"))
            .andExpect(jsonPath("$.eventType").value("wallet.withdrawal.failed.step_up_required"));

        verify(walletService, never()).processWithdrawal(any());
    }

    @Test
    void transferShouldReturnNormalizedStepUpCodeWhenDeviceRiskNeedsVerification() throws Exception {
        Wallet sourceWallet = new Wallet();
        ReflectionTestUtils.setField(sourceWallet, "id", UUID.randomUUID());
        Wallet targetWallet = new Wallet();
        ReflectionTestUtils.setField(targetWallet, "id", UUID.randomUUID());

        when(walletRepository.findByUserId(AUTHENTICATED_USER_ID)).thenReturn(java.util.Optional.of(sourceWallet));
        when(operationRateLimitService.enforce(any(), any(), any(), any(), any()))
            .thenReturn(WalletOperationRateLimitService.RateLimitResult.allow());
        when(ipGeovelocityRiskService.evaluate(any(), any(), any()))
            .thenReturn(new IpGeovelocityRiskService.RiskDecision(false, false, null));
        when(deviceLocationRiskService.requiresAdditionalVerification(any(), any(), any())).thenReturn(true);

        String payload = String.format(
            "{\"targetWalletId\":\"%s\",\"amount\":20.00,\"idempotencyKey\":\"idem-transfer-step-up\"}",
            targetWallet.getId());

        mockMvc.perform(post("/wallets/transfers")
                .with(csrf())
                .contentType("application/json")
                .content(payload))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("STEP_UP_REQUIRED"))
            .andExpect(jsonPath("$.eventType").value("wallet.transfer.failed.step_up_required"));

        verify(walletService, never()).processTransfer(any());
    }

    @Test
    void withdrawalShouldBlockWhenFraudRulesDecisionIsBlocked() throws Exception {
        Wallet wallet = new Wallet();
        ReflectionTestUtils.setField(wallet, "id", UUID.randomUUID());

        when(walletRepository.findByUserId(AUTHENTICATED_USER_ID)).thenReturn(java.util.Optional.of(wallet));
        when(operationRateLimitService.enforce(any(), any(), any(), any(), any()))
            .thenReturn(WalletOperationRateLimitService.RateLimitResult.allow());
        when(destinationRiskService.evaluate(any(), any(), any()))
            .thenReturn(new DestinationRiskService.DestinationRiskDecision(false, false, null));
        when(fraudRulesEngine.evaluate(any(), any(), any(), any()))
            .thenReturn(FraudRulesEngine.FraudDecision.block("Transaction blocked by operator fraud override"));

        mockMvc.perform(post("/wallets/withdrawals")
                .with(csrf())
                .contentType("application/json")
                .content("""
                    {
                      "amount":20.00,
                      "mode":"REGISTERED_NUMBER",
                      "targetNumber":"+1234567890",
                      "espCode":"esp-1234",
                      "eacCode":"eac-1234",
                      "idempotencyKey":"idem-withdraw-fraud-block"
                    }
                    """))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("Transaction blocked by operator fraud override"))
            .andExpect(jsonPath("$.eventType").value("wallet.withdrawal.failed"));

        verify(walletService, never()).processWithdrawal(any());
    }

    @Test
    void transferShouldBlockWhenFraudRulesDecisionIsBlocked() throws Exception {
        Wallet sourceWallet = new Wallet();
        ReflectionTestUtils.setField(sourceWallet, "id", UUID.randomUUID());
        Wallet targetWallet = new Wallet();
        ReflectionTestUtils.setField(targetWallet, "id", UUID.randomUUID());

        when(walletRepository.findByUserId(AUTHENTICATED_USER_ID)).thenReturn(java.util.Optional.of(sourceWallet));
        when(operationRateLimitService.enforce(any(), any(), any(), any(), any()))
            .thenReturn(WalletOperationRateLimitService.RateLimitResult.allow());
        when(fraudRulesEngine.evaluate(any(), any(), any(), any()))
            .thenReturn(FraudRulesEngine.FraudDecision.block("Transaction blocked by operator fraud override"));

        String payload = String.format(
            "{\"targetWalletId\":\"%s\",\"amount\":20.00,\"idempotencyKey\":\"idem-transfer-fraud-block\"}",
            targetWallet.getId());

        mockMvc.perform(post("/wallets/transfers")
                .with(csrf())
                .contentType("application/json")
                .content(payload))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("Transaction blocked by operator fraud override"))
            .andExpect(jsonPath("$.eventType").value("wallet.transfer.failed"));

        verify(walletService, never()).processTransfer(any());
    }

    @Test
    void withdrawalShouldNormalizeChallengeFailureFromService() throws Exception {
        Wallet wallet = new Wallet();
        ReflectionTestUtils.setField(wallet, "id", UUID.randomUUID());

        when(walletRepository.findByUserId(AUTHENTICATED_USER_ID)).thenReturn(java.util.Optional.of(wallet));
        when(operationRateLimitService.enforce(any(), any(), any(), any(), any()))
            .thenReturn(WalletOperationRateLimitService.RateLimitResult.allow());
        when(destinationRiskService.evaluate(any(), any(), any()))
            .thenReturn(new DestinationRiskService.DestinationRiskDecision(false, false, null));
        when(walletService.processWithdrawal(any()))
            .thenReturn(WalletFlowResult.failure("Transaction challenge confirmation required", wallet.getId(), "wallet.withdrawal.failed"));

        mockMvc.perform(post("/wallets/withdrawals")
                .with(csrf())
                .contentType("application/json")
                .content("""
                    {
                      "amount":20.00,
                      "mode":"REGISTERED_NUMBER",
                      "targetNumber":"+1234567890",
                      "espCode":"esp-1234",
                      "eacCode":"eac-1234",
                      "idempotencyKey":"idem-withdraw-challenge"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("TRANSACTION_CHALLENGE_REQUIRED"))
            .andExpect(jsonPath("$.eventType").value("wallet.withdrawal.failed.challenge_required"));
    }

    @Test
    void deviceFreeWithdrawalEndpointShouldUseDedicatedContractAndProcessWithDeviceFreeMode() throws Exception {
        Wallet wallet = new Wallet();
        ReflectionTestUtils.setField(wallet, "id", UUID.randomUUID());

        when(walletRepository.findByUserId(AUTHENTICATED_USER_ID)).thenReturn(java.util.Optional.of(wallet));
        when(operationRateLimitService.enforce(any(), any(), any(), any(), any()))
            .thenReturn(WalletOperationRateLimitService.RateLimitResult.allow());
        when(destinationRiskService.evaluate(any(), any(), any()))
            .thenReturn(new DestinationRiskService.DestinationRiskDecision(false, false, null));
        when(walletService.processWithdrawal(any()))
            .thenReturn(WalletFlowResult.success("Withdrawal completed", wallet.getId(), UUID.randomUUID(), "wallet.withdrawal.completed"));

        mockMvc.perform(post("/wallets/withdrawals/device-free")
                .with(csrf())
                .contentType("application/json")
                .content("""
                    {
                      "amount":20.00,
                      "targetNumber":"+1234567890",
                      "espCode":"esp-1234",
                      "eacCode":"eac-1234",
                      "idempotencyKey":"idem-device-free-1"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.eventType").value("wallet.withdrawal.completed"));

        verify(walletService).processWithdrawal(argThat((WithdrawalCommand command) -> command.mode() == WithdrawalMode.DEVICE_FREE));
    }

    @Test
    void releaseReservationShouldReturnNotFoundWhenReservationOwnedByAnotherUser() throws Exception {
        UUID reservationId = UUID.randomUUID();
        UUID userId = AUTHENTICATED_USER_ID;

        when(reservationRepository.findByIdAndWalletUserId(reservationId, userId))
            .thenReturn(java.util.Optional.empty());

        mockMvc.perform(post("/wallets/me/reservations/{id}/release", reservationId)
                        .with(csrf())
                        .contentType("application/json")
                        .content("{" + "\"reason\":\"idem-release-1\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WALLET_NOT_FOUND"));

        verify(walletService, never()).releaseReservation(any(), any());
    }

    @Test
    void confirmReservationShouldReturnNotFoundWhenReservationOwnedByAnotherUser() throws Exception {
        UUID reservationId = UUID.randomUUID();
        UUID userId = AUTHENTICATED_USER_ID;

        when(reservationRepository.findByIdAndWalletUserId(reservationId, userId))
            .thenReturn(java.util.Optional.empty());

        mockMvc.perform(post("/wallets/me/reservations/{id}/confirm", reservationId)
                        .with(csrf())
                        .contentType("application/json")
                        .content("{" + "\"reason\":\"idem-confirm-1\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WALLET_NOT_FOUND"));

        verify(walletService, never()).confirmReservation(any(), any());
    }
}
