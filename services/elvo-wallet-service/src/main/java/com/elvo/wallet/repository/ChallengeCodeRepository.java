package com.elvo.wallet.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.elvo.wallet.entity.ChallengeCode;

import jakarta.persistence.LockModeType;

public interface ChallengeCodeRepository extends JpaRepository<ChallengeCode, UUID> {

    Optional<ChallengeCode> findByCodeHashAndWalletId(String codeHash, UUID walletId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ChallengeCode c where c.codeHash = :codeHash and c.wallet.id = :walletId")
    Optional<ChallengeCode> findByCodeHashAndWalletIdForUpdate(@Param("codeHash") String codeHash,
                                                                @Param("walletId") UUID walletId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update ChallengeCode c set c.status = com.elvo.wallet.entity.ChallengeCode$ChallengeCodeStatus.EXPIRED " +
            "where c.status = com.elvo.wallet.entity.ChallengeCode$ChallengeCodeStatus.ACTIVE and c.expiresAt < :now")
    int expireActiveCodes(@Param("now") Instant now);
}
