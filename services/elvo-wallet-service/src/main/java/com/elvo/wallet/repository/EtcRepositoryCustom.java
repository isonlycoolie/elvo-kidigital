package com.elvo.wallet.repository;

import java.time.Instant;
import java.util.UUID;

import com.elvo.wallet.entity.Etc;

public interface EtcRepositoryCustom {

    Etc generateCode(UUID walletId, String codeHash, Instant expiresAt);

    boolean redeemCode(String codeHash, Instant currentTime);

    int expireGeneratedCodes(Instant currentTime);

    boolean isCodeExpired(String codeHash, Instant currentTime);
}
