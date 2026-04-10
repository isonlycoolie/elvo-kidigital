package com.elvo.identity.service.impl;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.elvo.identity.entity.User;
import com.elvo.identity.repository.UserRepository;
import com.elvo.identity.security.Base32Codec;
import com.elvo.identity.security.TotpCodeService;
import com.elvo.identity.security.TotpSecretCryptoService;
import com.elvo.identity.service.TotpManagementService;

@Service
public class RedisTotpManagementService implements TotpManagementService {

    private static final int SECRET_BYTES = 20;

    private final StringRedisTemplate redisTemplate;
    private final UserRepository userRepository;
    private final TotpCodeService totpCodeService;
    private final TotpSecretCryptoService cryptoService;
    private final Duration enrollmentTtl;
    private final String pendingKeyPrefix;
    private final String activeKeyPrefix;
    private final String issuer;
    private final SecureRandom secureRandom = new SecureRandom();

    public RedisTotpManagementService(
            StringRedisTemplate redisTemplate,
            UserRepository userRepository,
            TotpCodeService totpCodeService,
            TotpSecretCryptoService cryptoService,
            @Value("${elvo.security.totp.enrollment-ttl-minutes:10}") long enrollmentTtlMinutes,
            @Value("${elvo.security.totp.pending-key-prefix:elvo:totp:pending:}") String pendingKeyPrefix,
            @Value("${elvo.security.totp.active-key-prefix:elvo:totp:active:}") String activeKeyPrefix,
            @Value("${elvo.security.totp.issuer:ELVO}") String issuer) {
        this.redisTemplate = redisTemplate;
        this.userRepository = userRepository;
        this.totpCodeService = totpCodeService;
        this.cryptoService = cryptoService;
        this.enrollmentTtl = Duration.ofMinutes(Math.max(1, enrollmentTtlMinutes));
        this.pendingKeyPrefix = pendingKeyPrefix;
        this.activeKeyPrefix = activeKeyPrefix;
        this.issuer = issuer;
    }

    @Override
    public Enrollment startEnrollment(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        byte[] bytes = new byte[SECRET_BYTES];
        secureRandom.nextBytes(bytes);
        String secret = Base32Codec.encode(bytes);
        String encrypted = cryptoService.encrypt(secret);
        redisTemplate.opsForValue().set(pendingKey(userId), encrypted, enrollmentTtl);

        Instant expiresAt = Instant.now().plus(enrollmentTtl);
        String label = user.getEmail() != null && !user.getEmail().isBlank() ? user.getEmail() : user.getPhone();
        String otpauth = buildOtpAuthUrl(label, secret);
        return new Enrollment(secret, otpauth, expiresAt);
    }

    @Override
    public boolean confirmEnrollment(UUID userId, String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        String encrypted = redisTemplate.opsForValue().get(pendingKey(userId));
        if (encrypted == null || encrypted.isBlank()) {
            return false;
        }
        String secret = cryptoService.decrypt(encrypted);
        boolean verified = totpCodeService.verify(secret, code.trim(), Instant.now());
        if (!verified) {
            return false;
        }

        redisTemplate.opsForValue().set(activeKey(userId), cryptoService.encrypt(secret));
        redisTemplate.delete(pendingKey(userId));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        user.setMfaEnabled(true);
        userRepository.save(user);
        return true;
    }

    @Override
    public boolean verifyActiveCode(UUID userId, String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        String encrypted = redisTemplate.opsForValue().get(activeKey(userId));
        if (encrypted == null || encrypted.isBlank()) {
            return false;
        }
        String secret = cryptoService.decrypt(encrypted);
        return totpCodeService.verify(secret, code.trim(), Instant.now());
    }

    private String buildOtpAuthUrl(String label, String secret) {
        String normalizedLabel = label == null || label.isBlank() ? "user" : label.trim();
        return "otpauth://totp/"
                + url(issuer)
                + ":"
                + url(normalizedLabel)
                + "?secret="
                + secret
                + "&issuer="
                + url(issuer);
    }

    private String url(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String pendingKey(UUID userId) {
        return pendingKeyPrefix + userId;
    }

    private String activeKey(UUID userId) {
        return activeKeyPrefix + userId;
    }
}