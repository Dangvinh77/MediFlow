package com.mediflow.patient.infrastructure.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ topology. One durable topic exchange for all domain events; routing keys are dot.case
 * event names. See docs/ai/06-events-rabbitmq.md.
 */
@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "mediflow.events";
    public static final String DLX = "mediflow.events.dlx";

    public static final String RK_PATIENT_CREATED = "patient.created";
    public static final String RK_PATIENT_UPDATED = "patient.updated";
    public static final String RK_PAYMENT_COMPLETED = "payment.completed";

    public static final String QUEUE = "patient.q";
    public static final String DLQ = "patient.dlq";

    @Bean
    public TopicExchange mediflowEventsExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange mediflowEventsDlx() {
        return new TopicExchange(DLX, true, false);
    }

    /** Poison messages land here instead of looping forever. */
    @Bean
    public Queue patientDlq() {
        return QueueBuilder.durable(DLQ).build();
    }

    @Bean
    public Queue patientQueue() {
        return QueueBuilder.durable(QUEUE)
                .deadLetterExchange(DLX)
                .deadLetterRoutingKey(RK_PAYMENT_COMPLETED)
                .build();
    }

    @Bean
    public Binding patientQueueBinding(Queue patientQueue, TopicExchange mediflowEventsExchange) {
        return BindingBuilder.bind(patientQueue).to(mediflowEventsExchange).with(RK_PAYMENT_COMPLETED);
    }

    @Bean
    public Binding patientDlqBinding(Queue patientDlq, TopicExchange mediflowEventsDlx) {
        return BindingBuilder.bind(patientDlq).to(mediflowEventsDlx).with(RK_PAYMENT_COMPLETED);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory cf, MessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(cf);
        template.setMessageConverter(converter);
        return template;
    }
}
