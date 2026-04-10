package com.elvo.wallet.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.elvo.wallet.dto.response.DelegatedWithdrawalTokenResponseDto;

public interface DelegatedWithdrawalTokenLifecycleService {

    DelegatedWithdrawalTokenResponseDto issueToken(UUID userId,
                                                   UUID walletId,
                                                   BigDecimal amount,
                                                   Instant expiresAt,
                                                   String delegateReference,
                                                   String idempotencyKey);

    DelegatedWithdrawalTokenResponseDto getToken(UUID userId,
                                                 UUID walletId,
                                                 String delegatedToken);

    DelegatedWithdrawalTokenResponseDto cancelToken(UUID userId,
                                                    UUID walletId,
                                                    String delegatedToken,
                                                    String reason);
}
