package com.elvo.billing.repository;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.elvo.billing.entity.BillLookup;
import com.elvo.billing.entity.enums.LookupStatus;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;

@Repository
@Transactional
public class BillLookupRepositoryCustomImpl implements BillLookupRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    private final BillingMetadataJsonNormalizer metadataJsonNormalizer;

    public BillLookupRepositoryCustomImpl(BillingMetadataJsonNormalizer metadataJsonNormalizer) {
        this.metadataJsonNormalizer = metadataJsonNormalizer;
    }

    @Override
    public BillLookup createLookup(BillLookup lookup) {
        Objects.requireNonNull(lookup, "lookup must not be null");
        Objects.requireNonNull(lookup.getLookupStatus(), "lookupStatus must not be null");

        if (lookup.getLookupId() == null) {
            lookup.setLookupId(UUID.randomUUID());
        }

        lookup.setMetadata(metadataJsonNormalizer.normalize(lookup.getMetadata()));

        entityManager.persist(lookup);
        return lookup;
    }

    @Override
    public BillLookup updateLookupStatus(UUID lookupId, LookupStatus status) {
        Objects.requireNonNull(lookupId, "lookupId must not be null");
        Objects.requireNonNull(status, "status must not be null");

        BillLookup lookup = entityManager.find(BillLookup.class, lookupId, LockModeType.PESSIMISTIC_WRITE);
        if (lookup == null) {
            throw new IllegalArgumentException("Lookup not found for id: " + lookupId);
        }

        lookup.setLookupStatus(status);
        return lookup;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BillLookup> getLookupById(UUID lookupId) {
        Objects.requireNonNull(lookupId, "lookupId must not be null");
        return Optional.ofNullable(entityManager.find(BillLookup.class, lookupId));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BillLookup> getLookupByReference(String referenceNumber) {
        Objects.requireNonNull(referenceNumber, "referenceNumber must not be null");

        return entityManager.createQuery(
                        "select lookup from BillLookup lookup where lookup.referenceNumber = :referenceNumber",
                        BillLookup.class)
                .setParameter("referenceNumber", referenceNumber)
                .getResultStream()
                .findFirst();
    }
}