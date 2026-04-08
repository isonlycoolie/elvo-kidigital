package com.elvo.billing.security;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.elvo.billing.monitoring.SecurityMonitoringService;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

@Component
public class BillingRoleBasedAccessControl {

    private final Map<BillingSensitivePermission, Set<String>> roleMatrix = new EnumMap<>(BillingSensitivePermission.class);
    private SecurityMonitoringService securityMonitoringService;
    private BillingMfaService billingMfaService;

    public BillingRoleBasedAccessControl() {
        roleMatrix.put(BillingSensitivePermission.PAYMENT_REVERSE, Set.of("ROLE_OPERATIONS_ADMIN", "ROLE_BILLING_ADMIN"));
        roleMatrix.put(BillingSensitivePermission.PAYMENT_REFUND, Set.of("ROLE_OPERATIONS_ADMIN", "ROLE_BILLING_ADMIN"));
        roleMatrix.put(BillingSensitivePermission.MANUAL_OVERRIDE, Set.of("ROLE_OPERATIONS_ADMIN", "ROLE_BILLING_ADMIN"));
        roleMatrix.put(BillingSensitivePermission.AUDIT_READ, Set.of("ROLE_AUDIT_ADMIN", "ROLE_BILLING_AUDITOR"));
    }

    public void authorize(BillingSensitivePermission permission) {
        if (!isAllowed(permission)) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String principal = authentication == null ? "anonymous" : authentication.getName();
            if (securityMonitoringService != null) {
                securityMonitoringService.recordPrivilegeEscalationAttempt(principal, permission == null ? "unknown" : permission.name());
            }
            throw new AccessDeniedException("RBAC denied for permission " + permission);
        }

        if (billingMfaService != null) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (!billingMfaService.isMfaSatisfied(authentication, permission)) {
                String principal = authentication == null ? "anonymous" : authentication.getName();
                if (securityMonitoringService != null) {
                    securityMonitoringService.recordSuspiciousEvent(
                            "billing.security.mfa_required",
                            "mfa_required_for_sensitive_permission",
                            Map.of("principal", principal, "permission", permission.name()));
                }
                throw new AccessDeniedException("MFA required for permission " + permission);
            }
        }
    }

    @Autowired(required = false)
    void setSecurityMonitoringService(@Nullable SecurityMonitoringService securityMonitoringService) {
        this.securityMonitoringService = securityMonitoringService;
    }

    @Autowired(required = false)
    void setBillingMfaService(@Nullable BillingMfaService billingMfaService) {
        this.billingMfaService = billingMfaService;
    }

    public boolean isAllowed(BillingSensitivePermission permission) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        Set<String> allowedRoles = roleMatrix.get(permission);
        if (allowedRoles == null || allowedRoles.isEmpty()) {
            return false;
        }

        for (GrantedAuthority authority : authentication.getAuthorities()) {
            String role = authority.getAuthority();
            if (role == null || role.isBlank()) {
                continue;
            }
            String normalized = role.toUpperCase(Locale.ROOT);
            if (allowedRoles.contains(normalized)) {
                return true;
            }
        }

        return false;
    }
}
