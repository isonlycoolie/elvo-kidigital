package com.elvo.billing.entity;

import com.elvo.billing.entity.enums.BillCategory;
import com.elvo.billing.entity.enums.LookupStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bill_lookups")
public class BillLookup {

    @Id
    @Column(name = "lookup_id", nullable = false)
    private UUID lookupId;

    @Column(name = "request_id", nullable = false, length = 128)
    private String requestId;

    @Enumerated(EnumType.STRING)
    @Column(name = "bill_category", nullable = false, length = 64)
    private BillCategory billCategory;

    @Column(name = "service_code", nullable = false, length = 64)
    private String serviceCode;

    @Column(name = "reference_number", nullable = false, length = 128)
    private String referenceNumber;

    @Column(name = "customer_phone", length = 32)
    private String customerPhone;

    @Column(name = "metadata", nullable = false, columnDefinition = "jsonb")
    private String metadata = "{}";

    @Enumerated(EnumType.STRING)
    @Column(name = "lookup_status", nullable = false, length = 32)
    private LookupStatus lookupStatus;

    @Column(name = "customer_name", length = 255)
    private String customerName;

    @Column(name = "amount", precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", length = 3)
    private String currency;

    @Column(name = "description")
    private String description;

    @Column(name = "bill_items", columnDefinition = "jsonb")
    private String billItems;

    @Column(name = "raw_provider_reference", length = 255)
    private String rawProviderReference;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UUID getLookupId() {
        return lookupId;
    }

    public void setLookupId(UUID lookupId) {
        this.lookupId = lookupId;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public BillCategory getBillCategory() {
        return billCategory;
    }

    public void setBillCategory(BillCategory billCategory) {
        this.billCategory = billCategory;
    }

    public String getServiceCode() {
        return serviceCode;
    }

    public void setServiceCode(String serviceCode) {
        this.serviceCode = serviceCode;
    }

    public String getReferenceNumber() {
        return referenceNumber;
    }

    public void setReferenceNumber(String referenceNumber) {
        this.referenceNumber = referenceNumber;
    }

    public String getCustomerPhone() {
        return customerPhone;
    }

    public void setCustomerPhone(String customerPhone) {
        this.customerPhone = customerPhone;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    public LookupStatus getLookupStatus() {
        return lookupStatus;
    }

    public void setLookupStatus(LookupStatus lookupStatus) {
        this.lookupStatus = lookupStatus;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getBillItems() {
        return billItems;
    }

    public void setBillItems(String billItems) {
        this.billItems = billItems;
    }

    public String getRawProviderReference() {
        return rawProviderReference;
    }

    public void setRawProviderReference(String rawProviderReference) {
        this.rawProviderReference = rawProviderReference;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}