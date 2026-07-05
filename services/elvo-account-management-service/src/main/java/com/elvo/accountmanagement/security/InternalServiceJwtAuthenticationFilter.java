package com.elvo.accountmanagement.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.crypto.SecretKey;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.elvo.accountmanagement.contract.AccountContracts.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class InternalServiceJwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String INTERNAL_PATH_PREFIX = "/api/v1/internal/accounts";
    private static final String SOURCE_SERVICE_HEADER = "X-Source-Service";
    private static final Set<String> ALLOWED_SOURCE_SERVICES = Set.of(
            "identity-service",
            "wallet-service",
            "billing-service");

    private final InternalServiceJwtProperties jwtProperties;
    private final ObjectMapper objectMapper;

    public InternalServiceJwtAuthenticationFilter(
            InternalServiceJwtProperties jwtProperties,
            ObjectMapper objectMapper) {
        this.jwtProperties = jwtProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(INTERNAL_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            forbidden(response, "Missing or invalid service bearer token");
            return;
        }

        String token = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
        try {
            Claims claims = parseClaims(token);
            String sourceService = claims.get(jwtProperties.getSourceServiceClaim(), String.class);
            String serviceIdentity = claims.get(jwtProperties.getServiceIdentityClaim(), String.class);
            String sourceServiceHeader = request.getHeader(SOURCE_SERVICE_HEADER);
            Collection<? extends GrantedAuthority> authorities = extractAuthorities(claims);

            if (sourceService == null || sourceService.isBlank()) {
                forbidden(response, "Missing source service claim");
                return;
            }

            if (!ALLOWED_SOURCE_SERVICES.contains(sourceService.toLowerCase(Locale.ROOT))) {
                forbidden(response, "Source service is not allowed");
                return;
            }

            if (serviceIdentity == null || !sourceService.equalsIgnoreCase(serviceIdentity)) {
                forbidden(response, "Service identity claim does not match source service claim");
                return;
            }

            if (sourceServiceHeader != null && !sourceServiceHeader.isBlank()
                    && !sourceService.equalsIgnoreCase(sourceServiceHeader.trim())) {
                forbidden(response, "Source service header does not match signed token claim");
                return;
            }

            if (authorities.stream().noneMatch(a -> a.getAuthority().equals("ROLE_" + jwtProperties.getRequiredRole()))) {
                forbidden(response, "Caller does not have required service role");
                return;
            }

            UsernamePasswordAuthenticationToken authenticationToken =
                    new UsernamePasswordAuthenticationToken(sourceService, "N/A", authorities);
            SecurityContextHolder.getContext().setAuthentication(authenticationToken);
            filterChain.doFilter(request, response);
        } catch (Exception ex) {
            forbidden(response, "Invalid internal service token");
        }
    }

    private Claims parseClaims(String token) {
        String secret = jwtProperties.getSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("elvo.security.internal-jwt.secret must be configured");
        }
        SecretKey secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));

        return Jwts.parser()
                .verifyWith(secretKey)
                .requireIssuer(jwtProperties.getIssuer())
                .requireAudience(jwtProperties.getAudience())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private Collection<? extends GrantedAuthority> extractAuthorities(Claims claims) {
        Object rolesObj = claims.get("roles");
        if (!(rolesObj instanceof List<?> roles) || roles.isEmpty()) {
            return List.of();
        }

        return roles.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role.toUpperCase(Locale.ROOT))
                .distinct()
                .map(SimpleGrantedAuthority::new)
                .toList();
    }

    private void forbidden(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), ApiResponse.error(message));
    }
}
