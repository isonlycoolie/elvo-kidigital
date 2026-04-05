package com.elvo.identity.config;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
@EnableConfigurationProperties(AuditMessagingProperties.class)
public class AuditMessagingConfig {

    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         Jackson2JsonMessageConverter messageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter);
        return rabbitTemplate;
    }

    @Bean
    public RetryTemplate auditPublishRetryTemplate(AuditMessagingProperties properties) {
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(properties.getPublishMaxAttempts());
        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(properties.getPublishInitialIntervalMs());
        backOffPolicy.setMultiplier(properties.getPublishMultiplier());
        backOffPolicy.setMaxInterval(properties.getPublishMaxIntervalMs());

        RetryTemplate retryTemplate = new RetryTemplate();
        retryTemplate.setRetryPolicy(retryPolicy);
        retryTemplate.setBackOffPolicy(backOffPolicy);
        return retryTemplate;
    }

    @Bean(name = "auditEventPublisherExecutor")
    public Executor auditEventPublisherExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("audit-publisher-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(500);
        executor.initialize();
        return executor;
    }

    @Bean
    public Declarables auditMessagingTopology(AuditMessagingProperties properties) {
        TopicExchange exchange = new TopicExchange(properties.getExchange(), true, false);
        DirectExchange deadLetterExchange = new DirectExchange(properties.getDeadLetterExchange(), true, false);

        Map<String, Object> queueArgs = new HashMap<>();
        queueArgs.put("x-dead-letter-exchange", properties.getDeadLetterExchange());
        queueArgs.put("x-dead-letter-routing-key", properties.getRetryRoutingKey());
        Queue auditQueue = new Queue(properties.getQueue(), true, false, false, queueArgs);

        Map<String, Object> retryArgs = new HashMap<>();
        retryArgs.put("x-dead-letter-exchange", properties.getExchange());
        retryArgs.put("x-dead-letter-routing-key", properties.getRoutingKey());
        retryArgs.put("x-message-ttl", properties.getRetryDelayMs());
        Queue retryQueue = new Queue(properties.getRetryQueue(), true, false, false, retryArgs);

        Queue deadLetterQueue = new Queue(properties.getDeadLetterQueue(), true);

        Binding auditBinding = BindingBuilder.bind(auditQueue).to(exchange).with(properties.getRoutingKey());
        Binding retryBinding = BindingBuilder.bind(retryQueue).to(deadLetterExchange).with(properties.getRetryRoutingKey());
        Binding deadLetterBinding = BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with(properties.getDeadLetterRoutingKey());

        return new Declarables(exchange, deadLetterExchange, auditQueue, retryQueue, deadLetterQueue,
                auditBinding, retryBinding, deadLetterBinding);
    }
}
