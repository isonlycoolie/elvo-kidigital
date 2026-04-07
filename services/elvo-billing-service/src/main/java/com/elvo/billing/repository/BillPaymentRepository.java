package com.elvo.billing.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.elvo.billing.entity.BillPayment;

public interface BillPaymentRepository extends JpaRepository<BillPayment, UUID>, BillPaymentRepositoryCustom {
}