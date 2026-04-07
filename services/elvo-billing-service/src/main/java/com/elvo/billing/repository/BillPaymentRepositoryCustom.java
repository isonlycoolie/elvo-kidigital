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

    Optional<BillPayment> getPaymentByReferenceWithLock(String referenceNumber);
}