package com.elvo.wallet.client;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.stereotype.Component;

@Component
public class DefaultAgentServiceClient implements AgentServiceClient {

    @Override
    public boolean hasAvailableFloat(UUID userId, BigDecimal amount) {
        return userId != null && amount != null && amount.signum() > 0;
    }
}
