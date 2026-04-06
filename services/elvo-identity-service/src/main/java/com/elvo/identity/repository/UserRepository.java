package com.elvo.identity.repository;

import java.util.Optional;
import java.util.UUID;
import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.elvo.identity.entity.User;

@Repository
@Transactional(readOnly = true)
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByPhone(String phone);

    Optional<User> findByEan(String ean);

    List<User> findByAccountStatusAndVerificationDeadlineBefore(User.AccountStatus accountStatus, Instant before);

    List<User> findByAccountStatusAndVerificationDeadlineIsNullAndCreatedAtBefore(User.AccountStatus accountStatus, Instant before);
}
