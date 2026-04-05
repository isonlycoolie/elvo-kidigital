package com.elvo.wallet.service.impl;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class MobileCallbackReconciliationService {

    private static final Logger AUDIT_LOG = LoggerFactory.getLogger("audit.wallet.reconciliation");

    private final Map<String, ReconciliationEntry> pendingCallbacks = new ConcurrentHashMap<>();

    public void scheduleRetry(String callbackReference, UUID walletId, BigDecimal amount) {
        if (callbackReference == null || callbackReference.isBlank()) {
            return;
        }
        pendingCallbacks.put(callbackReference, new ReconciliationEntry(walletId, amount, 1));
        AUDIT_LOG.info("mobile_callback_retry_scheduled callbackReference={} walletId={} amount={}",
                callbackReference,
                walletId,
                amount);
    }

    public void markReconciled(String callbackReference) {
        if (callbackReference == null || callbackReference.isBlank()) {
            return;
        }
        pendingCallbacks.remove(callbackReference);
        AUDIT_LOG.info("mobile_callback_reconciled callbackReference={}", callbackReference);
    }

    private record ReconciliationEntry(UUID walletId, BigDecimal amount, int attempts) {
    }
}
