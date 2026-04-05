package com.elvo.wallet.repository;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.elvo.wallet.entity.Etc;
import com.elvo.wallet.entity.Wallet;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;

@Repository
@Transactional
public class EtcRepositoryImpl implements EtcRepositoryCustom {

    private static final Logger AUDIT_LOG = LoggerFactory.getLogger("audit.wallet.repository");

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Etc generateCode(UUID walletId, String code, Instant expiresAt) {
        validateGenerateInputs(walletId, code, expiresAt);

        Long duplicateCount = entityManager.createQuery(
                        "select count(e.id) from Etc e where e.code = :code and e.status in :statuses",
                        Long.class)
                .setParameter("code", code)
                .setParameter("statuses", List.of(Etc.EtcStatus.GENERATED, Etc.EtcStatus.REDEEMED))
                .getSingleResult();

        if (duplicateCount != null && duplicateCount > 0L) {
            throw new IllegalStateException("Duplicate ETC code is not allowed");
        }

        Wallet wallet = entityManager.find(Wallet.class, walletId, LockModeType.PESSIMISTIC_READ);
        if (wallet == null) {
            throw new IllegalArgumentException("Wallet not found for id: " + walletId);
        }

        Etc etc = new Etc();
        etc.setWallet(wallet);
        etc.setCode(code);
        etc.setExpiresAt(expiresAt);
        etc.setStatus(Etc.EtcStatus.GENERATED);
        entityManager.persist(etc);

        AUDIT_LOG.info("etc_generated etcId={} walletId={} code={} expiresAt={}",
                etc.getId(),
                walletId,
                code,
                expiresAt);

        return etc;
    }

    @Override
    public boolean redeemCode(String code, Instant currentTime) {
        Objects.requireNonNull(currentTime, "currentTime must not be null");

        Etc etc = entityManager.createQuery(
                        "select e from Etc e where e.code = :code",
                        Etc.class)
                .setParameter("code", code)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultStream()
                .findFirst()
                .orElse(null);

        if (etc == null) {
            return false;
        }

        if (etc.getStatus() != Etc.EtcStatus.GENERATED) {
            return false;
        }

        if (etc.getExpiresAt().isBefore(currentTime)) {
            etc.setStatus(Etc.EtcStatus.EXPIRED);
            AUDIT_LOG.info("etc_marked_expired etcId={} walletId={} code={}",
                    etc.getId(),
                    etc.getWallet() != null ? etc.getWallet().getId() : null,
                    etc.getCode());
            return false;
        }

        etc.setStatus(Etc.EtcStatus.REDEEMED);
        AUDIT_LOG.info("etc_redeemed etcId={} walletId={} code={}",
                etc.getId(),
                etc.getWallet() != null ? etc.getWallet().getId() : null,
                etc.getCode());

        return true;
    }

    @Override
    public int expireGeneratedCodes(Instant currentTime) {
        Objects.requireNonNull(currentTime, "currentTime must not be null");

        List<Etc> generatedButExpiredCodes = entityManager.createQuery(
                        "select e from Etc e where e.status = :status and e.expiresAt <= :currentTime",
                        Etc.class)
                .setParameter("status", Etc.EtcStatus.GENERATED)
                .setParameter("currentTime", currentTime)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultList();

        for (Etc etc : generatedButExpiredCodes) {
            etc.setStatus(Etc.EtcStatus.EXPIRED);
            AUDIT_LOG.info("etc_expired etcId={} walletId={} code={} expiresAt={}",
                    etc.getId(),
                    etc.getWallet() != null ? etc.getWallet().getId() : null,
                    etc.getCode(),
                    etc.getExpiresAt());
        }

        return generatedButExpiredCodes.size();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isCodeExpired(String code, Instant currentTime) {
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(currentTime, "currentTime must not be null");

        Etc etc = entityManager.createQuery("select e from Etc e where e.code = :code", Etc.class)
                .setParameter("code", code)
                .getResultStream()
                .findFirst()
                .orElse(null);

        return etc == null || etc.getStatus() == Etc.EtcStatus.EXPIRED || etc.getExpiresAt().isBefore(currentTime);
    }

    private static void validateGenerateInputs(UUID walletId, String code, Instant expiresAt) {
        Objects.requireNonNull(walletId, "walletId must not be null");
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");

        if (code.isBlank()) {
            throw new IllegalArgumentException("ETC code must not be blank");
        }
    }
}
