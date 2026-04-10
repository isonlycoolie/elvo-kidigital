package com.elvo.accountmanagement.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.elvo.accountmanagement.entity.AccountRelationship;

public interface AccountRelationshipRepository extends JpaRepository<AccountRelationship, UUID> {

    List<AccountRelationship> findByParentAccountIdAndStatus(UUID parentAccountId, com.elvo.accountmanagement.entity.Account.RelationshipStatus status);

    Optional<AccountRelationship> findByParentAccountIdAndChildAccountIdAndStatus(UUID parentAccountId,
                                                                                   UUID childAccountId,
                                                                                   com.elvo.accountmanagement.entity.Account.RelationshipStatus status);
}
