package com.elvo.wallet.security;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.elvo.wallet.monitoring.SecurityAlertStreamingService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;

@Service
public class WalletPrivilegedAccessControlService {

    private final List<String> privilegedRoles = List.of("ROLE_FRAUD_ADMIN", "ROLE_COMPLIANCE_ADMIN", "ROLE_WALLET_ADMIN");
    private SecurityAlertStreamingService securityAlertStreamingService;

    @Value("${elvo.wallet.security.privileged-access.require-mfa:true}")
    private boolean requireMfa = true;

    @Autowired(required = false)
    void setSecurityAlertStreamingService(@Nullable SecurityAlertStreamingService securityAlertStreamingService) {
        this.securityAlertStreamingService = securityAlertStreamingService;
    }

    public void authorizePrivilegedAction(String action, String targetIdentifier) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String principal = authentication == null ? "anonymous" : authentication.getName();

        if (!hasPrivilegedRole(authentication)) {
            stream("wallet.security.privileged_access_denied", "HIGH", principal, action, targetIdentifier, "missing_role");
            throw new AccessDeniedException("Privileged role required for action " + action);
        }

        if (requireMfa && !hasMfa(authentication)) {
            stream("wallet.security.privileged_access_denied", "HIGH", principal, action, targetIdentifier, "mfa_required");
            throw new AccessDeniedException("MFA required for privileged action " + action);
        }

        stream("wallet.security.privileged_access_granted", "INFO", principal, action, targetIdentifier, "authorized");
    }

    private boolean hasPrivilegedRole(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        for (GrantedAuthority authority : authentication.getAuthorities()) {
            String value = authority == null ? null : authority.getAuthority();
            if (value == null) {
                continue;
            }
            String normalized = value.toUpperCase(Locale.ROOT);
            if (privilegedRoles.contains(normalized)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasMfa(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        for (GrantedAuthority authority : authentication.getAuthorities()) {
            String value = authority == null ? null : authority.getAuthority();
            if (value == null) {
                continue;
            }
            String normalized = value.toUpperCase(Locale.ROOT);
            if (normalized.equals("MFA_OTP") || normalized.equals("MFA_AUTHENTICATOR_APP") || normalized.equals("MFA_HARDWARE_TOKEN")) {
                return true;
            }
        }

        Object details = authentication.getDetails();
        if (details instanceof Map<?, ?> map) {
            Object verified = map.get("mfaVerified");
            if (Boolean.TRUE.equals(verified)) {
                return true;
            }
            Object method = map.get("mfaMethod");
            if (method != null) {
                String normalizedMethod = String.valueOf(method).trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
                return normalizedMethod.equals("OTP")
                        || normalizedMethod.equals("AUTHENTICATOR_APP")
                        || normalizedMethod.equals("HARDWARE_TOKEN")
                        || normalizedMethod.equals("MFA_OTP")
                        || normalizedMethod.equals("MFA_AUTHENTICATOR_APP")
                        || normalizedMethod.equals("MFA_HARDWARE_TOKEN");
            }
            Object methods = map.get("mfaMethods");
            if (methods instanceof Collection<?> collection) {
                for (Object item : collection) {
                    if (item == null) {
                        continue;
                    }
                    String normalizedMethod = String.valueOf(item).trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
                    if (normalizedMethod.equals("OTP")
                            || normalizedMethod.equals("AUTHENTICATOR_APP")
                            || normalizedMethod.equals("HARDWARE_TOKEN")
                            || normalizedMethod.equals("MFA_OTP")
                            || normalizedMethod.equals("MFA_AUTHENTICATOR_APP")
                            || normalizedMethod.equals("MFA_HARDWARE_TOKEN")) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private void stream(String eventType, String severity, String principal, String action, String targetIdentifier, String reason) {
        if (securityAlertStreamingService == null) {
            return;
        }
        securityAlertStreamingService.stream(eventType, severity, null, Map.of(
                "principal", principal,
                "action", action,
                "targetIdentifier", targetIdentifier == null ? "unknown" : targetIdentifier,
                "reason", reason));
    }
}
