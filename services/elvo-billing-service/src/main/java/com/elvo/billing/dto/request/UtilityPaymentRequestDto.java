package com.elvo.billing.dto.request;

import java.math.BigDecimal;

public class UtilityPaymentRequestDto {

    private String referenceNumber;
    private BigDecimal amount;
    private String customerPhone;
    private String customerName;
    private String metadata = "{}";
    private boolean lookupRequired;

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

    public String getCustomerPhone() {
        return customerPhone;
    }

    public void setCustomerPhone(String customerPhone) {
        this.customerPhone = customerPhone;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    public boolean isLookupRequired() {
        return lookupRequired;
    }

    public void setLookupRequired(boolean lookupRequired) {
        this.lookupRequired = lookupRequired;
    }
}