package com.elvo.billing.repository;

import java.util.Optional;
import java.util.UUID;

import com.elvo.billing.entity.BillLookup;
import com.elvo.billing.entity.enums.LookupStatus;

public interface BillLookupRepositoryCustom {

    BillLookup createLookup(BillLookup lookup);

    BillLookup updateLookupStatus(UUID lookupId, LookupStatus status);

    Optional<BillLookup> getLookupById(UUID lookupId);

    Optional<BillLookup> getLookupByReference(String referenceNumber);
}