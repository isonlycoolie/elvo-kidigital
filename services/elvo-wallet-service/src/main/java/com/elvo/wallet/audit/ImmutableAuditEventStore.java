package com.elvo.wallet.audit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;

@Repository
public class ImmutableAuditEventStore {

    private static final DateTimeFormatter HASH_TIME_FORMATTER = DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC);

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public void append(AuditEventRecord record) {
        String previousHash = findLatestHash();
        record.setPreviousHash(previousHash);
        record.setRecordHash(computeRecordHash(record));
        entityManager.persist(record);
    }

    @Transactional(readOnly = true)
    public List<AuditEventRecord> findRecent(int limit) {
        int boundedLimit = Math.max(1, Math.min(limit, 200));
        return entityManager.createQuery(
                        "select r from AuditEventRecord r order by r.createdAt desc",
                        AuditEventRecord.class)
                .setMaxResults(boundedLimit)
                .getResultList();
    }

    String computeRecordHash(AuditEventRecord record) {
        String source = String.join("|",
                safe(record.getEventType()),
                safe(record.getRequestId()),
                safe(record.getCorrelationId()),
                HASH_TIME_FORMATTER.format(record.getOccurredAt()),
                safe(record.getPayload()),
                safe(record.getPreviousHash()));
        return sha256(source);
    }

    private String findLatestHash() {
        List<String> hashes = entityManager.createQuery(
                        "select r.recordHash from AuditEventRecord r order by r.createdAt desc",
                        String.class)
                .setMaxResults(1)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultList();
        if (hashes.isEmpty() || hashes.get(0) == null || hashes.get(0).isBlank()) {
            return AuditEventRecord.GENESIS_HASH;
        }
        return hashes.get(0);
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required for audit hash chain", ex);
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}