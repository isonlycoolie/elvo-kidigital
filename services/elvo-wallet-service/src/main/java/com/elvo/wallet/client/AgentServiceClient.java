package com.elvo.wallet.client;

import java.math.BigDecimal;
import java.util.UUID;

public interface AgentServiceClient {

    boolean hasAvailableFloat(UUID userId, BigDecimal amount);
}
