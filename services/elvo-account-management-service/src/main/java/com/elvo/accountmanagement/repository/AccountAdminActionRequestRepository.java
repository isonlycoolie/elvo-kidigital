package com.elvo.accountmanagement.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.elvo.accountmanagement.entity.AccountAdminActionRequest;

public interface AccountAdminActionRequestRepository extends JpaRepository<AccountAdminActionRequest, UUID> {
}
