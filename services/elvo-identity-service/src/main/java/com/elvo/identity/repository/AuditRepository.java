package com.elvo.identity.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.elvo.identity.entity.Audit;

@Repository
@Transactional(readOnly = true)
public interface AuditRepository extends JpaRepository<Audit, UUID> {

    List<Audit> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<Audit> findByActionTypeOrderByCreatedAtDesc(Audit.ActionType actionType);

    List<Audit> findByCreatedAtBetweenOrderByCreatedAtDesc(Instant from, Instant to);

    List<Audit> findByCorrelationIdOrderByCreatedAtDesc(String correlationId);

    List<Audit> findByUserIdAndActionTypeAndCreatedAtBetweenOrderByCreatedAtDesc(
            UUID userId,
            Audit.ActionType actionType,
            Instant from,
            Instant to
    );
}
