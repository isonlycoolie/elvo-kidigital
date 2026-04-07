package com.elvo.billing.repository;

import java.util.Optional;
import java.util.UUID;

import com.elvo.billing.entity.BillPayment;
import com.elvo.billing.entity.enums.PaymentStatus;

public interface BillPaymentRepositoryCustom {

    BillPayment createPayment(BillPayment payment);

    BillPayment updatePaymentStatus(UUID paymentId, PaymentStatus status);

    Optional<BillPayment> getPaymentById(UUID paymentId);

    Optional<BillPayment> getPaymentByReference(String referenceNumber);

    /**
     * Acquire a pessimistic lock on a payment by reference number.
     * Used to prevent race conditions when multiple payments target the same reference.
     * 
     * @param referenceNumber the payment reference number
     * @return Optional containing the locked BillPayment if found
     */
    Optional<BillPayment> getPaymentByReferenceWithLock(String referenceNumber);
}