package com.elvo.identity.util;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.elvo.identity.security.SecretManagerService;
import com.elvo.identity.security.TokenRevocationChecker;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.Jwts.SIG;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import io.jsonwebtoken.io.Decoders;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

@Component
public class TokenService {

    private static final String TOKEN_TYPE_CLAIM = "tokenType";
    private static final String ACCESS_TOKEN_TYPE = "ACCESS";
    private static final String REFRESH_TOKEN_TYPE = "REFRESH";
    private static final String EAN_CLAIM = "ean";
    private static final String ROLES_CLAIM = "roles";
    private static final String SCOPES_CLAIM = "scopes";
    private static final List<String> DEFAULT_ROLES = List.of("USER");
    private static final List<String> DEFAULT_SCOPES = List.of("wallet:read", "wallet:write");

    private final SecretKey signingKey;
    private final PrivateKey signingPrivateKey;
    private final PublicKey verificationPublicKey;
    private final PublicKey previousVerificationPublicKey;
    private final String signingKeyId;
    private final String previousSigningKeyId;
    private final boolean asymmetricSigningEnabled;
    private final TokenRevocationChecker tokenRevocationChecker;
    private final String issuer;
    private final String audience;
    private final long accessTokenTtlMinutes;
    private final long refreshTokenTtlDays;

    public TokenService(@Value("${elvo.security.jwt.secret}") String jwtSecret,
                        @Value("${elvo.security.jwt.signing.private-key-pem:}") String privateKeyPem,
                        @Value("${elvo.security.jwt.signing.public-key-pem:}") String publicKeyPem,
                        @Value("${elvo.security.jwt.signing.previous-public-key-pem:}") String previousPublicKeyPem,
                        @Value("${elvo.security.jwt.signing.key-id:}") String signingKeyId,
                        @Value("${elvo.security.jwt.signing.previous-key-id:}") String previousSigningKeyId,
                        @Value("${elvo.security.jwt.issuer:elvo-identity-service}") String issuer,
                        @Value("${elvo.security.jwt.audience:elvo-platform}") String audience,
                        @Value("${elvo.security.jwt.access-token-ttl-minutes:15}") long accessTokenTtlMinutes,
                        @Value("${elvo.security.jwt.refresh-token-ttl-days:7}") long refreshTokenTtlDays,
                        SecretManagerService secretManagerService,
                        TokenRevocationChecker tokenRevocationChecker) {
        this(secretManagerService.resolve(
                "identity-jwt-secret",
                jwtSecret,
                "ELVO_JWT_SECRET",
                null),
            secretManagerService.resolve(
                "identity-jwt-signing-private-key",
                privateKeyPem,
                "ELVO_JWT_SIGNING_PRIVATE_KEY_PEM",
                null),
            secretManagerService.resolve(
                "identity-jwt-signing-public-key",
                publicKeyPem,
                "ELVO_JWT_SIGNING_PUBLIC_KEY_PEM",
                null),
            secretManagerService.resolve(
                "identity-jwt-signing-previous-public-key",
                previousPublicKeyPem,
                "ELVO_JWT_SIGNING_PREVIOUS_PUBLIC_KEY_PEM",
                null),
            signingKeyId,
            previousSigningKeyId,
            issuer,
            audience,
            accessTokenTtlMinutes,
            refreshTokenTtlDays,
            tokenRevocationChecker,
            true);
    }

    public TokenService(String jwtSecret,
                        String privateKeyPem,
                        String publicKeyPem,
                        String previousPublicKeyPem,
                        String signingKeyId,
                        String previousSigningKeyId,
                        String issuer,
                        String audience,
                        long accessTokenTtlMinutes,
                        long refreshTokenTtlDays) {
        this(jwtSecret,
            privateKeyPem,
            publicKeyPem,
            previousPublicKeyPem,
            signingKeyId,
            previousSigningKeyId,
            issuer,
            audience,
            accessTokenTtlMinutes,
            refreshTokenTtlDays,
            jti -> false,
            false);
    }

    public TokenService(String jwtSecret,
                        String privateKeyPem,
                        String publicKeyPem,
                        String signingKeyId,
                        String issuer,
                        String audience,
                        long accessTokenTtlMinutes,
                        long refreshTokenTtlDays) {
        this(jwtSecret,
            privateKeyPem,
            publicKeyPem,
            null,
            signingKeyId,
            null,
            issuer,
            audience,
            accessTokenTtlMinutes,
            refreshTokenTtlDays,
            jti -> false,
            false);
    }

    private TokenService(String jwtSecret,
                         String privateKeyPem,
                         String publicKeyPem,
                         String previousPublicKeyPem,
                         String signingKeyId,
                         String previousSigningKeyId,
                         String issuer,
                         String audience,
                         long accessTokenTtlMinutes,
                         long refreshTokenTtlDays,
                         TokenRevocationChecker tokenRevocationChecker,
                         boolean ignored) {
        if (hasText(privateKeyPem) || hasText(publicKeyPem) || hasText(signingKeyId)) {
            this.signingPrivateKey = parsePrivateKey(privateKeyPem);
            this.verificationPublicKey = parsePublicKey(publicKeyPem);
            this.signingKeyId = requireText(signingKeyId, "elvo.security.jwt.signing.key-id must be configured");
            this.previousVerificationPublicKey = hasText(previousPublicKeyPem) ? parsePublicKey(previousPublicKeyPem) : null;
            this.previousSigningKeyId = hasText(previousSigningKeyId) ? requireText(previousSigningKeyId, "elvo.security.jwt.signing.previous-key-id must be configured") : null;
            this.signingKey = null;
            this.asymmetricSigningEnabled = true;
        } else {
            validateJwtSecret(jwtSecret);
            this.signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
            this.signingPrivateKey = null;
            this.verificationPublicKey = null;
            this.previousVerificationPublicKey = null;
            this.signingKeyId = null;
            this.previousSigningKeyId = null;
            this.asymmetricSigningEnabled = false;
        }
        this.tokenRevocationChecker = tokenRevocationChecker;
        this.issuer = issuer;
        this.audience = audience;
        this.accessTokenTtlMinutes = accessTokenTtlMinutes;
        this.refreshTokenTtlDays = refreshTokenTtlDays;
    }

    TokenService(PrivateKey signingPrivateKey,
                 PublicKey verificationPublicKey,
                 String issuer,
                 String audience,
                 String signingKeyId,
                 long accessTokenTtlMinutes,
                 long refreshTokenTtlDays) {
        this(signingPrivateKey,
            verificationPublicKey,
            null,
            issuer,
            audience,
            signingKeyId,
            null,
            accessTokenTtlMinutes,
            refreshTokenTtlDays,
            jti -> false);
    }

    TokenService(PrivateKey signingPrivateKey,
                 PublicKey verificationPublicKey,
                 PublicKey previousVerificationPublicKey,
                 String issuer,
                 String audience,
                 String signingKeyId,
                 String previousSigningKeyId,
                 long accessTokenTtlMinutes,
                 long refreshTokenTtlDays) {
        this(signingPrivateKey,
            verificationPublicKey,
            previousVerificationPublicKey,
            issuer,
            audience,
            signingKeyId,
            previousSigningKeyId,
            accessTokenTtlMinutes,
            refreshTokenTtlDays,
            jti -> false);
    }

    TokenService(PrivateKey signingPrivateKey,
                 PublicKey verificationPublicKey,
                 PublicKey previousVerificationPublicKey,
                 String issuer,
                 String audience,
                 String signingKeyId,
                 String previousSigningKeyId,
                 long accessTokenTtlMinutes,
                 long refreshTokenTtlDays,
                 TokenRevocationChecker tokenRevocationChecker) {
        this.signingPrivateKey = signingPrivateKey;
        this.verificationPublicKey = verificationPublicKey;
        this.signingKeyId = requireText(signingKeyId, "signing key id must be configured");
        this.previousVerificationPublicKey = previousVerificationPublicKey;
        this.previousSigningKeyId = previousSigningKeyId;
        this.signingKey = null;
        this.asymmetricSigningEnabled = true;
        this.tokenRevocationChecker = tokenRevocationChecker;
        this.issuer = issuer;
        this.audience = audience;
        this.accessTokenTtlMinutes = accessTokenTtlMinutes;
        this.refreshTokenTtlDays = refreshTokenTtlDays;
    }

    private void validateJwtSecret(String jwtSecret) {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException("elvo.security.jwt.secret must be configured");
        }
        if (jwtSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("elvo.security.jwt.secret must be at least 32 bytes");
        }
    }

    public TokenPayload generateAccessToken(UUID userId, String ean) {
        return generateAccessToken(userId, ean, DEFAULT_ROLES, DEFAULT_SCOPES);
    }

    public TokenPayload generateAccessToken(UUID userId, String ean, List<String> roles, List<String> scopes) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(accessTokenTtlMinutes * 60);
        var builder = Jwts.builder()
            .issuer(issuer)
            .audience().add(audience).and()
                .subject(userId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .claim(EAN_CLAIM, ean)
                .claim(ROLES_CLAIM, sanitizeRequiredStringListClaim(roles, ROLES_CLAIM))
                .claim(SCOPES_CLAIM, sanitizeRequiredStringListClaim(scopes, SCOPES_CLAIM))
                .claim(TOKEN_TYPE_CLAIM, ACCESS_TOKEN_TYPE)
                .id(UUID.randomUUID().toString());
            String token = sign(builder);
        return new TokenPayload(token, expiresAt);
    }

    public TokenPayload generateRefreshToken(UUID userId) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(refreshTokenTtlDays * 24 * 60 * 60);
        var builder = Jwts.builder()
            .issuer(issuer)
            .audience().add(audience).and()
                .subject(userId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .claim(TOKEN_TYPE_CLAIM, REFRESH_TOKEN_TYPE)
            .id(UUID.randomUUID().toString());
        String token = sign(builder);
        return new TokenPayload(token, expiresAt);
    }

    public AccessTokenClaims validateAccessToken(String token) {
        Claims claims = parseClaims(token);
        if (!ACCESS_TOKEN_TYPE.equals(claims.get(TOKEN_TYPE_CLAIM, String.class))) {
            throw new IllegalArgumentException("Token type is invalid");
        }

        UUID userId = parseRequiredUuid(claims.getSubject());
        String ean = claims.get(EAN_CLAIM, String.class);
        if (ean == null || ean.isBlank()) {
            throw new IllegalArgumentException("Token payload is invalid");
        }
        List<String> roles = parseRequiredStringListClaim(claims.get(ROLES_CLAIM), ROLES_CLAIM);
        List<String> scopes = parseRequiredStringListClaim(claims.get(SCOPES_CLAIM), SCOPES_CLAIM);
        String jti = parseRequiredJti(claims);
        ensureNotRevoked(jti);

        return new AccessTokenClaims(userId, ean, roles, scopes, jti, claims.getExpiration().toInstant());
    }

    public RefreshTokenClaims validateRefreshToken(String token) {
        Claims claims = parseClaims(token);
        if (!REFRESH_TOKEN_TYPE.equals(claims.get(TOKEN_TYPE_CLAIM, String.class))) {
            throw new IllegalArgumentException("Token type is invalid");
        }

        UUID userId = parseRequiredUuid(claims.getSubject());
        String jti = parseRequiredJti(claims);
        ensureNotRevoked(jti);
        return new RefreshTokenClaims(userId, jti, claims.getExpiration().toInstant());
    }

    public JwksDocument getJwksDocument() {
        if (!asymmetricSigningEnabled) {
            throw new IllegalStateException("JWKS requires asymmetric signing configuration");
        }
        if (previousVerificationPublicKey == null) {
            return new JwksDocument(List.of(buildRsaJwk(verificationPublicKey, signingKeyId)));
        }
        return new JwksDocument(List.of(
                buildRsaJwk(verificationPublicKey, signingKeyId),
                buildRsaJwk(previousVerificationPublicKey, requireText(previousSigningKeyId, "elvo.security.jwt.signing.previous-key-id must be configured"))));
    }

    private Claims parseClaims(String token) {
        try {
            if (asymmetricSigningEnabled) {
                String keyId = resolveKeyId(token);
                PublicKey verificationKey = resolveVerificationKey(keyId);
                if (verificationKey == null) {
                    throw new IllegalArgumentException("Token key id is invalid");
                }
                return Jwts.parser()
                        .verifyWith(verificationKey)
                        .requireIssuer(issuer)
                        .requireAudience(audience)
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();
            }

            return Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(issuer)
                    .requireAudience(audience)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException ex) {
            throw new IllegalArgumentException("Token is invalid", ex);
        }
    }

    private String sign(io.jsonwebtoken.JwtBuilder builder) {
        if (asymmetricSigningEnabled) {
            return builder
                    .header().keyId(signingKeyId).and()
                    .signWith(signingPrivateKey, SIG.RS256)
                    .compact();
        }
        return builder
                .signWith(signingKey)
                .compact();
    }

    private String resolveKeyId(String token) {
        try {
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
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Token key id is invalid", ex);
        }
    }

    private PublicKey resolveVerificationKey(String keyId) {
        if (signingKeyId.equals(keyId)) {
            return verificationPublicKey;
        }
        if (previousVerificationPublicKey != null && previousSigningKeyId != null && previousSigningKeyId.equals(keyId)) {
            return previousVerificationPublicKey;
        }
        return null;
    }

    private JwkKey buildRsaJwk(PublicKey publicKey, String keyId) {
        if (!(publicKey instanceof RSAPublicKey rsaPublicKey)) {
            throw new IllegalStateException("JWKS currently supports only RSA keys");
        }
        String modulus = Base64.getUrlEncoder().withoutPadding().encodeToString(toUnsignedBytes(rsaPublicKey.getModulus().toByteArray()));
        String exponent = Base64.getUrlEncoder().withoutPadding().encodeToString(toUnsignedBytes(rsaPublicKey.getPublicExponent().toByteArray()));
        return new JwkKey(keyId, "RSA", "RS256", "sig", modulus, exponent);
    }

    private byte[] toUnsignedBytes(byte[] value) {
        if (value.length > 1 && value[0] == 0) {
            byte[] normalized = new byte[value.length - 1];
            System.arraycopy(value, 1, normalized, 0, normalized.length);
            return normalized;
        }
        return value;
    }

    private PrivateKey parsePrivateKey(String pem) {
        String value = requireText(pem, "elvo.security.jwt.signing.private-key-pem must be configured");
        try {
            String normalized = value
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] decoded = Decoders.BASE64.decode(normalized);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decoded);
            return KeyFactory.getInstance("RSA").generatePrivate(spec);
        } catch (Exception ex) {
            throw new IllegalStateException("Invalid private key configuration", ex);
        }
    }

    private PublicKey parsePublicKey(String pem) {
        String value = requireText(pem, "elvo.security.jwt.signing.public-key-pem must be configured");
        try {
            String normalized = value
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] decoded = Decoders.BASE64.decode(normalized);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);
            return KeyFactory.getInstance("RSA").generatePublic(spec);
        } catch (Exception ex) {
            throw new IllegalStateException("Invalid public key configuration", ex);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String requireText(String value, String message) {
        if (!hasText(value)) {
            throw new IllegalStateException(message);
        }
        return value;
    }

    private UUID parseRequiredUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Token subject is invalid", ex);
        }
    }

    private String parseRequiredJti(Claims claims) {
        String jti = claims.getId();
        if (jti == null || jti.isBlank()) {
            throw new IllegalArgumentException("Token identifier is invalid");
        }
        return jti;
    }

    private void ensureNotRevoked(String jti) {
        if (tokenRevocationChecker.isRevoked(jti)) {
            throw new IllegalArgumentException("Token is revoked");
        }
    }

    private List<String> sanitizeRequiredStringListClaim(List<String> values, String claimName) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("Token " + claimName + " claim is invalid");
        }
        List<String> sanitized = values.stream()
                .filter(v -> v != null && !v.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        if (sanitized.isEmpty()) {
            throw new IllegalArgumentException("Token " + claimName + " claim is invalid");
        }
        return sanitized;
    }

    private List<String> parseRequiredStringListClaim(Object claimValue, String claimName) {
        if (!(claimValue instanceof List<?> rawValues) || rawValues.isEmpty()) {
            throw new IllegalArgumentException("Token " + claimName + " claim is invalid");
        }
        List<String> parsed = rawValues.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(String::trim)
                .filter(v -> !v.isBlank())
                .distinct()
                .toList();
        if (parsed.isEmpty() || parsed.size() != rawValues.size()) {
            throw new IllegalArgumentException("Token " + claimName + " claim is invalid");
        }
        return parsed;
    }

    public record TokenPayload(String token, Instant expiresAt) {
    }

    public record AccessTokenClaims(UUID userId, String ean, List<String> roles, List<String> scopes, String jti, Instant expiresAt) {
    }

    public record RefreshTokenClaims(UUID userId, String jti, Instant expiresAt) {
    }

    public record JwksDocument(List<JwkKey> keys) {
    }

    public record JwkKey(String kid, String kty, String alg, String use, String n, String e) {
    }
}
