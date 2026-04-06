package com.elvo.wallet.controller;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.core.env.Environment;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.elvo.wallet.entity.Wallet;
import com.elvo.wallet.exception.GlobalExceptionHandler;
import com.elvo.wallet.mapper.WalletMapper;
import com.elvo.wallet.repository.EtcRepository;
import com.elvo.wallet.repository.ReservationRepository;
import com.elvo.wallet.repository.TransactionRepository;
import com.elvo.wallet.repository.WalletRepository;
import com.elvo.wallet.security.DestinationRiskService;
import com.elvo.wallet.security.DeviceLocationRiskService;
import com.elvo.wallet.security.EtcCodePolicyService;
import com.elvo.wallet.security.SecretManagerService;
import com.elvo.wallet.security.UserJwtPrincipal;
import com.elvo.wallet.security.WalletFieldEncryptionService;
import com.elvo.wallet.security.WalletOperationRateLimitService;
import com.elvo.wallet.service.WalletService;
import com.elvo.wallet.service.model.WalletFlowResult;

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
    private WalletFieldEncryptionService fieldEncryptionService;

    @BeforeEach
    void setUpAuthenticatedUser() {
        UserJwtPrincipal principal = new UserJwtPrincipal(AUTHENTICATED_USER_ID, "ELVO-UNIT-123456", java.util.List.of("wallet:read", "wallet:write"));
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, "N/A", java.util.List.of(() -> "ROLE_USER"));
        SecurityContextHolder.getContext().setAuthentication(authentication);
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
