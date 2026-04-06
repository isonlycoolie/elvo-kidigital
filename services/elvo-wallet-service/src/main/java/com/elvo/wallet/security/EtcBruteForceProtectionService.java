package com.elvo.wallet.security;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class EtcBruteForceProtectionService {

    private final Map<String, AttemptWindow> codeAttempts = new ConcurrentHashMap<>();
    private final Map<String, AttemptWindow> deviceAttempts = new ConcurrentHashMap<>();
    private final Map<String, AttemptWindow> ipAttempts = new ConcurrentHashMap<>();

    private final int maxAttemptsPerCode;
    private final int maxAttemptsPerDevice;
    private final int maxAttemptsPerIp;
    private final long windowSeconds;

    public EtcBruteForceProtectionService(
            @Value("${elvo.security.etc.bruteforce.max-attempts-per-code:5}") int maxAttemptsPerCode,
            @Value("${elvo.security.etc.bruteforce.max-attempts-per-device:10}") int maxAttemptsPerDevice,
            @Value("${elvo.security.etc.bruteforce.max-attempts-per-ip:20}") int maxAttemptsPerIp,
            @Value("${elvo.security.etc.bruteforce.window-seconds:900}") long windowSeconds
    ) {
        this.maxAttemptsPerCode = maxAttemptsPerCode;
        this.maxAttemptsPerDevice = maxAttemptsPerDevice;
        this.maxAttemptsPerIp = maxAttemptsPerIp;
        this.windowSeconds = windowSeconds;
    }

    public boolean isBlocked(String codeHash, String deviceId, String sourceIp) {
        Instant now = Instant.now();
        cleanup(now);
        return attempts(codeAttempts, codeHash, now) >= maxAttemptsPerCode
                || attempts(deviceAttempts, normalize(deviceId), now) >= maxAttemptsPerDevice
                || attempts(ipAttempts, normalize(sourceIp), now) >= maxAttemptsPerIp;
    }

    public boolean registerFailure(String codeHash, String deviceId, String sourceIp) {
        Instant now = Instant.now();
        int codeCount = increment(codeAttempts, codeHash, now);
        int deviceCount = increment(deviceAttempts, normalize(deviceId), now);
        int ipCount = increment(ipAttempts, normalize(sourceIp), now);
        return codeCount >= maxAttemptsPerCode || deviceCount >= maxAttemptsPerDevice || ipCount >= maxAttemptsPerIp;
    }

    public void clearOnSuccess(String codeHash, String deviceId, String sourceIp) {
        codeAttempts.remove(codeHash);
        deviceAttempts.remove(normalize(deviceId));
        ipAttempts.remove(normalize(sourceIp));
    }

    private int attempts(Map<String, AttemptWindow> store, String key, Instant now) {
        AttemptWindow state = store.get(key);
        if (state == null || now.isAfter(state.expiresAt())) {
            return 0;
        }
        return state.count();
    }

    private int increment(Map<String, AttemptWindow> store, String key, Instant now) {
        AttemptWindow state = store.get(key);
        if (state == null || now.isAfter(state.expiresAt())) {
            AttemptWindow reset = new AttemptWindow(1, now.plusSeconds(windowSeconds));
            store.put(key, reset);
            return reset.count();
        }

        AttemptWindow updated = new AttemptWindow(state.count() + 1, state.expiresAt());
        store.put(key, updated);
        return updated.count();
    }

    private void cleanup(Instant now) {
        codeAttempts.entrySet().removeIf(entry -> now.isAfter(entry.getValue().expiresAt()));
        deviceAttempts.entrySet().removeIf(entry -> now.isAfter(entry.getValue().expiresAt()));
        ipAttempts.entrySet().removeIf(entry -> now.isAfter(entry.getValue().expiresAt()));
    }

    private String normalize(String value) {
        return (value == null || value.isBlank()) ? "unknown" : value.trim();
    }

    private record AttemptWindow(int count, Instant expiresAt) {
    }
}