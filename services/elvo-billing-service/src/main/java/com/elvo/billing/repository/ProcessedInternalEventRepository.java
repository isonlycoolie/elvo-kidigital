package com.elvo.billing.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.elvo.billing.entity.ProcessedInternalEvent;

public interface ProcessedInternalEventRepository extends JpaRepository<ProcessedInternalEvent, String> {
}
