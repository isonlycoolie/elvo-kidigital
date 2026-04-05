package com.elvo.wallet.service.impl;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.elvo.wallet.service.model.WalletFlowResult;

@Service
public class WalletIdempotencyService {

    private final ConcurrentHashMap<String, WalletFlowResult> completedOperations = new ConcurrentHashMap<>();

    public Optional<WalletFlowResult> get(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(completedOperations.get(key));
    }

    public void put(String key, WalletFlowResult result) {
        if (key == null || key.isBlank() || result == null) {
            return;
        }
        completedOperations.putIfAbsent(key, result);
    }
}
