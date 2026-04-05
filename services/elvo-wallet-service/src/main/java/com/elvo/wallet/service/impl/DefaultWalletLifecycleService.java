package com.elvo.wallet.service.impl;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.elvo.wallet.entity.Wallet;
import com.elvo.wallet.messaging.producer.WalletEventPublisher;
import com.elvo.wallet.repository.WalletRepository;
import com.elvo.wallet.service.WalletLifecycleService;
import com.elvo.wallet.service.model.WalletFlowResult;

@Service
public class DefaultWalletLifecycleService implements WalletLifecycleService {

    private static final Logger AUDIT_LOG = LoggerFactory.getLogger("audit.wallet.lifecycle");

    private final WalletRepository walletRepository;
    private final WalletEventPublisher eventPublisher;

    public DefaultWalletLifecycleService(WalletRepository walletRepository,
                                         WalletEventPublisher eventPublisher) {
        this.walletRepository = walletRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public WalletFlowResult freeze(UUID walletId, String reason) {
        Wallet wallet = walletRepository.findByIdForUpdate(walletId).orElse(null);
        if (wallet == null) {
            return WalletFlowResult.failure("Wallet not found", walletId, "wallet.wallet.frozen.failed");
        }
eventPublisher.publish("wallet.wallet.frozen", java.util.Map.of(
                "walletId", walletId,
                "reason", reason == null ? "manual" : reason));
        
        wallet.setStatus(Wallet.WalletStatus.FROZEN);
        AUDIT_LOG.info("event=wallet.wallet.frozen walletId={} reason={}", walletId, reason);
        return WalletFlowResult.success("Wallet frozen", walletId, null, "wallet.wallet.frozen");
    }

    @Override
    @Transactional
    public WalletFlowResult unfreeze(UUID walletId, String reason) {
        Wallet wallet = walletRepository.findByIdForUpdate(walletId).orElse(null);
        if (wallet == null) {
            return WalletFlowResult.failure("Wallet not found", walletId, "wallet.wallet.unfrozen.failed");
        }
eventPublisher.publish("wallet.wallet.unfrozen", java.util.Map.of(
                "walletId", walletId,
                "reason", reason == null ? "manual" : reason));
        
        wallet.setStatus(Wallet.WalletStatus.ACTIVE);
        AUDIT_LOG.info("event=wallet.wallet.unfrozen walletId={} reason={}", walletId, reason);
        return WalletFlowResult.success("Wallet unfrozen", walletId, null, "wallet.wallet.unfrozen");
    }
}
