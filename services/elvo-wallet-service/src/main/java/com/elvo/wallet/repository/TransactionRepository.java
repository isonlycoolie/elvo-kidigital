package com.elvo.wallet.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.elvo.wallet.entity.Transaction;

import jakarta.persistence.LockModeType;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

        boolean existsByReference(String reference);

        Transaction findFirstByReferenceOrderByCreatedAtDesc(String reference);

    Page<Transaction> findByWalletIdOrderByCreatedAtDesc(UUID walletId, Pageable pageable);

    List<Transaction> findByWalletIdOrderByCreatedAtDesc(UUID walletId);

        List<Transaction> findByStatusInAndExpiresAtBefore(Collection<Transaction.TransactionStatus> statuses, Instant expiresAt);

        List<Transaction> findByExternalReferenceAndStatusIn(String externalReference,
                        Collection<Transaction.TransactionStatus> statuses);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("""
                select t
                from Transaction t
                where t.externalReference = :externalReference
                  and t.status in :statuses
                """)
        List<Transaction> findByExternalReferenceAndStatusInForUpdate(
                @Param("externalReference") String externalReference,
                @Param("statuses") Collection<Transaction.TransactionStatus> statuses);

    @Query("""
            select t
            from Transaction t
            where t.wallet.id = :walletId
              and (:fromDate is null or t.createdAt >= :fromDate)
              and (:toDate is null or t.createdAt <= :toDate)
              and (:type is null or t.type = :type)
              and (:status is null or t.status = :status)
              and (:reference is null or lower(t.reference) like lower(concat('%', :reference, '%')))
            order by t.createdAt desc
            """)
    Page<Transaction> findTransactionHistory(
            @Param("walletId") UUID walletId,
            @Param("fromDate") Instant fromDate,
            @Param("toDate") Instant toDate,
            @Param("type") Transaction.TransactionType type,
            @Param("status") Transaction.TransactionStatus status,
            @Param("reference") String reference,
            Pageable pageable
    );
}
