package com.elvo.agent.dto.response;

import java.math.BigDecimal;

public class AgentFloatCheckResponse {

    private boolean available;
    private BigDecimal availableAmount;
    private String message;

    public AgentFloatCheckResponse() {
    }

    public AgentFloatCheckResponse(boolean available, BigDecimal availableAmount, String message) {
        this.available = available;
        this.availableAmount = availableAmount;
        this.message = message;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public BigDecimal getAvailableAmount() {
        return availableAmount;
    }

    public void setAvailableAmount(BigDecimal availableAmount) {
        this.availableAmount = availableAmount;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
