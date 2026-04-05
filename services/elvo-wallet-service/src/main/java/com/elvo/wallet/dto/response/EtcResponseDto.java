package com.elvo.wallet.dto.response;

import java.time.Instant;
import java.util.UUID;

public class EtcResponseDto {

    private UUID id;
    private UUID walletId;
    private String code;
    private String status;
    private Instant expiresAt;
    private Instant createdAt;

    public EtcResponseDto() {
    }

    public EtcResponseDto(UUID id, UUID walletId, String code, String status, Instant expiresAt, Instant createdAt) {
        this.id = id;
        this.walletId = walletId;
        this.code = code;
        this.status = status;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getWalletId() {
        return walletId;
    }

    public void setWalletId(UUID walletId) {
        this.walletId = walletId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
