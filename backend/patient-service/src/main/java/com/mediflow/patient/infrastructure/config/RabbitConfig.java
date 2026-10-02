package com.mediflow.patient.infrastructure.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {
    public static final String EXCHANGE = "mediflow.events";
    public static final String DLX = "mediflow.events.dlx";
    public static final String QUEUE = "patient.q";
    public static final String DLQ = "patient.dlq";
    public static final String RK_PATIENT_CREATED = "patient.created";
    public static final String RK_PATIENT_UPDATED = "patient.updated";
    public static final String RK_PAYMENT_COMPLETED = "payment.completed";
    private static final String DEAD_LETTER_ROUTING_KEY = "patient.dead-letter";

    @Bean
    public TopicExchange mediflowEventsExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange mediflowEventsDlx() {
        return new TopicExchange(DLX, true, false);
    }

    @Bean
    public Queue patientDeadLetterQueue() {
        return QueueBuilder.durable(DLQ).build();
    }

    @Bean
    public Queue patientQueue() {
        return QueueBuilder.durable(QUEUE)
                .deadLetterExchange(DLX)
                .deadLetterRoutingKey(DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    @Bean
    public Declarables patientQueueBindings(Queue patientQueue, TopicExchange mediflowEventsExchange) {
        Binding paymentBinding = BindingBuilder.bind(patientQueue).to(mediflowEventsExchange)
                .with(RK_PAYMENT_COMPLETED);
        return new Declarables(paymentBinding);
    }

    @Bean
    public Binding patientDeadLetterBinding(Queue patientDeadLetterQueue, TopicExchange mediflowEventsDlx) {
        return BindingBuilder.bind(patientDeadLetterQueue).to(mediflowEventsDlx)
                .with(DEAD_LETTER_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        return template;
    }
}
