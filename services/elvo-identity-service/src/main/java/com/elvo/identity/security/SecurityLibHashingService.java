package com.elvo.identity.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class SecurityLibHashingService implements SecurityHashingService {

    private final PasswordEncoder passwordEncoder;

    public SecurityLibHashingService(
            @Value("${elvo.security.hash.secret:elvo-identity-pepper}") String secret,
            @Value("${elvo.security.hash.salt-length:16}") int saltLength,
            @Value("${elvo.security.hash.iterations:185000}") int iterations,
            @Value("${elvo.security.hash.algorithm:PBKDF2WithHmacSHA256}") String algorithm
    ) {
        Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm keyFactoryAlgorithm =
                Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm.valueOf(algorithm);
        this.passwordEncoder = new Pbkdf2PasswordEncoder(secret, saltLength, iterations, keyFactoryAlgorithm);
    }

    @Override
    public String hashPassword(String rawValue) {
        return passwordEncoder.encode(rawValue);
    }

    @Override
    public boolean verifyPassword(String rawValue, String hashedValue) {
        return passwordEncoder.matches(rawValue, hashedValue);
    }

    @Override
    public String hashEsp(String rawValue) {
        return passwordEncoder.encode(rawValue);
    }

    @Override
    public boolean verifyEsp(String rawValue, String hashedValue) {
        return passwordEncoder.matches(rawValue, hashedValue);
    }

    @Override
    public String hashOneTimeCode(String rawValue) {
        return passwordEncoder.encode(rawValue);
    }

    @Override
    public boolean verifyOneTimeCode(String rawValue, String hashedValue) {
        return passwordEncoder.matches(rawValue, hashedValue);
    }
}
