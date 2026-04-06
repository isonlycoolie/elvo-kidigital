package com.elvo.wallet.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.elvo.wallet.entity.TransactionStatusHistory;

public interface TransactionStatusHistoryRepository extends JpaRepository<TransactionStatusHistory, UUID> {
}