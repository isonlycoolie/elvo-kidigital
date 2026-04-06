package com.elvo.wallet.service.impl;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.elvo.wallet.security.WalletFieldEncryptionService;

@Service
public class MobileCallbackReconciliationService {

    private static final Logger AUDIT_LOG = LoggerFactory.getLogger("audit.wallet.reconciliation");

    private final Map<String, ReconciliationEntry> pendingCallbacks = new ConcurrentHashMap<>();
    private final WalletFieldEncryptionService fieldEncryptionService;

    public MobileCallbackReconciliationService(WalletFieldEncryptionService fieldEncryptionService) {
        this.fieldEncryptionService = fieldEncryptionService;
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

    private String protect(String callbackReference) {
        return fieldEncryptionService.encrypt(callbackReference.trim());
    }

    private record ReconciliationEntry(UUID walletId, BigDecimal amount, int attempts) {
    }
}
