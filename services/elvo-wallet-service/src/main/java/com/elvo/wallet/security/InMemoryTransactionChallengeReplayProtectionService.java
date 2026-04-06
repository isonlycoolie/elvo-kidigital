package com.elvo.wallet.security;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class InMemoryTransactionChallengeReplayProtectionService implements TransactionChallengeReplayProtectionService {

    private final Map<String, Instant> consumedChallenges = new ConcurrentHashMap<>();
    private final long replayWindowSeconds;

    public InMemoryTransactionChallengeReplayProtectionService(
            @Value("${elvo.security.transaction-challenge.replay.window-seconds:600}") long replayWindowSeconds) {
        this.replayWindowSeconds = replayWindowSeconds;
    }

    @Override
    public boolean consume(String challengeId, Instant expiresAt) {
        if (challengeId == null || challengeId.isBlank() || expiresAt == null) {
            return false;
        }

        Instant now = Instant.now();
        cleanupExpired(now);
        Instant replayExpiresAt = expiresAt.plusSeconds(replayWindowSeconds);
        Instant previous = consumedChallenges.putIfAbsent(challengeId, replayExpiresAt);
        if (previous != null && now.isBefore(previous)) {
            return false;
        }
        if (previous != null && !now.isBefore(previous)) {
            consumedChallenges.put(challengeId, replayExpiresAt);
        }
        return previous == null;
    }

    private void cleanupExpired(Instant now) {
        consumedChallenges.entrySet().removeIf(entry -> now.isAfter(entry.getValue()));
    }
}