package com.elvo.wallet.service.impl;

import java.math.BigDecimal;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class WalletLedgerIntegrationService {

    private static final Logger AUDIT_LOG = LoggerFactory.getLogger("audit.wallet.ledger");

    public void recordDoubleEntry(String flow, UUID walletId, BigDecimal amount, String reference) {
        AUDIT_LOG.info("ledger_double_entry flow={} walletId={} amount={} reference={}",
                flow,
                walletId,
                amount,
                reference);
    }

    public void reconcileEntry(String flow, UUID walletId, BigDecimal amount, String reference) {
        AUDIT_LOG.info("ledger_reconciliation flow={} walletId={} amount={} reference={}",
                flow,
                walletId,
                amount,
                reference);
    }
}
