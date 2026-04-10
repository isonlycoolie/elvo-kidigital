package com.elvo.accountmanagement.config;

import java.util.HashMap;
import java.util.Map;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;

@Configuration
@EnableRabbit
@EnableConfigurationProperties(IdentityRegistrationMessagingProperties.class)
public class IdentityRegistrationMessagingConfig {

    @Bean
    public Declarables identityRegistrationMessagingTopology(IdentityRegistrationMessagingProperties properties) {
        TopicExchange exchange = new TopicExchange(properties.getExchange(), true, false);
        DirectExchange deadLetterExchange = new DirectExchange(properties.getDeadLetterExchange(), true, false);

        Map<String, Object> queueArgs = new HashMap<>();
        queueArgs.put("x-dead-letter-exchange", properties.getDeadLetterExchange());
        queueArgs.put("x-dead-letter-routing-key", properties.getDeadLetterRoutingKey());
        Queue queue = new Queue(properties.getQueue(), true, false, false, queueArgs);
        Queue deadLetterQueue = new Queue(properties.getDeadLetterQueue(), true);

        Binding queueBinding = BindingBuilder.bind(queue).to(exchange).with(properties.getRoutingKey());
        Binding deadLetterBinding = BindingBuilder.bind(deadLetterQueue)
                .to(deadLetterExchange)
                .with(properties.getDeadLetterRoutingKey());

        return new Declarables(exchange, deadLetterExchange, queue, deadLetterQueue, queueBinding, deadLetterBinding);
    }
}
