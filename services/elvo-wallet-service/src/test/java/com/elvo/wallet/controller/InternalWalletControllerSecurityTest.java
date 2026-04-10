package com.elvo.wallet.controller;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.elvo.wallet.entity.Wallet;
import com.elvo.wallet.exception.GlobalExceptionHandler;
import com.elvo.wallet.mapper.WalletMapper;
import com.elvo.wallet.repository.WalletRepository;
import com.elvo.wallet.security.IdentityJwksKeyResolver;
import com.elvo.wallet.security.InternalServiceAuthorizationMatrix;
import com.elvo.wallet.security.MakerCheckerApprovalService;
import com.elvo.wallet.security.SecretManagerService;
import com.elvo.wallet.security.SecurityConfig;
import com.elvo.wallet.security.UserTokenRevocationChecker;
import com.elvo.wallet.security.WalletFieldEncryptionService;
import com.elvo.wallet.security.WalletOperationRateLimitService;
import com.elvo.wallet.service.DelegatedWithdrawalTokenLifecycleService;
import com.elvo.wallet.service.WalletService;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@WebMvcTest(controllers = InternalWalletController.class)
@Import({
        SecurityConfig.class,
        WalletMapper.class,
        GlobalExceptionHandler.class,
    InternalServiceAuthorizationMatrix.class,
    InternalWalletControllerSecurityTest.InternalWalletControllerSecurityTestConfig.class
})
@TestPropertySource(properties = {
        "elvo.security.internal-jwt.secret=test-secret-for-wallet-internal-auth-32",
        "elvo.security.internal-jwt.issuer=elvo-internal-auth",
        "elvo.security.internal-jwt.audience=elvo-wallet-service",
        "elvo.security.internal-jwt.required-role=INTERNAL_SERVICE",
    "elvo.security.internal-jwt.source-service-claim=sourceService",
    "elvo.security.internal-jwt.service-identity-claim=serviceIdentity",
    "elvo.security.correlation.signature-secret=test-wallet-correlation-signature-secret",
    "ELVO_INTERNAL_JWT_SECRET=test-wallet-correlation-signature-secret"
})
class InternalWalletControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WalletRepository walletRepository;

    @MockBean
    private WalletService walletService;

    @MockBean
    private DelegatedWithdrawalTokenLifecycleService delegatedWithdrawalTokenLifecycleService;

        @MockBean
        private WalletOperationRateLimitService operationRateLimitService;

        @MockBean
        private WalletFieldEncryptionService fieldEncryptionService;

    @MockBean
    private MakerCheckerApprovalService makerCheckerApprovalService;

    @MockBean
    private UserTokenRevocationChecker userTokenRevocationChecker;

    @MockBean
    private IdentityJwksKeyResolver identityJwksKeyResolver;

    @TestConfiguration
    static class InternalWalletControllerSecurityTestConfig {
        @Bean
        SecretManagerService secretManagerService(Environment environment) {
            return new SecretManagerService(environment);
        }
    }

    @Test
    void internalBalanceShouldRejectUnauthorizedSourceService() throws Exception {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        Wallet wallet = new Wallet();
        wallet.setUserId(userId);
        wallet.setBalance(new BigDecimal("100.00"));
        wallet.setReservedBalance(new BigDecimal("10.00"));
        when(walletRepository.findByUserId(userId)).thenReturn(java.util.Optional.of(wallet));

        mockMvc.perform(get("/api/v1/internal/wallets/{userId}/balance", userId)
                        .header("Authorization", "Bearer " + serviceToken("billing-service")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void internalBalanceShouldAllowIdentityService() throws Exception {
        UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        Wallet wallet = new Wallet();
        wallet.setUserId(userId);
        wallet.setBalance(new BigDecimal("200.00"));
        wallet.setReservedBalance(new BigDecimal("50.00"));
        when(walletRepository.findByUserId(userId)).thenReturn(java.util.Optional.of(wallet));

        mockMvc.perform(get("/api/v1/internal/wallets/{userId}/balance", userId)
                        .header("Authorization", "Bearer " + serviceToken("identity-service")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(200.0))
                .andExpect(jsonPath("$.reservedBalance").value(50.0));
    }

    @Test
    void internalCreateWalletShouldAllowIdentityService() throws Exception {
        UUID userId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        when(walletRepository.findByUserId(userId)).thenReturn(java.util.Optional.empty());

        Wallet persisted = new Wallet();
        persisted.setUserId(userId);
        persisted.setBalance(new BigDecimal("0.00"));
        persisted.setReservedBalance(new BigDecimal("0.00"));
        persisted.setStatus(Wallet.WalletStatus.ACTIVE);
        when(walletRepository.save(any(Wallet.class))).thenReturn(persisted);

        mockMvc.perform(post("/api/v1/internal/wallets/{userId}", userId)
                        .contentType("application/json")
                        .header("Authorization", "Bearer " + serviceToken("identity-service"))
                        .content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(userId.toString()));
    }

    @Test
    void internalCreateWalletShouldRejectBillingService() throws Exception {
        UUID userId = UUID.fromString("55555555-5555-5555-5555-555555555555");

        mockMvc.perform(post("/api/v1/internal/wallets/{userId}", userId)
                        .contentType("application/json")
                        .header("Authorization", "Bearer " + serviceToken("billing-service"))
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @WithMockUser
    void internalBalanceShouldRejectMissingBearerForInternalPath() throws Exception {
        UUID userId = UUID.fromString("33333333-3333-3333-3333-333333333333");

        mockMvc.perform(get("/api/v1/internal/wallets/{userId}/balance", userId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    private String serviceToken(String sourceService) {
        SecretKey key = Keys.hmacShaKeyFor("test-secret-for-wallet-internal-auth-32".getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();

        return Jwts.builder()
                .issuer("elvo-internal-auth")
                .audience().add("elvo-wallet-service").and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(300)))
                .claim("sourceService", sourceService)
                .claim("serviceIdentity", sourceService)
                .claim("roles", List.of("INTERNAL_SERVICE"))
                .signWith(key)
                .compact();
    }
}
