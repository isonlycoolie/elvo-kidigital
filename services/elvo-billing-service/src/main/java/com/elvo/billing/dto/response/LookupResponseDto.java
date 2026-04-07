package com.elvo.billing.dto.response;

import java.math.BigDecimal;

import com.elvo.billing.entity.enums.LookupStatus;

public class LookupResponseDto {

    private LookupStatus lookupStatus;
    private String customerName;
    private String referenceNumber;
    private BigDecimal amount;
    private String currency;
    private String description;
    private String billItems;
    private String rawProviderReference;

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

    public String getReferenceNumber() {
        return referenceNumber;
    }

    public void setReferenceNumber(String referenceNumber) {
        this.referenceNumber = referenceNumber;
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
}