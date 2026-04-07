package com.elvo.billing.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.elvo.billing.entity.BillPayment;
import com.elvo.billing.entity.enums.PaymentStatus;

public interface BillPaymentRepository extends JpaRepository<BillPayment, UUID>, BillPaymentRepositoryCustom {

	long countByStatus(PaymentStatus status);
}