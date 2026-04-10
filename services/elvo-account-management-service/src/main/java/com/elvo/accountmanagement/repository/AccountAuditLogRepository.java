package com.elvo.accountmanagement.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.elvo.accountmanagement.entity.AccountAuditLog;

public interface AccountAuditLogRepository extends JpaRepository<AccountAuditLog, UUID> {
}
