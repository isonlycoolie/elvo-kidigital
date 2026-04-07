package com.elvo.billing.repository;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.elvo.billing.entity.BillPayment;
import com.elvo.billing.entity.enums.PaymentStatus;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;

@Repository
@Transactional
public class BillPaymentRepositoryCustomImpl implements BillPaymentRepositoryCustom {

    private static final Logger auditLog = LoggerFactory.getLogger("audit.billing.repository");

    @PersistenceContext
    private EntityManager entityManager;

    private final BillingMetadataJsonNormalizer metadataJsonNormalizer;

    public BillPaymentRepositoryCustomImpl(BillingMetadataJsonNormalizer metadataJsonNormalizer) {
        this.metadataJsonNormalizer = metadataJsonNormalizer;
    }

    @Override
    public BillPayment createPayment(BillPayment payment) {
        Objects.requireNonNull(payment, "payment must not be null");
        Objects.requireNonNull(payment.getIdempotencyKey(), "idempotencyKey must not be null");
        Objects.requireNonNull(payment.getBillCategory(), "billCategory must not be null");

        if (payment.getPaymentId() == null) {
            payment.setPaymentId(UUID.randomUUID());
        }

        if (payment.getStatus() == null) {
            payment.setStatus(PaymentStatus.INITIATED);
        }

        Optional<BillPayment> existingPayment = entityManager.createQuery(
                "select payment from BillPayment payment where payment.idempotencyKey = :idempotencyKey",
                BillPayment.class)
            .setParameter("idempotencyKey", payment.getIdempotencyKey())
            .getResultStream()
            .findFirst();

        if (existingPayment.isPresent()) {
            BillPayment alreadyPersisted = existingPayment.get();
            auditLog.info(
                "billing_payment_create_idempotent_replay paymentId={} requestId={} idempotencyKey={} status={}",
                alreadyPersisted.getPaymentId(),
                alreadyPersisted.getRequestId(),
                alreadyPersisted.getIdempotencyKey(),
                alreadyPersisted.getStatus());
            return alreadyPersisted;
        }

        payment.setMetadata(metadataJsonNormalizer.normalize(payment.getMetadata()));

        entityManager.persist(payment);
        auditLog.info(
            "billing_payment_created paymentId={} requestId={} idempotencyKey={} status={} category={}",
            payment.getPaymentId(),
            payment.getRequestId(),
            payment.getIdempotencyKey(),
            payment.getStatus(),
            payment.getBillCategory());
        return payment;
    }

    @Override
    public BillPayment updatePaymentStatus(UUID paymentId, PaymentStatus status) {
        Objects.requireNonNull(paymentId, "paymentId must not be null");
        Objects.requireNonNull(status, "status must not be null");

        BillPayment payment = entityManager.find(BillPayment.class, paymentId, LockModeType.PESSIMISTIC_WRITE);
        if (payment == null) {
            throw new IllegalArgumentException("Payment not found for id: " + paymentId);
        }

        PaymentStatus previousStatus = payment.getStatus();
        payment.setStatus(status);
        auditLog.info(
                "billing_payment_status_updated paymentId={} fromStatus={} toStatus={} requestId={}",
                payment.getPaymentId(),
                previousStatus,
                payment.getStatus(),
                payment.getRequestId());
        return payment;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BillPayment> getPaymentById(UUID paymentId) {
        Objects.requireNonNull(paymentId, "paymentId must not be null");
        return Optional.ofNullable(entityManager.find(BillPayment.class, paymentId));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BillPayment> getPaymentByReference(String referenceNumber) {
        Objects.requireNonNull(referenceNumber, "referenceNumber must not be null");

        return entityManager.createQuery(
                        "select payment from BillPayment payment where payment.referenceNumber = :referenceNumber",
                        BillPayment.class)
                .setParameter("referenceNumber", referenceNumber)
                .getResultStream()
                .findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BillPayment> getPaymentByReferenceWithLock(String referenceNumber) {
        Objects.requireNonNull(referenceNumber, "referenceNumber must not be null");

        return entityManager.createQuery(
                        "select payment from BillPayment payment where payment.referenceNumber = :referenceNumber",
                        BillPayment.class)
                .setParameter("referenceNumber", referenceNumber)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultStream()
                .findFirst();
    }
}