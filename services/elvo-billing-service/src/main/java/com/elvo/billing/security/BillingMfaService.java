package com.elvo.billing.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;

@Service
public class BillingMfaService {

    public enum MfaMethod {
        OTP,
        AUTHENTICATOR_APP,
        HARDWARE_TOKEN
    }

    @Value("${elvo.security.mfa.required-for-sensitive-permissions:true}")
    private boolean requiredForSensitivePermissions = true;

    @Value("${elvo.security.mfa.allowed-methods:OTP,AUTHENTICATOR_APP,HARDWARE_TOKEN}")
    private String allowedMethods = "OTP,AUTHENTICATOR_APP,HARDWARE_TOKEN";

    public boolean isMfaSatisfied(Authentication authentication, BillingSensitivePermission permission) {
        if (!requiredForSensitivePermissions || !requiresMfa(permission)) {
            return true;
        }
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        return hasAllowedAuthority(authentication) || hasAllowedDetails(authentication);
    }

    public boolean requiresMfa(BillingSensitivePermission permission) {
        return permission == BillingSensitivePermission.PAYMENT_REVERSE
                || permission == BillingSensitivePermission.PAYMENT_REFUND
                || permission == BillingSensitivePermission.MANUAL_OVERRIDE;
    }

    private boolean hasAllowedAuthority(Authentication authentication) {
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            String value = authority == null ? null : authority.getAuthority();
            if (value == null || value.isBlank()) {
                continue;
            }
            String normalized = value.toUpperCase(Locale.ROOT);
            for (MfaMethod method : allowedMethodSet()) {
                if (normalized.equals("MFA_" + method.name())) {
                    return true;
                }
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private boolean hasAllowedDetails(Authentication authentication) {
        Object details = authentication.getDetails();
        if (details instanceof Map<?, ?> map) {
            Object verified = map.get("mfaVerified");
            if (Boolean.TRUE.equals(verified)) {
                return true;
            }
            Object method = map.get("mfaMethod");
            if (method != null && matchesAllowedMethod(String.valueOf(method))) {
                return true;
            }
            Object methods = map.get("mfaMethods");
            if (methods instanceof Collection<?> collection) {
                for (Object item : collection) {
                    if (item != null && matchesAllowedMethod(String.valueOf(item))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean matchesAllowedMethod(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        for (MfaMethod method : allowedMethodSet()) {
            if (normalized.equals(method.name()) || normalized.equals("MFA_" + method.name())) {
                return true;
            }
        }
        return false;
    }

    private EnumSet<MfaMethod> allowedMethodSet() {
        EnumSet<MfaMethod> methods = EnumSet.noneOf(MfaMethod.class);
        if (allowedMethods == null || allowedMethods.isBlank()) {
            return EnumSet.allOf(MfaMethod.class);
        }
        for (String method : allowedMethods.split(",")) {
            String normalized = method.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
            try {
                methods.add(MfaMethod.valueOf(normalized));
            } catch (IllegalArgumentException ignored) {
                // ignore unsupported configuration values
            }
        }
        return methods.isEmpty() ? EnumSet.allOf(MfaMethod.class) : methods;
    }
}