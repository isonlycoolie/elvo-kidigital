package com.elvo.billing.audit;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import com.elvo.billing.entity.BillLookup;

@Component
public class LookupAuditLogger {

    private static final Logger auditLog = LoggerFactory.getLogger("audit.billing.lookup");
    private final ImmutableAuditStorageService immutableAuditStorageService;

    public LookupAuditLogger(@Nullable ImmutableAuditStorageService immutableAuditStorageService) {
        this.immutableAuditStorageService = immutableAuditStorageService;
    }

    public void logLookupExecuted(BillLookup lookup) {
        auditLog.info(
                "lookup_executed lookupId={} requestId={} referenceNumber={} status={} amount={} currency={} serviceCode={}",
                lookup.getLookupId(),
                lookup.getRequestId(),
                lookup.getReferenceNumber(),
                lookup.getLookupStatus(),
                lookup.getAmount(),
                lookup.getCurrency(),
                lookup.getServiceCode());

        if (immutableAuditStorageService == null) {
            return;
        }

        try {
            immutableAuditStorageService.append(
                    "billing.lookup.executed",
                    lookup.getRequestId(),
                    lookup.getRequestId(),
                    Instant.now(),
                    "lookupStatus=" + lookup.getLookupStatus() + ",serviceCode=" + lookup.getServiceCode());
        } catch (RuntimeException ignored) {
            // Preserve lookup flow when audit persistence is temporarily unavailable.
        }
    }
}