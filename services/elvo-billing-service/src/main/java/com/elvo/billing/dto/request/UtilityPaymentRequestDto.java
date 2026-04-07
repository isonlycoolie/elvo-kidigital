package com.elvo.billing.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public class UtilityPaymentRequestDto {

    @NotBlank(message = "referenceNumber is required")
    private String referenceNumber;

    @Positive(message = "amount must be greater than zero")
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

    @AssertTrue(message = "amount is required when lookupRequired is false")
    public boolean isAmountValidForLookupRule() {
        if (lookupRequired) {
            return true;
        }
        return amount != null;
    }
}