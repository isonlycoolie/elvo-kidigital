package com.elvo.wallet.security;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import jakarta.servlet.http.HttpServletRequest;

final class WalletClientKeyResolver {

    private WalletClientKeyResolver() {
    }

    static String resolve(HttpServletRequest request) {
        String remoteAddress = request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
        String username = resolveBasicUsername(request.getHeader("Authorization"));
        return username == null || username.isBlank() ? remoteAddress : username + "@" + remoteAddress;
    }

    private static String resolveBasicUsername(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Basic ")) {
            return null;
        }

        try {
            String encoded = authorizationHeader.substring(6).trim();
            String decoded = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
            int separator = decoded.indexOf(':');
            return separator > 0 ? decoded.substring(0, separator) : null;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
