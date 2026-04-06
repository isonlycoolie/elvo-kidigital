package com.elvo.wallet.service.impl;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.elvo.wallet.security.WalletFieldEncryptionService;

@Service
public class MobileCallbackReconciliationService {

    private static final Logger AUDIT_LOG = LoggerFactory.getLogger("audit.wallet.reconciliation");

    private final Map<String, ReconciliationEntry> pendingCallbacks = new ConcurrentHashMap<>();
    private final Map<String, Long> consumedCallbacks = new ConcurrentHashMap<>();
    private final WalletFieldEncryptionService fieldEncryptionService;
    private final long maxReplayAgeSeconds;

    public MobileCallbackReconciliationService(WalletFieldEncryptionService fieldEncryptionService,
                                               @Value("${elvo.mobile.callback.replay.max-age-seconds:600}") long maxReplayAgeSeconds) {
        this.fieldEncryptionService = fieldEncryptionService;
        this.maxReplayAgeSeconds = maxReplayAgeSeconds;
    }

    public void scheduleRetry(String callbackReference, UUID walletId, BigDecimal amount) {
        if (callbackReference == null || callbackReference.isBlank()) {
            return;
        }
        String protectedReference = protect(callbackReference);
        pendingCallbacks.put(protectedReference, new ReconciliationEntry(walletId, amount, 1));
        AUDIT_LOG.info("mobile_callback_retry_scheduled callbackReference={} walletId={} amount={}",
                protectedReference,
                walletId,
                amount);
    }

    public void markReconciled(String callbackReference) {
        if (callbackReference == null || callbackReference.isBlank()) {
            return;
        }
        String protectedReference = protect(callbackReference);
        pendingCallbacks.remove(protectedReference);
        AUDIT_LOG.info("mobile_callback_reconciled callbackReference={}", protectedReference);
    }

    public boolean consumeOnce(String callbackReference, Long callbackTimestamp) {
        if (callbackReference == null || callbackReference.isBlank() || callbackTimestamp == null) {
            return false;
        }

        long nowEpochSeconds = Instant.now().getEpochSecond();
        long callbackAgeSeconds = nowEpochSeconds - callbackTimestamp;
        if (callbackAgeSeconds > maxReplayAgeSeconds) {
            return false;
        }

        String protectedReference = protect(callbackReference);
        Long previous = consumedCallbacks.putIfAbsent(protectedReference, callbackTimestamp);
        if (previous != null) {
            AUDIT_LOG.warn("mobile_callback_replay_detected callbackReference={} previousTimestamp={} incomingTimestamp={}",
                    protectedReference,
                    previous,
                    callbackTimestamp);
            return false;
        }
        return true;
    }

    private String protect(String callbackReference) {
        return fieldEncryptionService.encrypt(callbackReference.trim());
    }

    private record ReconciliationEntry(UUID walletId, BigDecimal amount, int attempts) {
    }
}
