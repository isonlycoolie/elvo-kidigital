package com.elvo.wallet.repository;

import java.time.Instant;
import java.util.UUID;

import com.elvo.wallet.entity.Etc;

public interface EtcRepositoryCustom {

    Etc generateCode(UUID walletId, String code, Instant expiresAt);

    boolean redeemCode(String code, Instant currentTime);

    int expireGeneratedCodes(Instant currentTime);

    boolean isCodeExpired(String code, Instant currentTime);
}
