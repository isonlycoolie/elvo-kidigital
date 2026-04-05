package com.elvo.identity.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.elvo.identity.entity.Session;

@Repository
@Transactional(readOnly = true)
public interface SessionRepository extends JpaRepository<Session, UUID> {

        java.util.Optional<Session> findByRefreshToken(String refreshToken);

        List<Session> findByDeviceIdAndActiveTrueAndRevokedFalse(UUID deviceId);

    List<Session> findByUserIdAndActiveTrueAndRevokedFalseOrderByCreatedAtDesc(UUID userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            update Session s
               set s.revoked = true,
                   s.active = false,
                   s.sessionStatus = com.elvo.identity.entity.Session.SessionStatus.REVOKED
             where s.id = :sessionId
            """)
    int revokeSession(@Param("sessionId") UUID sessionId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            update Session s
               set s.revoked = true,
                   s.active = false,
                   s.sessionStatus = com.elvo.identity.entity.Session.SessionStatus.REVOKED
             where s.user.id = :userId
               and s.active = true
               and s.revoked = false
            """)
    int revokeActiveSessionsByUserId(@Param("userId") UUID userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            delete from Session s
             where s.expiresAt < :referenceTime
                or s.sessionStatus = com.elvo.identity.entity.Session.SessionStatus.EXPIRED
            """)
    int deleteExpiredSessions(@Param("referenceTime") Instant referenceTime);
}
