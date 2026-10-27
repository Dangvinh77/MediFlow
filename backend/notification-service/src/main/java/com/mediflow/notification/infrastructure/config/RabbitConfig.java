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
 * docs/ai/06-events-rabbitmq.md). One queue subscribes to the current compatibility events;
 * {@code notification.sent} là routing key publish, không cần binding vào đây.
 */
@Configuration
public class RabbitConfig {

    /** Bound retries for transient storage failures; permanent wire conflicts go directly to DLQ. */
    @Bean
    public org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory notificationListenerFactory(
            ConnectionFactory connection,
            @org.springframework.beans.factory.annotation.Value("${spring.rabbitmq.listener.simple.auto-startup:true}") boolean autoStartup) {
        var factory = new org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connection);
        factory.setAutoStartup(autoStartup);
        factory.setAcknowledgeMode(org.springframework.amqp.core.AcknowledgeMode.AUTO);
        factory.setDefaultRequeueRejected(false);
        factory.setConcurrentConsumers(1);
        factory.setMaxConcurrentConsumers(2);
        factory.setPrefetchCount(10);
        factory.setAdviceChain(org.springframework.amqp.rabbit.config.RetryInterceptorBuilder.stateless()
                .retryPolicy(new org.springframework.retry.policy.SimpleRetryPolicy(3,
                        java.util.Map.of(org.springframework.amqp.AmqpRejectAndDontRequeueException.class, false), true, true))
                .backOffOptions(100, 2, 500)
                .recoverer((message, error) -> { throw new org.springframework.amqp.AmqpRejectAndDontRequeueException("Notification retries exhausted", error); })
                .build());
        return factory;
    }

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
    public static final String RK_INVOICE_CREATED = "invoice.created";

    // Subscribe — CONTRACT-CARE-PROJECTIONS-01 (nested envelope, version=1, no .v1 suffix)
    public static final String RK_ADMISSION_DEPOSIT_REQUESTED = "admission.deposit.requested";
    public static final String RK_ADMISSION_STARTED = "admission.started";
    public static final String RK_ADMISSION_CLOSED = "admission.closed";
    public static final String RK_SURGERY_READY = "surgery.ready";
    public static final String RK_SURGERY_CANCELLED = "surgery.cancelled";
    public static final String RK_SETTLEMENT_COMPLETED = "settlement.completed";

    private static final String[] SUBSCRIBED_ROUTING_KEYS = {
            RK_PATIENT_CREATED,
            RK_APPOINTMENT_CREATED,
            RK_LAB_RESULT_CREATED,
            "medicalrecord.completed",
            "followup.recommendation.created",
            RK_PRESCRIPTION_FILLED,
            RK_PAYMENT_COMPLETED,
            RK_PAYMENT_FAILED,
            RK_ADMISSION_DEPOSIT_REQUESTED,
            RK_ADMISSION_STARTED,
            RK_ADMISSION_CLOSED,
            RK_SURGERY_READY,
            RK_SURGERY_CANCELLED,
            RK_SETTLEMENT_COMPLETED,
            RK_INVOICE_CREATED
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
     * Bind {@value #QUEUE} to the compatibility and approved care projection routing keys. Returns {@link Declarables} (not a raw
     * {@code Binding[]} thô) để {@code RabbitAdmin} — vốn chỉ quét bean kiểu {@code Declarable} —
     * khai báo được từng binding.
     */
    @Bean
    public Declarables notificationQueueBindings(Queue notificationQueue, @org.springframework.beans.factory.annotation.Qualifier("mediflowEventsExchange") TopicExchange mediflowEventsExchange) {
        Binding[] bindings = new Binding[SUBSCRIBED_ROUTING_KEYS.length];
        for (int i = 0; i < SUBSCRIBED_ROUTING_KEYS.length; i++) {
            bindings[i] = BindingBuilder.bind(notificationQueue).to(mediflowEventsExchange)
                    .with(SUBSCRIBED_ROUTING_KEYS[i]);
        }
        return new Declarables(bindings);
    }

    @Bean
    public Binding notificationDeadLetterBinding(Queue notificationDeadLetterQueue, @org.springframework.beans.factory.annotation.Qualifier("mediflowEventsDlx") TopicExchange mediflowEventsDlx) {
        return BindingBuilder.bind(notificationDeadLetterQueue).to(mediflowEventsDlx).with(DEAD_LETTER_ROUTING_KEY);
    }

    @Bean("notificationJsonMessageConverter")
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean("notificationRabbitTemplate")
    public RabbitTemplate rabbitTemplate(ConnectionFactory cf, @org.springframework.beans.factory.annotation.Qualifier("notificationJsonMessageConverter") MessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(cf);
        template.setMessageConverter(converter);
        return template;
    }
}
