package com.elvo.delegatedaccess.dto.response;

import java.time.Instant;
import java.util.UUID;

public class DelegatedGrantResponse {

    private UUID grantId;
    private UUID ownerUserId;
    private UUID delegateUserId;
    private String scope;
    private String status;
    private Instant expiresAt;

    public DelegatedGrantResponse() {
    }

    public DelegatedGrantResponse(UUID grantId, UUID ownerUserId, UUID delegateUserId, String scope, String status, Instant expiresAt) {
        this.grantId = grantId;
        this.ownerUserId = ownerUserId;
        this.delegateUserId = delegateUserId;
        this.scope = scope;
        this.status = status;
        this.expiresAt = expiresAt;
    }

    public UUID getGrantId() {
        return grantId;
    }

    public void setGrantId(UUID grantId) {
        this.grantId = grantId;
    }

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
}
