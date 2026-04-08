package com.elvo.wallet.security;

import com.elvo.wallet.monitoring.SecurityAlertStreamingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class WalletPrivilegedAccessControlServiceTest {

    private WalletPrivilegedAccessControlService service;
    private SecurityAlertStreamingService alertStreamingService;

    @BeforeEach
    void setUp() {
        service = new WalletPrivilegedAccessControlService();
        alertStreamingService = mock(SecurityAlertStreamingService.class);
        service.setSecurityAlertStreamingService(alertStreamingService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void deniesPrivilegedActionWithoutRole() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "wallet-user",
                "N/A",
                List.of(new SimpleGrantedAuthority("ROLE_USER"))));

        assertThrows(AccessDeniedException.class, () -> service.authorizePrivilegedAction("fraud_override_user", "user-1"));
        verify(alertStreamingService).stream(
                org.mockito.ArgumentMatchers.eq("wallet.security.privileged_access_denied"),
                org.mockito.ArgumentMatchers.eq("HIGH"),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.anyMap());
    }

    @Test
    void deniesPrivilegedActionWithoutMfa() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "fraud-admin",
                "N/A",
                List.of(new SimpleGrantedAuthority("ROLE_FRAUD_ADMIN"))));

        assertThrows(AccessDeniedException.class, () -> service.authorizePrivilegedAction("fraud_override_user", "user-2"));
    }

    @Test
    void allowsPrivilegedActionWithRoleAndMfaAuthority() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "fraud-admin",
                "N/A",
                List.of(
                        new SimpleGrantedAuthority("ROLE_FRAUD_ADMIN"),
                        new SimpleGrantedAuthority("MFA_OTP"))));

        assertDoesNotThrow(() -> service.authorizePrivilegedAction("aml_case_resolve", "case-1"));
        verify(alertStreamingService).stream(
                org.mockito.ArgumentMatchers.eq("wallet.security.privileged_access_granted"),
                org.mockito.ArgumentMatchers.eq("INFO"),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.anyMap());
    }

    @Test
    void allowsPrivilegedActionWithMfaDetails() {
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                "fraud-admin",
                "N/A",
                List.of(new SimpleGrantedAuthority("ROLE_FRAUD_ADMIN")));
        authentication.setDetails(Map.of("mfaVerified", true));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        assertDoesNotThrow(() -> service.authorizePrivilegedAction("fraud_override_target", "target-1"));
    }
}
