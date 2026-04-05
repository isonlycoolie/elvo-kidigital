package com.elvo.wallet.entity;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "etc_codes")
public class Etc {

    private static final Logger AUDIT_LOG = LoggerFactory.getLogger("audit.wallet.entity");

    @Id
    @GeneratedValue(strategy = jakarta.persistence.GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "wallet_id", nullable = false)
    private Wallet wallet;

    @Column(name = "code", nullable = false, length = 128, unique = true)
    private String code;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private EtcStatus status = EtcStatus.GENERATED;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public enum EtcStatus {
        GENERATED,
        REDEEMED,
        EXPIRED
    }

    @PrePersist
    void onCreate() {
        AUDIT_LOG.info("etc_entity_generated walletId={} code={} expiresAt={} status={}",
                wallet != null ? wallet.getId() : null,
                code,
                expiresAt,
                status);
    }

    @PreUpdate
    void onUpdate() {
        if (status == EtcStatus.REDEEMED) {
            AUDIT_LOG.info("etc_entity_redeemed etcId={} walletId={} code={}",
                    id,
                    wallet != null ? wallet.getId() : null,
                    code);
            return;
        }

        AUDIT_LOG.info("etc_entity_updated etcId={} walletId={} code={} status={} expiresAt={}",
                id,
                wallet != null ? wallet.getId() : null,
                code,
                status,
                expiresAt);
    }

    public UUID getId() {
        return id;
    }

    public Wallet getWallet() {
        return wallet;
    }

    public void setWallet(Wallet wallet) {
        this.wallet = wallet;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public EtcStatus getStatus() {
        return status;
    }

    public void setStatus(EtcStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
