package com.elvo.wallet.dto.response;

import java.util.UUID;

public class FlowResultResponseDto {

    private boolean success;
    private String message;
    private UUID walletId;
    private UUID transactionId;
    private String eventType;

    public FlowResultResponseDto() {
    }

    public FlowResultResponseDto(boolean success, String message, UUID walletId, UUID transactionId, String eventType) {
        this.success = success;
        this.message = message;
        this.walletId = walletId;
        this.transactionId = transactionId;
        this.eventType = eventType;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public UUID getWalletId() {
        return walletId;
    }

    public void setWalletId(UUID walletId) {
        this.walletId = walletId;
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(UUID transactionId) {
        this.transactionId = transactionId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }
}
