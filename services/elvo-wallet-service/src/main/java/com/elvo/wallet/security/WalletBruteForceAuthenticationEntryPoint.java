package com.elvo.wallet.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

public class WalletBruteForceAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final WalletBruteForceGuardService guardService;

    public WalletBruteForceAuthenticationEntryPoint(WalletBruteForceGuardService guardService) {
        this.guardService = guardService;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        String clientKey = WalletClientKeyResolver.resolve(request);
        boolean locked = guardService.recordFailure(clientKey);

        response.setStatus(locked ? 429 : HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(locked
                ? "{\"success\":false,\"code\":\"TOO_MANY_ATTEMPTS\",\"message\":\"Too many failed authentication attempts. Try again later.\"}"
                : "{\"success\":false,\"code\":\"UNAUTHORIZED\",\"message\":\"Authentication required\"}");
    }
}
