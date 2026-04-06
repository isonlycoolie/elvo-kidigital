package com.elvo.wallet.service.impl;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class WalletLedgerIntegrationService {

    private static final Logger AUDIT_LOG = LoggerFactory.getLogger("audit.wallet.ledger");
    private static final String GENESIS_HASH = "GENESIS";

    private final StringRedisTemplate redisTemplate;
    private final String hashKeyPrefix;
    private final Map<UUID, String> fallbackLatestHashes = new ConcurrentHashMap<>();

    public WalletLedgerIntegrationService(ObjectProvider<StringRedisTemplate> redisTemplateProvider,
                                          @Value("${elvo.security.ledger.hash.key-prefix:elvo:wallet:ledger:hash:}") String hashKeyPrefix) {
        this.redisTemplate = redisTemplateProvider.getIfAvailable();
        this.hashKeyPrefix = hashKeyPrefix == null || hashKeyPrefix.isBlank()
                ? "elvo:wallet:ledger:hash:"
                : hashKeyPrefix;
    }

    public void recordDoubleEntry(String flow, UUID walletId, BigDecimal amount, String reference) {
        String previousHash = latestHash(walletId);
        String currentHash = computeHash(flow, walletId, amount, reference, previousHash, Instant.now());
        persistLatestHash(walletId, currentHash);

        AUDIT_LOG.info("ledger_double_entry flow={} walletId={} amount={} reference={} previousHash={} currentHash={}",
                flow,
                walletId,
                amount,
                reference,
                previousHash,
                currentHash);
    }

    public void reconcileEntry(String flow, UUID walletId, BigDecimal amount, String reference) {
        AUDIT_LOG.info("ledger_reconciliation flow={} walletId={} amount={} reference={}",
                flow,
                walletId,
                amount,
                reference);
    }

    String latestHash(UUID walletId) {
        if (walletId == null) {
            return GENESIS_HASH;
        }

        String redisKey = hashKeyPrefix + walletId;
        if (redisTemplate != null) {
            try {
                String value = redisTemplate.opsForValue().get(redisKey);
                if (value != null && !value.isBlank()) {
                    return value;
                }
            } catch (RuntimeException ignored) {
            }
        }

        return fallbackLatestHashes.getOrDefault(walletId, GENESIS_HASH);
    }

    private void persistLatestHash(UUID walletId, String hash) {
        if (walletId == null || hash == null || hash.isBlank()) {
            return;
        }

        String redisKey = hashKeyPrefix + walletId;
        if (redisTemplate != null) {
            try {
                redisTemplate.opsForValue().set(redisKey, hash);
            } catch (RuntimeException ignored) {
                fallbackLatestHashes.put(walletId, hash);
            }
            return;
        }

        fallbackLatestHashes.put(walletId, hash);
    }

    private String computeHash(String flow,
                               UUID walletId,
                               BigDecimal amount,
                               String reference,
                               String previousHash,
                               Instant timestamp) {
        String source = String.join("|",
                flow == null ? "" : flow,
                walletId == null ? "" : walletId.toString(),
                amount == null ? "" : amount.toPlainString(),
                reference == null ? "" : reference,
                previousHash == null ? GENESIS_HASH : previousHash,
                timestamp == null ? "" : timestamp.toString());
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(source.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required for ledger hashing", ex);
        }
    }
}
