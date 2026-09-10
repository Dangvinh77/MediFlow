package com.mediflow.lab.infrastructure.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** RabbitMQ topology and JSON serialization for Lab publishers and consumers. */
@Configuration
public class RabbitConfig {

    public static final String EVENTS_EXCHANGE = "mediflow.events";
    public static final String DEAD_LETTER_EXCHANGE = "mediflow.events.dlx";
    public static final String LAB_QUEUE = "lab.q";
    public static final String LAB_DEAD_LETTER_QUEUE = "lab.dlq";
    public static final String MEDICAL_RECORD_CREATED = "medicalrecord.created";
    public static final String LAB_DEAD_LETTER_KEY = "lab.dead-letter";

    @Bean
    public TopicExchange eventsExchange() {
        return new TopicExchange(EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange deadLetterExchange() {
        return new TopicExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    public Queue labQueue() {
        return QueueBuilder.durable(LAB_QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(LAB_DEAD_LETTER_KEY)
                .build();
    }

    @Bean
    public Queue labDeadLetterQueue() {
        return QueueBuilder.durable(LAB_DEAD_LETTER_QUEUE).build();
    }

    @Bean
    public Binding medicalRecordCreatedBinding(
            @Qualifier("labQueue") Queue labQueue,
            @Qualifier("eventsExchange") TopicExchange eventsExchange) {
        return BindingBuilder.bind(labQueue).to(eventsExchange).with(MEDICAL_RECORD_CREATED);
    }

    @Bean
    public Binding labDeadLetterBinding(
            @Qualifier("labDeadLetterQueue") Queue deadLetterQueue,
            @Qualifier("deadLetterExchange") TopicExchange deadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueue)
                .to(deadLetterExchange)
                .with(LAB_DEAD_LETTER_KEY);
    }

    @Bean
    public Jackson2JsonMessageConverter rabbitJsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
