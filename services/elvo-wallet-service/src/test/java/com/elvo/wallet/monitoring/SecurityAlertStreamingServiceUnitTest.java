package com.elvo.wallet.monitoring;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;

class SecurityAlertStreamingServiceUnitTest {

    @SuppressWarnings("unchecked")
    private SecurityAlertStreamingService serviceWithRabbit(RabbitTemplate rabbitTemplate) {
        ObjectProvider<RabbitTemplate> provider = org.mockito.Mockito.mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(rabbitTemplate);
        return new SecurityAlertStreamingService(provider, true, "security.exchange", "security.alert");
    }

    @SuppressWarnings("unchecked")
    private SecurityAlertStreamingService disabledService() {
        ObjectProvider<RabbitTemplate> provider = org.mockito.Mockito.mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        return new SecurityAlertStreamingService(provider, false, "security.exchange", "security.alert");
    }

    @Test
    void shouldPublishAlertToConfiguredExchange() {
        RabbitTemplate rabbitTemplate = org.mockito.Mockito.mock(RabbitTemplate.class);
        SecurityAlertStreamingService service = serviceWithRabbit(rabbitTemplate);

        service.stream("wallet.security.fraud.blocked", "HIGH", UUID.randomUUID(), Map.of("operation", "transfer"));

        verify(rabbitTemplate).convertAndSend(eq("security.exchange"), eq("security.alert"), any(Map.class));
    }

    @Test
    void shouldNotPublishWhenDisabled() {
        SecurityAlertStreamingService service = disabledService();
        RabbitTemplate rabbitTemplate = org.mockito.Mockito.mock(RabbitTemplate.class);

        service.stream("wallet.security.fraud.blocked", "HIGH", UUID.randomUUID(), Map.of());

        verify(rabbitTemplate, never()).convertAndSend(any(String.class), any(String.class), any(Map.class));
    }
}
