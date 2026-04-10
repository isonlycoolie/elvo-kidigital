package com.elvo.accountmanagement.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.elvo.accountmanagement.entity.AccountLimit;

public interface AccountLimitRepository extends JpaRepository<AccountLimit, UUID> {

    Optional<AccountLimit> findByAccountId(UUID accountId);
}
