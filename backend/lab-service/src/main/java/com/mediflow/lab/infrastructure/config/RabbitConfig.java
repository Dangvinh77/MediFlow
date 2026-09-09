package com.mediflow.lab.infrastructure.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** RabbitMQ topology and JSON serialization shared by lab publishers. */
@Configuration
public class RabbitConfig {

    public static final String EVENTS_EXCHANGE = "mediflow.events";

    @Bean
    public TopicExchange eventsExchange() {
        return new TopicExchange(EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public Jackson2JsonMessageConverter rabbitJsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
