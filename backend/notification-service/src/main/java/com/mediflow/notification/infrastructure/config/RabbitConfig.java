package com.mediflow.notification.infrastructure.config;

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

/**
 * Topology RabbitMQ của notification-service (backend-spec/07-notification.md §13.4,
 * docs/ai/06-events-rabbitmq.md). Một queue {@value #QUEUE} bind cả 6 routing key subscribe;
 * {@code notification.sent} là routing key publish, không cần binding vào đây.
 */
@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "mediflow.events";
    public static final String DLX = "mediflow.events.dlx";

    // Publish
    public static final String RK_NOTIFICATION_SENT = "notification.sent";

    // Subscribe — bound to QUEUE below
    public static final String RK_PATIENT_CREATED = "patient.created";
    public static final String RK_APPOINTMENT_CREATED = "appointment.created";
    public static final String RK_LAB_RESULT_CREATED = "lab.result.created";
    public static final String RK_PRESCRIPTION_FILLED = "prescription.filled";
    public static final String RK_PAYMENT_COMPLETED = "payment.completed";
    public static final String RK_PAYMENT_FAILED = "payment.failed";

    private static final String[] SUBSCRIBED_ROUTING_KEYS = {
            RK_PATIENT_CREATED,
            RK_APPOINTMENT_CREATED,
            RK_LAB_RESULT_CREATED,
            RK_PRESCRIPTION_FILLED,
            RK_PAYMENT_COMPLETED,
            RK_PAYMENT_FAILED
    };

    public static final String QUEUE = "notification.q";
    public static final String DLQ = "notification.dlq";
    private static final String DEAD_LETTER_ROUTING_KEY = "notification.dead-letter";

    @Bean
    public TopicExchange mediflowEventsExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange mediflowEventsDlx() {
        return new TopicExchange(DLX, true, false);
    }

    @Bean
    public Queue notificationDeadLetterQueue() {
        return QueueBuilder.durable(DLQ).build();
    }

    @Bean
    public Queue notificationQueue() {
        return QueueBuilder.durable(QUEUE)
                .deadLetterExchange(DLX)
                .deadLetterRoutingKey(DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    /**
     * Bind {@value #QUEUE} vào cả 6 routing key. Trả {@link Declarables} (không phải mảng
     * {@code Binding[]} thô) để {@code RabbitAdmin} — vốn chỉ quét bean kiểu {@code Declarable} —
     * khai báo được từng binding.
     */
    @Bean
    public Declarables notificationQueueBindings(Queue notificationQueue, TopicExchange mediflowEventsExchange) {
        Binding[] bindings = new Binding[SUBSCRIBED_ROUTING_KEYS.length];
        for (int i = 0; i < SUBSCRIBED_ROUTING_KEYS.length; i++) {
            bindings[i] = BindingBuilder.bind(notificationQueue).to(mediflowEventsExchange)
                    .with(SUBSCRIBED_ROUTING_KEYS[i]);
        }
        return new Declarables(bindings);
    }

    @Bean
    public Binding notificationDeadLetterBinding(Queue notificationDeadLetterQueue, TopicExchange mediflowEventsDlx) {
        return BindingBuilder.bind(notificationDeadLetterQueue).to(mediflowEventsDlx).with(DEAD_LETTER_ROUTING_KEY);
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
