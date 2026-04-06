package com.elvo.wallet.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LedgerSnapshotService {

    public record LedgerSnapshot(LocalDate snapshotDate, int entryCount, String rootHash, String reconciliationProof) {
    }

    private static final Logger AUDIT_LOG = LoggerFactory.getLogger("audit.wallet.ledger.snapshot");

    private final WalletLedgerIntegrationService ledgerIntegrationService;

    public LedgerSnapshotService(WalletLedgerIntegrationService ledgerIntegrationService) {
        this.ledgerIntegrationService = ledgerIntegrationService;
    }

    public LedgerSnapshot createDailySnapshot(LocalDate snapshotDate) {
        LocalDate date = snapshotDate == null ? LocalDate.now() : snapshotDate;
        Map<java.util.UUID, String> hashes = ledgerIntegrationService.snapshotHashes();

        String rootHash = hashes.entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
                .map(entry -> entry.getKey() + ":" + entry.getValue())
                .reduce("", (left, right) -> left + "|" + right);

        String rootDigest = digest(rootHash);
        String reconciliationProof = digest(date + "|" + hashes.size() + "|" + rootDigest);

        LedgerSnapshot snapshot = new LedgerSnapshot(date, hashes.size(), rootDigest, reconciliationProof);
        AUDIT_LOG.info("ledger_daily_snapshot date={} entryCount={} rootHash={} reconciliationProof={}",
                snapshot.snapshotDate(), snapshot.entryCount(), snapshot.rootHash(), snapshot.reconciliationProof());
        return snapshot;
    }

    private String digest(String source) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(source.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required for ledger snapshot proofs", ex);
        }
    }
}
