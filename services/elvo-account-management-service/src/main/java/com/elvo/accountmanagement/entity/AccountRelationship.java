package com.elvo.accountmanagement.entity;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(
        name = "account_relationships",
        indexes = {
                @Index(name = "idx_account_relationships_parent", columnList = "parent_account_id"),
                @Index(name = "idx_account_relationships_child", columnList = "child_account_id")
        }
)
public class AccountRelationship {

    @Id
    @GeneratedValue(strategy = jakarta.persistence.GenerationType.UUID)
    @Column(name = "relationship_id", nullable = false, updatable = false)
    private UUID relationshipId;

    @Column(name = "parent_account_id", nullable = false)
    private UUID parentAccountId;

    @Column(name = "child_account_id", nullable = false)
    private UUID childAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "relationship_type", nullable = false, length = 32)
    private Account.RelationshipType relationshipType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private Account.RelationshipStatus status = Account.RelationshipStatus.PENDING;

    @Column(name = "start_date", nullable = false)
    private Instant startDate = Instant.now();

    @Column(name = "end_date")
    private Instant endDate;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UUID getRelationshipId() {
        return relationshipId;
    }

    public UUID getParentAccountId() {
        return parentAccountId;
    }

    public void setParentAccountId(UUID parentAccountId) {
        this.parentAccountId = parentAccountId;
    }

    public UUID getChildAccountId() {
        return childAccountId;
    }

    public void setChildAccountId(UUID childAccountId) {
        this.childAccountId = childAccountId;
    }

    public Account.RelationshipType getRelationshipType() {
        return relationshipType;
    }

    public void setRelationshipType(Account.RelationshipType relationshipType) {
        this.relationshipType = relationshipType;
    }

    public Account.RelationshipStatus getStatus() {
        return status;
    }

    public void setStatus(Account.RelationshipStatus status) {
        this.status = status;
    }

    public Instant getStartDate() {
        return startDate;
    }

    public void setStartDate(Instant startDate) {
        this.startDate = startDate;
    }

    public Instant getEndDate() {
        return endDate;
    }

    public void setEndDate(Instant endDate) {
        this.endDate = endDate;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
