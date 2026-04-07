package com.elvo.billing.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.elvo.billing.entity.PaymentHistory;

public interface PaymentHistoryRepository extends JpaRepository<PaymentHistory, UUID>, PaymentHistoryRepositoryCustom {
}