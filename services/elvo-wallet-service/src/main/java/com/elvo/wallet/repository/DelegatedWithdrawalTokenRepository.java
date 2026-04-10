package com.elvo.wallet.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.elvo.wallet.entity.DelegatedWithdrawalToken;

public interface DelegatedWithdrawalTokenRepository extends JpaRepository<DelegatedWithdrawalToken, UUID> {

    Optional<DelegatedWithdrawalToken> findByWalletIdAndUserIdAndTokenReference(UUID walletId, UUID userId, String tokenReference);
}
