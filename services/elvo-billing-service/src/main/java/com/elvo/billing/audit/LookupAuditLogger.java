package com.elvo.billing.audit;

import com.elvo.billing.entity.BillLookup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LookupAuditLogger {

    private static final Logger auditLog = LoggerFactory.getLogger("audit.billing.lookup");

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
    }
}