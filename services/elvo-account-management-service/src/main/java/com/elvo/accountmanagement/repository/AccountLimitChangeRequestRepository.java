package com.elvo.accountmanagement.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.elvo.accountmanagement.entity.AccountLimitChangeRequest;

public interface AccountLimitChangeRequestRepository extends JpaRepository<AccountLimitChangeRequest, UUID> {
}
