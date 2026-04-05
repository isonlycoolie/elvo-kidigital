package com.elvo.wallet.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.elvo.wallet.entity.Etc;

import jakarta.persistence.LockModeType;

public interface EtcRepository extends JpaRepository<Etc, UUID>, EtcRepositoryCustom {

    boolean existsByCode(String code);

    boolean existsByCodeAndStatusIn(String code, Iterable<Etc.EtcStatus> statuses);

    Optional<Etc> findByCode(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Etc e where e.code = :code")
    Optional<Etc> findByCodeForUpdate(@Param("code") String code);
}
