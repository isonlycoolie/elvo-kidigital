package com.elvo.billing.config;

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
public class BillingListenerQueueConfig {

    @Bean
    public TopicExchange walletExchange(
            @Value("${elvo.messaging.wallet.exchange:elvo.wallet.exchange}") String exchangeName) {
        return new TopicExchange(exchangeName, true, false);
    }

    @Bean
    public Queue walletCompletedQueue(
            @Value("${elvo.messaging.wallet.completed-queue:wallet.transaction.completed.queue}") String queueName) {
        return new Queue(queueName, true);
    }

    @Bean
    public Queue walletFailedQueue(
            @Value("${elvo.messaging.wallet.failed-queue:wallet.transaction.failed.queue}") String queueName) {
        return new Queue(queueName, true);
    }

    @Bean
    public Binding walletCompletedBinding(Queue walletCompletedQueue, TopicExchange walletExchange) {
        return BindingBuilder.bind(walletCompletedQueue).to(walletExchange).with("wallet.transaction.completed");
    }

    @Bean
    public Binding walletFailedBinding(Queue walletFailedQueue, TopicExchange walletExchange) {
        return BindingBuilder.bind(walletFailedQueue).to(walletExchange).with("wallet.transaction.failed");
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
