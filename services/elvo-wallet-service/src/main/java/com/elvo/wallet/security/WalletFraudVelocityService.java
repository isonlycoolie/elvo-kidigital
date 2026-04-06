package com.elvo.wallet.security;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class WalletFraudVelocityService {

    public enum Operation {
        TRANSFER,
        WITHDRAWAL
    }

    private static final Logger AUDIT_LOG = LoggerFactory.getLogger("audit.wallet.fraud.velocity");

    private final int windowSeconds;
    private final int transferMaxCount;
    private final int withdrawalMaxCount;
    private final Map<String, Deque<Instant>> history = new ConcurrentHashMap<>();

    public WalletFraudVelocityService(
            @org.springframework.beans.factory.annotation.Value("${elvo.security.fraud.velocity.window-seconds:120}") int windowSeconds,
            @org.springframework.beans.factory.annotation.Value("${elvo.security.fraud.velocity.transfer-max-count:5}") int transferMaxCount,
            @org.springframework.beans.factory.annotation.Value("${elvo.security.fraud.velocity.withdrawal-max-count:4}") int withdrawalMaxCount) {
        this.windowSeconds = Math.max(30, windowSeconds);
        this.transferMaxCount = Math.max(2, transferMaxCount);
        this.withdrawalMaxCount = Math.max(2, withdrawalMaxCount);
    }

    public boolean isSuspicious(Operation operation, UUID userId, BigDecimal amount) {
        if (operation == null || userId == null || amount == null || amount.signum() <= 0) {
            return false;
        }

        String key = operation + ":" + userId;
        Instant now = Instant.now();
        Instant lowerBound = now.minusSeconds(windowSeconds);

        Deque<Instant> events = history.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (events) {
            while (!events.isEmpty() && events.peekFirst().isBefore(lowerBound)) {
                events.removeFirst();
            }
            events.addLast(now);
            int threshold = operation == Operation.TRANSFER ? transferMaxCount : withdrawalMaxCount;
            if (events.size() > threshold) {
                AUDIT_LOG.warn("wallet_fraud_velocity_detected operation={} userId={} count={} threshold={} windowSeconds={}",
                        operation, userId, events.size(), threshold, windowSeconds);
                return true;
            }
        }

        return false;
    }
}