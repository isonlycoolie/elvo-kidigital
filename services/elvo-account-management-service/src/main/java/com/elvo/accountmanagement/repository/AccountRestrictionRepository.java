package com.elvo.accountmanagement.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.elvo.accountmanagement.entity.AccountRestriction;

public interface AccountRestrictionRepository extends JpaRepository<AccountRestriction, UUID> {

    List<AccountRestriction> findByAccountIdAndEndDateIsNull(UUID accountId);
}
