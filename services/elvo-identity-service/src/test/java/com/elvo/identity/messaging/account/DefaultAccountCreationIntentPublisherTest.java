package com.elvo.identity.messaging.account;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.retry.RecoveryCallback;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.support.RetryTemplate;

import com.elvo.identity.config.AccountIntentMessagingProperties;
import com.elvo.identity.entity.User;

@ExtendWith(MockitoExtension.class)
class DefaultAccountCreationIntentPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private RetryTemplate retryTemplate;

    private AccountIntentMessagingProperties properties;
    private DefaultAccountCreationIntentPublisher publisher;

    @BeforeEach
    void setUp() {
        properties = new AccountIntentMessagingProperties();
        publisher = new DefaultAccountCreationIntentPublisher(rabbitTemplate, retryTemplate, properties);

        when(retryTemplate.execute(any(RetryCallback.class), any(RecoveryCallback.class))).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            RetryCallback<Object, RuntimeException> retryCallback = invocation.getArgument(0);
            return retryCallback.doWithRetry(null);
        });
    }

    @Test
    void publishShouldSendAccountCreationIntentEvent() {
        User user = new User();
        user.setEmail("user@elvo.com");
        user.setPhone("+250700000001");
        user.setDisplayName("ELVO User");
        user.setMfaEnabled(true);
        setId(user, UUID.randomUUID());

        publisher.publish(user, "127.0.0.1", "JUnit");

        verify(rabbitTemplate).convertAndSend(
                eq(properties.getExchange()),
                eq(properties.getRoutingKey()),
                any(),
            any(MessagePostProcessor.class));
    }

    private void setId(User user, UUID id) {
        try {
            var field = User.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, id);
        } catch (NoSuchFieldException | IllegalAccessException ex) {
            throw new IllegalStateException("Unable to set user id", ex);
        }
    }
}
