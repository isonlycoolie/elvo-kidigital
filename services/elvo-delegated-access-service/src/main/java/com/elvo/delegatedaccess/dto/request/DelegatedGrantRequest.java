package com.elvo.delegatedaccess.dto.request;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class DelegatedGrantRequest {

    @NotNull
    private UUID ownerUserId;

    @NotNull
    private UUID delegateUserId;

    @NotBlank
    private String scope;

    private Instant expiresAt;

    public UUID getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(UUID ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public UUID getDelegateUserId() {
        return delegateUserId;
    }

    public void setDelegateUserId(UUID delegateUserId) {
        this.delegateUserId = delegateUserId;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }
}
