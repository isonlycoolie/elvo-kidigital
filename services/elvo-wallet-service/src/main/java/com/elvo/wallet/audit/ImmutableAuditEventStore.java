package com.elvo.wallet.audit;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Repository
public class ImmutableAuditEventStore {

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public void append(AuditEventRecord record) {
        entityManager.persist(record);
    }
}