package com.elvo.billing.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class BillingRoleBasedAccessControlTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldAllowOperationsAdminForPaymentReverse() {
        BillingRoleBasedAccessControl rbac = new BillingRoleBasedAccessControl();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "ops-admin",
                "N/A",
                List.of(new SimpleGrantedAuthority("ROLE_OPERATIONS_ADMIN"))));

        assertThat(rbac.isAllowed(BillingSensitivePermission.PAYMENT_REVERSE)).isTrue();
    }

    @Test
    void shouldRejectRegularBillingUserForPaymentReverse() {
        BillingRoleBasedAccessControl rbac = new BillingRoleBasedAccessControl();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "billing-user",
                "N/A",
                List.of(new SimpleGrantedAuthority("ROLE_BILLING_USER"))));

        assertThat(rbac.isAllowed(BillingSensitivePermission.PAYMENT_REVERSE)).isFalse();
    }

    @Test
    void shouldAllowAuditAdminForAuditRead() {
        BillingRoleBasedAccessControl rbac = new BillingRoleBasedAccessControl();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "audit-admin",
                "N/A",
                List.of(new SimpleGrantedAuthority("ROLE_AUDIT_ADMIN"))));

        assertThat(rbac.isAllowed(BillingSensitivePermission.AUDIT_READ)).isTrue();
    }
}
