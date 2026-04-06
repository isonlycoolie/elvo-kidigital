package com.elvo.wallet.security;

import java.time.Instant;

public interface TransactionChallengeReplayProtectionService {

    boolean consume(String challengeId, Instant expiresAt);
}