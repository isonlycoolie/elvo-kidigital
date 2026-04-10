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
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableConfigurationProperties(AccountIntentMessagingProperties.class)
public class AccountIntentMessagingConfig {

    @Bean(name = "accountIntentPublishRetryTemplate")
    public RetryTemplate accountIntentPublishRetryTemplate(AccountIntentMessagingProperties properties) {
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

    @Bean(name = "accountIntentEventPublisherExecutor")
    public Executor accountIntentEventPublisherExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("account-intent-publisher-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(500);
        executor.initialize();
        return executor;
    }

    @Bean
    public Declarables accountIntentMessagingTopology(AccountIntentMessagingProperties properties) {
        TopicExchange exchange = new TopicExchange(properties.getExchange(), true, false);
        DirectExchange deadLetterExchange = new DirectExchange(properties.getDeadLetterExchange(), true, false);

        Map<String, Object> queueArgs = new HashMap<>();
        queueArgs.put("x-dead-letter-exchange", properties.getDeadLetterExchange());
        queueArgs.put("x-dead-letter-routing-key", properties.getRetryRoutingKey());
        Queue queue = new Queue(properties.getQueue(), true, false, false, queueArgs);

        Map<String, Object> retryArgs = new HashMap<>();
        retryArgs.put("x-dead-letter-exchange", properties.getExchange());
        retryArgs.put("x-dead-letter-routing-key", properties.getRoutingKey());
        retryArgs.put("x-message-ttl", properties.getRetryDelayMs());
        Queue retryQueue = new Queue(properties.getRetryQueue(), true, false, false, retryArgs);

        Queue deadLetterQueue = new Queue(properties.getDeadLetterQueue(), true);

        Binding queueBinding = BindingBuilder.bind(queue).to(exchange).with(properties.getRoutingKey());
        Binding retryBinding = BindingBuilder.bind(retryQueue).to(deadLetterExchange).with(properties.getRetryRoutingKey());
        Binding deadLetterBinding = BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with(properties.getDeadLetterRoutingKey());

        return new Declarables(exchange, deadLetterExchange, queue, retryQueue, deadLetterQueue,
                queueBinding, retryBinding, deadLetterBinding);
    }
}
