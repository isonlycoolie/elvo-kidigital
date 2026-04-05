package com.elvo.identity.entity;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

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

@Entity
@Table(
        name = "audits",
        indexes = {
                @Index(name = "idx_audits_user_id", columnList = "user_id"),
                @Index(name = "idx_audits_action_type", columnList = "action_type"),
                @Index(name = "idx_audits_created_at", columnList = "created_at"),
                @Index(name = "idx_audits_source_ip", columnList = "source_ip")
        }
)
public class Audit {

    @Id
    @GeneratedValue(strategy = jakarta.persistence.GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 64)
    private ActionType actionType;

    @Column(name = "description", length = 512)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 32)
    private SourceType sourceType = SourceType.API;

    @Column(name = "source_ip", length = 45)
    private String sourceIp;

    @Column(name = "source_user_agent", length = 512)
    private String sourceUserAgent;

    @Column(name = "session_id")
    private UUID sessionId;

    @Column(name = "device_id")
    private UUID deviceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public enum ActionType {
        REGISTRATION,
        LOGIN,
        LOGOUT,
        PASSWORD_CHANGE,
        ESP_CHANGE,
        SESSION_REVOCATION,
        DEVICE_REMOVAL,
        USER_ACTIVITY
    }

    public enum SourceType {
        API,
        SYSTEM,
        USER,
        DEVICE
    }

    public UUID getId() {
        return id;
    }

    public ActionType getActionType() {
        return actionType;
    }

    public void setActionType(ActionType actionType) {
        this.actionType = actionType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public SourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(SourceType sourceType) {
        this.sourceType = sourceType;
    }

    public String getSourceIp() {
        return sourceIp;
    }

    public void setSourceIp(String sourceIp) {
        this.sourceIp = sourceIp;
    }

    public String getSourceUserAgent() {
        return sourceUserAgent;
    }

    public void setSourceUserAgent(String sourceUserAgent) {
        this.sourceUserAgent = sourceUserAgent;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public void setSessionId(UUID sessionId) {
        this.sessionId = sessionId;
    }

    public UUID getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(UUID deviceId) {
        this.deviceId = deviceId;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
