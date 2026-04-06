package com.elvo.wallet.controller;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.elvo.wallet.entity.Wallet;
import com.elvo.wallet.exception.GlobalExceptionHandler;
import com.elvo.wallet.mapper.WalletMapper;
import com.elvo.wallet.repository.WalletRepository;
import com.elvo.wallet.security.InternalServiceAuthorizationMatrix;
import com.elvo.wallet.security.SecurityConfig;
import com.elvo.wallet.security.WalletFieldEncryptionService;
import com.elvo.wallet.security.WalletOperationRateLimitService;
import com.elvo.wallet.service.WalletService;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@WebMvcTest(controllers = InternalWalletController.class)
@Import({
        SecurityConfig.class,
        WalletMapper.class,
        GlobalExceptionHandler.class,
        InternalServiceAuthorizationMatrix.class
})
@TestPropertySource(properties = {
        "elvo.security.internal-jwt.secret=test-secret-for-wallet-internal-auth-32",
        "elvo.security.internal-jwt.issuer=elvo-internal-auth",
        "elvo.security.internal-jwt.audience=elvo-wallet-service",
        "elvo.security.internal-jwt.required-role=INTERNAL_SERVICE",
    "elvo.security.internal-jwt.source-service-claim=sourceService",
    "elvo.security.internal-jwt.service-identity-claim=serviceIdentity"
})
class InternalWalletControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WalletRepository walletRepository;

    @MockBean
    private WalletService walletService;

        @MockBean
        private WalletOperationRateLimitService operationRateLimitService;

        @MockBean
        private WalletFieldEncryptionService fieldEncryptionService;

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
