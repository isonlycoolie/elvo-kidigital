package com.elvo.identity.entity;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "sessions",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_sessions_refresh_token", columnNames = "refresh_token")
        },
        indexes = {
                @Index(name = "idx_sessions_user_id", columnList = "user_id"),
                @Index(name = "idx_sessions_device_id", columnList = "device_id"),
                @Index(name = "idx_sessions_expires_at", columnList = "expires_at"),
                @Index(name = "idx_sessions_absolute_expires_at", columnList = "absolute_expires_at"),
                @Index(name = "idx_sessions_status_active", columnList = "session_status,is_active")
        }
)
public class Session {

    @Id
    @GeneratedValue(strategy = jakarta.persistence.GenerationType.UUID)
    private UUID id;

    @Column(name = "jwt_token", nullable = false, length = 4096)
    private String jwtToken;

    @Column(name = "refresh_token", nullable = false, length = 512)
    private String refreshToken;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "absolute_expires_at", nullable = false)
    private Instant absoluteExpiresAt;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "session_status", nullable = false, length = 32)
    private SessionStatus sessionStatus = SessionStatus.ACTIVE;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "is_revoked", nullable = false)
    private boolean revoked;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public enum SessionStatus {
        ACTIVE,
        EXPIRED,
        REVOKED,
        TERMINATED
    }

    public UUID getId() {
        return id;
    }

    public String getJwtToken() {
        return jwtToken;
    }

    public void setJwtToken(String jwtToken) {
        this.jwtToken = jwtToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getAbsoluteExpiresAt() {
        return absoluteExpiresAt;
    }

    public void setAbsoluteExpiresAt(Instant absoluteExpiresAt) {
        this.absoluteExpiresAt = absoluteExpiresAt;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public SessionStatus getSessionStatus() {
        return sessionStatus;
    }

    public void setSessionStatus(SessionStatus sessionStatus) {
        this.sessionStatus = sessionStatus;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isRevoked() {
        return revoked;
    }

    public void setRevoked(boolean revoked) {
        this.revoked = revoked;
    }

    public Device getDevice() {
        return device;
    }

    public void setDevice(Device device) {
        this.device = device;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
