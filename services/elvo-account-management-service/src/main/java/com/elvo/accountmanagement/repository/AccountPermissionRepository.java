package com.elvo.accountmanagement.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.elvo.accountmanagement.entity.AccountPermission;

public interface AccountPermissionRepository extends JpaRepository<AccountPermission, UUID> {

    Optional<AccountPermission> findByAccountId(UUID accountId);
}
