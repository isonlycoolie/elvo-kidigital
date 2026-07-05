package com.elvo.agent.service;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.elvo.agent.dto.request.AgentFloatCheckRequest;
import com.elvo.agent.dto.response.AgentFloatCheckResponse;

@Service
public class AgentFloatService {

    private static final BigDecimal DEFAULT_FLOAT = new BigDecimal("500000.00");

    private final ConcurrentHashMap<UUID, BigDecimal> floatByUser = new ConcurrentHashMap<>();

    public AgentFloatCheckResponse checkFloat(AgentFloatCheckRequest request) {
        BigDecimal available = floatByUser.computeIfAbsent(request.getUserId(), ignored -> DEFAULT_FLOAT);
        boolean hasFloat = available.compareTo(request.getAmount()) >= 0;
        return new AgentFloatCheckResponse(
                hasFloat,
                available,
                hasFloat ? "Agent float available" : "Insufficient agent float");
    }
}
