package com.elvo.wallet.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.elvo.wallet.exception.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class UserJwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String USER_PATH_PREFIX = "/wallets/";
    private static final String TOKEN_TYPE_CLAIM = "tokenType";
    private static final String ACCESS_TOKEN_TYPE = "ACCESS";
    private static final String EAN_CLAIM = "ean";

    private final UserJwtProperties jwtProperties;
    private final ObjectMapper objectMapper;
    private final UserTokenRevocationChecker tokenRevocationChecker;
    private final IdentityJwksKeyResolver identityJwksKeyResolver;
    private final String resolvedJwtSecret;
    private final String resolvedSigningPublicKeyPem;
    private final String resolvedPreviousSigningPublicKeyPem;
    private final String resolvedSigningKeyId;
    private final String resolvedPreviousSigningKeyId;

    public UserJwtAuthenticationFilter(UserJwtProperties jwtProperties, ObjectMapper objectMapper) {
        this(jwtProperties, objectMapper, jti -> false, null, null);
    }

    public UserJwtAuthenticationFilter(UserJwtProperties jwtProperties,
                                       ObjectMapper objectMapper,
                                       UserTokenRevocationChecker tokenRevocationChecker) {
        this(jwtProperties, objectMapper, tokenRevocationChecker, null, null);
    }

    public UserJwtAuthenticationFilter(UserJwtProperties jwtProperties,
                                       ObjectMapper objectMapper,
                                       UserTokenRevocationChecker tokenRevocationChecker,
                                       SecretManagerService secretManagerService) {
        this(jwtProperties, objectMapper, tokenRevocationChecker, secretManagerService, null);
    }

    public UserJwtAuthenticationFilter(UserJwtProperties jwtProperties,
                                       ObjectMapper objectMapper,
                                       UserTokenRevocationChecker tokenRevocationChecker,
                                       SecretManagerService secretManagerService,
                                       IdentityJwksKeyResolver identityJwksKeyResolver) {
        this.jwtProperties = jwtProperties;
        this.objectMapper = objectMapper;
        this.tokenRevocationChecker = tokenRevocationChecker;
        this.identityJwksKeyResolver = identityJwksKeyResolver;
        if (secretManagerService == null) {
            this.resolvedJwtSecret = jwtProperties.getSecret();
            this.resolvedSigningPublicKeyPem = jwtProperties.getSigningPublicKeyPem();
            this.resolvedPreviousSigningPublicKeyPem = jwtProperties.getPreviousSigningPublicKeyPem();
            this.resolvedSigningKeyId = jwtProperties.getSigningKeyId();
            this.resolvedPreviousSigningKeyId = jwtProperties.getPreviousSigningKeyId();
        } else {
            this.resolvedJwtSecret = secretManagerService.resolve(
                    "wallet-user-jwt-secret",
                    jwtProperties.getSecret(),
                    "ELVO_JWT_SECRET",
                    null);
            this.resolvedSigningPublicKeyPem = secretManagerService.resolve(
                    "wallet-user-jwt-signing-public-key",
                    jwtProperties.getSigningPublicKeyPem(),
                    "ELVO_JWT_SIGNING_PUBLIC_KEY_PEM",
                    null);
            this.resolvedPreviousSigningPublicKeyPem = secretManagerService.resolve(
                    "wallet-user-jwt-signing-previous-public-key",
                    jwtProperties.getPreviousSigningPublicKeyPem(),
                    "ELVO_JWT_SIGNING_PREVIOUS_PUBLIC_KEY_PEM",
                    null);
            this.resolvedSigningKeyId = jwtProperties.getSigningKeyId();
            this.resolvedPreviousSigningKeyId = jwtProperties.getPreviousSigningKeyId();
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null || !path.startsWith(USER_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            forbidden(response, "Missing or invalid user bearer token");
            return;
        }

        String token = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
        try {
            Claims claims = parseClaims(token);
            if (!ACCESS_TOKEN_TYPE.equals(claims.get(TOKEN_TYPE_CLAIM, String.class))) {
                forbidden(response, "Token type is invalid");
                return;
            }

            String jti = claims.getId();
            if (jti == null || jti.isBlank()) {
                forbidden(response, "Token identifier is invalid");
                return;
            }
            if (tokenRevocationChecker.isRevoked(jti)) {
                forbidden(response, "Token is revoked");
                return;
            }

            String ean = claims.get(EAN_CLAIM, String.class);
            if (ean == null || ean.isBlank()) {
                forbidden(response, "Token payload is invalid");
                return;
            }

            Collection<? extends GrantedAuthority> authorities = extractAuthorities(claims);
            List<String> scopes = extractScopes(claims);
            if (authorities.isEmpty() || scopes.isEmpty()) {
                forbidden(response, "Token claims are invalid");
                return;
            }

            UUID subject = UUID.fromString(claims.getSubject());
            UserJwtPrincipal principal = new UserJwtPrincipal(subject, ean, scopes);
            UsernamePasswordAuthenticationToken authenticationToken =
                    new UsernamePasswordAuthenticationToken(principal, "N/A", authorities);
            SecurityContextHolder.getContext().setAuthentication(authenticationToken);
            filterChain.doFilter(request, response);
        } catch (Exception ex) {
            forbidden(response, "Invalid user token");
        }
    }

    private Claims parseClaims(String token) {
        if (hasText(resolvedSigningPublicKeyPem) || identityJwksKeyResolver != null) {
            String tokenKeyId = resolveKeyId(token);
            PublicKey publicKey = resolvePublicKey(tokenKeyId);

            return Jwts.parser()
                    .verifyWith(publicKey)
                    .requireIssuer(jwtProperties.getIssuer())
                    .requireAudience(jwtProperties.getAudience())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        }

        if (!hasText(resolvedJwtSecret)) {
            throw new IllegalStateException("elvo.security.jwt.secret must be configured");
        }
        SecretKey secretKey = Keys.hmacShaKeyFor(resolvedJwtSecret.getBytes(StandardCharsets.UTF_8));
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

    private List<String> extractScopes(Claims claims) {
        Object scopesObj = claims.get("scopes");
        if (!(scopesObj instanceof List<?> scopes) || scopes.isEmpty()) {
            return List.of();
        }
        return scopes.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(String::trim)
                .filter(scope -> !scope.isBlank())
                .distinct()
                .toList();
    }

    private PublicKey parsePublicKey(String pem) {
        try {
            String normalized = pem
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] decoded = Base64.getDecoder().decode(normalized);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);
            return KeyFactory.getInstance("RSA").generatePublic(spec);
        } catch (Exception ex) {
            throw new IllegalStateException("Invalid user JWT public key configuration", ex);
        }
    }

    private PublicKey resolvePublicKey(String tokenKeyId) {
        if (identityJwksKeyResolver != null) {
            return identityJwksKeyResolver.resolve(tokenKeyId);
        }

        if (!hasText(tokenKeyId)) {
            return parsePublicKey(resolvedSigningPublicKeyPem);
        }
        if (!hasText(resolvedSigningKeyId)) {
            return parsePublicKey(resolvedSigningPublicKeyPem);
        }
        if (resolvedSigningKeyId.equals(tokenKeyId)) {
            return parsePublicKey(resolvedSigningPublicKeyPem);
        }
        if (hasText(resolvedPreviousSigningPublicKeyPem)
                && hasText(resolvedPreviousSigningKeyId)
                && resolvedPreviousSigningKeyId.equals(tokenKeyId)) {
            return parsePublicKey(resolvedPreviousSigningPublicKeyPem);
        }
        return null;
    }

    private String resolveKeyId(String token) {
        String[] parts = token.split("\\.");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Token key id is invalid");
        }
        String headerJson = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
        int kidKeyIndex = headerJson.indexOf("\"kid\"");
        if (kidKeyIndex < 0) {
            throw new IllegalArgumentException("Token key id is invalid");
        }
        int colonIndex = headerJson.indexOf(':', kidKeyIndex);
        int startQuote = headerJson.indexOf('"', colonIndex + 1);
        int endQuote = headerJson.indexOf('"', startQuote + 1);
        if (startQuote < 0 || endQuote < 0) {
            throw new IllegalArgumentException("Token key id is invalid");
        }
        return headerJson.substring(startQuote + 1, endQuote);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private void forbidden(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), ApiResponse.error("ACCESS_DENIED", message));
    }
}
