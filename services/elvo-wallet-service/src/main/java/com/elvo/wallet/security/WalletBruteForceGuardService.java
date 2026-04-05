package com.elvo.wallet.security;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class WalletBruteForceGuardService {

    private static final int MAX_FAILURES = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final Map<String, AttemptWindow> attempts = new ConcurrentHashMap<>();

    public boolean isLocked(String clientKey) {
        if (clientKey == null || clientKey.isBlank()) {
            return false;
        }

        AttemptWindow window = attempts.get(clientKey);
        if (window == null || window.lockedUntil == null) {
            return false;
        }

        if (Instant.now().isBefore(window.lockedUntil)) {
            return true;
        }

        attempts.remove(clientKey);
        return false;
    }

    public boolean recordFailure(String clientKey) {
        if (clientKey == null || clientKey.isBlank()) {
            return false;
        }

        AttemptWindow updatedWindow = attempts.compute(clientKey, (key, existingWindow) -> {
            AttemptWindow window = existingWindow == null ? new AttemptWindow() : existingWindow;
            if (window.firstFailureAt == null || Duration.between(window.firstFailureAt, Instant.now()).compareTo(LOCK_DURATION) > 0) {
                window = new AttemptWindow();
            }

            window.firstFailureAt = window.firstFailureAt == null ? Instant.now() : window.firstFailureAt;
            window.failureCount++;
            if (window.failureCount >= MAX_FAILURES) {
                window.lockedUntil = Instant.now().plus(LOCK_DURATION);
            }
            return window;
        });

        return updatedWindow != null && updatedWindow.lockedUntil != null && Instant.now().isBefore(updatedWindow.lockedUntil);
    }

    public void clear(String clientKey) {
        if (clientKey == null || clientKey.isBlank()) {
            return;
        }
        attempts.remove(clientKey);
    }

    private static final class AttemptWindow {
        private Instant firstFailureAt;
        private int failureCount;
        private Instant lockedUntil;
    }
}
