package com.elvo.wallet.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.elvo.wallet.entity.ProcessedInternalEvent;

public interface ProcessedInternalEventRepository extends JpaRepository<ProcessedInternalEvent, String> {
}
