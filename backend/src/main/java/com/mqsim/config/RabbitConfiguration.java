package com.mqsim.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "mq.enabled", havingValue = "true", matchIfMissing = false)
public class RabbitConfiguration {

    @Value("${mq.default.reply.queue:sim.reply.default}")
    private String defaultReplyQueue;

    @Bean
    public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        return new RabbitAdmin(connectionFactory);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(new Jackson2JsonMessageConverter());
        return template;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(new Jackson2JsonMessageConverter());
        factory.setConcurrentConsumers(1);
        factory.setMaxConcurrentConsumers(5);
        return factory;
    }

    // Default exchanges and queues
    @Bean
    public DirectExchange defaultExchange() {
        return new DirectExchange("sim.direct");
    }

    @Bean
    public Queue defaultReplyQueue() {
        return QueueBuilder.durable(defaultReplyQueue).build();
    }

    @Bean
    public Binding defaultReplyBinding() {
        return BindingBuilder
                .bind(defaultReplyQueue())
                .to(defaultExchange())
                .with("reply");
    }

    // Note: Request queues are created dynamically by DynamicListenerService
    // based on database configuration
}