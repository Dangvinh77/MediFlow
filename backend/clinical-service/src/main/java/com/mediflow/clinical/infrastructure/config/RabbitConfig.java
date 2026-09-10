package com.mediflow.clinical.infrastructure.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** RabbitMQ topology and JSON serialization for Clinical publishers and consumers. */
@Configuration
public class RabbitConfig {

    public static final String EVENTS_EXCHANGE = "mediflow.events";
    public static final String DEAD_LETTER_EXCHANGE = "mediflow.events.dlx";
    public static final String CLINICAL_QUEUE = "clinical.q";
    public static final String CLINICAL_DEAD_LETTER_QUEUE = "clinical.dlq";
    public static final String LAB_RESULT_CREATED = "lab.result.created";
    public static final String CLINICAL_DEAD_LETTER_KEY = "clinical.dead-letter";

    @Bean
    public TopicExchange eventsExchange() {
        return new TopicExchange(EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange deadLetterExchange() {
        return new TopicExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    public Queue clinicalQueue() {
        return QueueBuilder.durable(CLINICAL_QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(CLINICAL_DEAD_LETTER_KEY)
                .build();
    }

    @Bean
    public Queue clinicalDeadLetterQueue() {
        return QueueBuilder.durable(CLINICAL_DEAD_LETTER_QUEUE).build();
    }

    @Bean
    public Binding labResultBinding(
            @Qualifier("clinicalQueue") Queue clinicalQueue,
            @Qualifier("eventsExchange") TopicExchange eventsExchange) {
        return BindingBuilder.bind(clinicalQueue).to(eventsExchange).with(LAB_RESULT_CREATED);
    }

    @Bean
    public Binding clinicalDeadLetterBinding(
            @Qualifier("clinicalDeadLetterQueue") Queue deadLetterQueue,
            @Qualifier("deadLetterExchange") TopicExchange deadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueue)
                .to(deadLetterExchange)
                .with(CLINICAL_DEAD_LETTER_KEY);
    }

    @Bean
    public Jackson2JsonMessageConverter rabbitJsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
