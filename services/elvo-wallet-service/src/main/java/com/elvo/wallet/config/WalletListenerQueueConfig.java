package com.elvo.wallet.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WalletListenerQueueConfig {

    @Bean
    public TopicExchange billingExchange(
            @Value("${elvo.messaging.billing.exchange:elvo.billing.exchange}") String exchangeName) {
        return new TopicExchange(exchangeName, true, false);
    }

    @Bean
    public Queue billingRequestedQueue(
            @Value("${elvo.messaging.billing.requested-queue:billing.transaction.requested.queue}") String queueName) {
        return new Queue(queueName, true);
    }

    @Bean
    public Queue billingCompletedQueue(
            @Value("${elvo.messaging.billing.completed-queue:billing.transaction.completed.queue}") String queueName) {
        return new Queue(queueName, true);
    }

    @Bean
    public Queue billingReversedQueue(
            @Value("${elvo.messaging.billing.reversed-queue:billing.transaction.reversed.queue}") String queueName) {
        return new Queue(queueName, true);
    }

    @Bean
    public Binding billingRequestedBinding(Queue billingRequestedQueue, TopicExchange billingExchange) {
        return BindingBuilder.bind(billingRequestedQueue).to(billingExchange).with("billing.transaction.requested");
    }

    @Bean
    public Binding billingCompletedBinding(Queue billingCompletedQueue, TopicExchange billingExchange) {
        return BindingBuilder.bind(billingCompletedQueue).to(billingExchange).with("billing.transaction.completed");
    }

    @Bean
    public Binding billingReversedBinding(Queue billingReversedQueue, TopicExchange billingExchange) {
        return BindingBuilder.bind(billingReversedQueue).to(billingExchange).with("billing.transaction.reversed");
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory,
            MessageConverter messageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setMessageConverter(messageConverter);
        return factory;
    }

    @Bean
    public MessagePostProcessor persistentDeliveryModePostProcessor() {
        return message -> {
            message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            return message;
        };
    }

    @Bean
    public RabbitTemplate rabbitTemplate(
            ConnectionFactory connectionFactory,
            MessageConverter messageConverter,
            MessagePostProcessor persistentDeliveryModePostProcessor) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter);
        rabbitTemplate.setBeforePublishPostProcessors(persistentDeliveryModePostProcessor);
        return rabbitTemplate;
    }
}
