package com.elvo.billing.repository;

import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.elvo.billing.entity.PaymentHistory;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Repository
@Transactional
public class PaymentHistoryRepositoryCustomImpl implements PaymentHistoryRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    private final BillingMetadataJsonNormalizer metadataJsonNormalizer;

    public PaymentHistoryRepositoryCustomImpl(BillingMetadataJsonNormalizer metadataJsonNormalizer) {
        this.metadataJsonNormalizer = metadataJsonNormalizer;
    }

    @Override
    public PaymentHistory logPaymentEvent(PaymentHistory paymentHistory) {
        Objects.requireNonNull(paymentHistory, "paymentHistory must not be null");
        Objects.requireNonNull(paymentHistory.getPaymentId(), "paymentId must not be null");
        Objects.requireNonNull(paymentHistory.getRequestId(), "requestId must not be null");
        Objects.requireNonNull(paymentHistory.getEventType(), "eventType must not be null");

        if (paymentHistory.getHistoryId() == null) {
            paymentHistory.setHistoryId(UUID.randomUUID());
        }

        paymentHistory.setMetadata(metadataJsonNormalizer.normalize(paymentHistory.getMetadata()));

        entityManager.persist(paymentHistory);
        return paymentHistory;
    }
}