package com.elvo.wallet.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.elvo.wallet.entity.Etc;

import jakarta.persistence.LockModeType;

public interface EtcRepository extends JpaRepository<Etc, UUID>, EtcRepositoryCustom {

    boolean existsByCodeHash(String codeHash);

    boolean existsByCodeHashAndStatusIn(String codeHash, Iterable<Etc.EtcStatus> statuses);

    Optional<Etc> findByCodeHash(String codeHash);

    List<Etc> findByWalletIdOrderByCreatedAtDesc(UUID walletId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Etc e where e.codeHash = :codeHash")
    Optional<Etc> findByCodeHashForUpdate(@Param("codeHash") String codeHash);
}
