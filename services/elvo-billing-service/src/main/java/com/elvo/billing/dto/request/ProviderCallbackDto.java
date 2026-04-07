package com.elvo.billing.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class ProviderCallbackDto {

    @NotBlank(message = "externalReference is required")
    private String externalReference;

    @NotBlank(message = "status is required")
    private String status;

    @NotBlank(message = "referenceNumber is required")
    private String referenceNumber;

    private String receiptNumber;
    private String message;
    private String metadata = "{}";

    public String getExternalReference() {
        return externalReference;
    }

    public void setExternalReference(String externalReference) {
        this.externalReference = externalReference;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getReferenceNumber() {
        return referenceNumber;
    }

    public void setReferenceNumber(String referenceNumber) {
        this.referenceNumber = referenceNumber;
    }

    public String getReceiptNumber() {
        return receiptNumber;
    }

    public void setReceiptNumber(String receiptNumber) {
        this.receiptNumber = receiptNumber;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        if (metadata == null || metadata.isBlank()) {
            this.metadata = "{}";
            return;
        }
        this.metadata = metadata;
    }
}
