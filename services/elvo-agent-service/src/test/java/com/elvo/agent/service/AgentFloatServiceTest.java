package com.elvo.agent.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.elvo.agent.dto.request.AgentFloatCheckRequest;

class AgentFloatServiceTest {

    @Test
    void checkFloatShouldAllowWhenAmountWithinDefaultFloat() {
        AgentFloatService service = new AgentFloatService();
        AgentFloatCheckRequest request = new AgentFloatCheckRequest();
        request.setUserId(UUID.randomUUID());
        request.setAmount(new BigDecimal("1000.00"));

        assertThat(service.checkFloat(request).isAvailable()).isTrue();
    }
}
