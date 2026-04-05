package com.elvo.wallet.security;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class WalletBruteForceProtectionFilter extends OncePerRequestFilter {

    private final WalletBruteForceGuardService guardService;

    public WalletBruteForceProtectionFilter(WalletBruteForceGuardService guardService) {
        this.guardService = guardService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null || !path.startsWith("/wallets/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String clientKey = WalletClientKeyResolver.resolve(request);
        if (guardService.isLocked(clientKey)) {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"success\":false,\"code\":\"TOO_MANY_ATTEMPTS\",\"message\":\"Authentication temporarily locked due to repeated failures\"}");
            return;
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()) {
                guardService.clear(clientKey);
            }
        }
    }
}
