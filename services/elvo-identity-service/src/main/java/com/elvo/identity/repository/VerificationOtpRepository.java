package com.elvo.identity.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.elvo.identity.entity.VerificationOtp;

import jakarta.persistence.LockModeType;

@Repository
@Transactional(readOnly = true)
public interface VerificationOtpRepository extends JpaRepository<VerificationOtp, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select otp from VerificationOtp otp
            where otp.userId = :userId
              and otp.purpose = :purpose
              and otp.status = com.elvo.identity.entity.VerificationOtp$Status.ACTIVE
            order by otp.createdAt desc
            """)
    List<VerificationOtp> lockActiveOtps(@Param("userId") UUID userId,
                                         @Param("purpose") VerificationOtp.Purpose purpose);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select otp from VerificationOtp otp
            where otp.userId = :userId
              and otp.purpose = :purpose
              and otp.requestId = :requestId
            """)
    Optional<VerificationOtp> lockByRequestId(@Param("userId") UUID userId,
                                              @Param("purpose") VerificationOtp.Purpose purpose,
                                              @Param("requestId") String requestId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<VerificationOtp> findFirstByUserIdAndPurposeAndStatusOrderByCreatedAtDesc(UUID userId,
                                                                                         VerificationOtp.Purpose purpose,
                                                                                         VerificationOtp.Status status);

    @Modifying
    @Transactional
    @Query("""
            update VerificationOtp otp
            set otp.status = :newStatus
            where otp.userId = :userId
              and otp.purpose = :purpose
              and otp.status = com.elvo.identity.entity.VerificationOtp$Status.ACTIVE
            """)
    int invalidateActiveOtps(@Param("userId") UUID userId,
                             @Param("purpose") VerificationOtp.Purpose purpose,
                             @Param("newStatus") VerificationOtp.Status newStatus);

    @Modifying
    @Transactional
    @Query("""
            update VerificationOtp otp
            set otp.status = :newStatus
            where otp.userId = :userId
              and otp.status = com.elvo.identity.entity.VerificationOtp$Status.ACTIVE
            """)
    int invalidateAllActiveOtps(@Param("userId") UUID userId,
                                @Param("newStatus") VerificationOtp.Status newStatus);

    @Query("""
            select count(otp)
            from VerificationOtp otp
            where otp.userId = :userId
              and otp.purpose = :purpose
              and otp.createdAt >= :fromInclusive
            """)
    long countByUserAndPurposeSince(@Param("userId") UUID userId,
                                    @Param("purpose") VerificationOtp.Purpose purpose,
                                    @Param("fromInclusive") Instant fromInclusive);
}
