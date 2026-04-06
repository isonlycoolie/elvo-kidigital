package com.elvo.wallet.security;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class EmergencyControlService {

    private static final String GLOBAL_KILL_SWITCH_KEY = "global-kill-switch";

    private final StringRedisTemplate redisTemplate;
    private final String keyPrefix;
    private final Map<String, String> fallbackState = new ConcurrentHashMap<>();

    public EmergencyControlService(
            ObjectProvider<StringRedisTemplate> redisTemplateProvider,
            @Value("${elvo.security.emergency.key-prefix:elvo:wallet:emergency:}") String keyPrefix) {
        this.redisTemplate = redisTemplateProvider.getIfAvailable();
        this.keyPrefix = keyPrefix == null || keyPrefix.isBlank() ? "elvo:wallet:emergency:" : keyPrefix;
    }

    public void setGlobalKillSwitch(boolean enabled, String reason) {
        setValue(GLOBAL_KILL_SWITCH_KEY, enabled ? normalizeReason(reason) : null);
    }

    public boolean isGlobalKillSwitchEnabled() {
        return getValue(GLOBAL_KILL_SWITCH_KEY) != null;
    }

    public String globalKillSwitchReason() {
        String value = getValue(GLOBAL_KILL_SWITCH_KEY);
        return value == null ? "Emergency controls active" : value;
    }

    public void freezeWalletEmergency(UUID walletId, String reason) {
        if (walletId == null) {
            return;
        }
        setValue("wallet-freeze:" + walletId, normalizeReason(reason));
    }

    public void unfreezeWalletEmergency(UUID walletId) {
        if (walletId == null) {
            return;
        }
        setValue("wallet-freeze:" + walletId, null);
    }

    public boolean isWalletEmergencyFrozen(UUID walletId) {
        if (walletId == null) {
            return false;
        }
        return getValue("wallet-freeze:" + walletId) != null;
    }

    public String walletEmergencyReason(UUID walletId) {
        if (walletId == null) {
            return "Emergency wallet freeze active";
        }
        String value = getValue("wallet-freeze:" + walletId);
        return value == null ? "Emergency wallet freeze active" : value;
    }

    private String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "Emergency control triggered by operator";
        }
        return reason;
    }

    private void setValue(String suffix, String value) {
        String key = keyPrefix + suffix;
        if (redisTemplate != null) {
            try {
                if (value == null) {
                    redisTemplate.delete(key);
                } else {
                    redisTemplate.opsForValue().set(key, value, Duration.ofDays(7));
                }
                return;
            } catch (RuntimeException ignored) {
            }
        }

        if (value == null) {
            fallbackState.remove(key);
        } else {
            fallbackState.put(key, value);
        }
    }

    private String getValue(String suffix) {
        String key = keyPrefix + suffix;
        if (redisTemplate != null) {
            try {
                return redisTemplate.opsForValue().get(key);
            } catch (RuntimeException ignored) {
            }
        }
        return fallbackState.get(key);
    }
}
