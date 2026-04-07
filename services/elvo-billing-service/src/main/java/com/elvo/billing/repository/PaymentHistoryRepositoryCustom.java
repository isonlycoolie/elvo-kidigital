package com.elvo.billing.repository;

import com.elvo.billing.entity.PaymentHistory;

public interface PaymentHistoryRepositoryCustom {

    PaymentHistory logPaymentEvent(PaymentHistory paymentHistory);
}