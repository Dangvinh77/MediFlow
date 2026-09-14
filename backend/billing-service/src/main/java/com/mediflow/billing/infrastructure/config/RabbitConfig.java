package com.mediflow.billing.infrastructure.config;

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
 * Topology RabbitMQ của billing-service (backend-spec/06-billing.md §12.4,
 * docs/ai/06-events-rabbitmq.md). Một queue {@value #QUEUE} nhận 6 routing key billing subscribe;
 * 3 routing key billing publish ({@code invoice.created}, {@code payment.completed},
 * {@code payment.failed}) không cần binding vào đây.
 */
@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "mediflow.events";
    public static final String DLX = "mediflow.events.dlx";

    // Publish
    public static final String RK_INVOICE_CREATED = "invoice.created";
    public static final String RK_PAYMENT_COMPLETED = "payment.completed";
    public static final String RK_PAYMENT_FAILED = "payment.failed";

    // Subscribe — bound to QUEUE below
    public static final String RK_PRESCRIPTION_CREATED = "prescription.created";
    public static final String RK_PRESCRIPTION_FILLED = "prescription.filled";
    public static final String RK_PRESCRIPTION_DISPENSE_FAILED = "prescription.dispense.failed";
    public static final String RK_MEDICAL_RECORD_CREATED = "medicalrecord.created";
    public static final String RK_LAB_RESULT_CREATED = "lab.result.created";
    public static final String RK_APPOINTMENT_STATUS_CHANGED = "appointment.status.changed";

    private static final String[] SUBSCRIBED_ROUTING_KEYS = {
            RK_PRESCRIPTION_CREATED,
            RK_PRESCRIPTION_FILLED,
            RK_PRESCRIPTION_DISPENSE_FAILED,
            RK_MEDICAL_RECORD_CREATED,
            RK_LAB_RESULT_CREATED,
            RK_APPOINTMENT_STATUS_CHANGED
    };

    public static final String QUEUE = "billing.q";
    public static final String DLQ = "billing.dlq";
    private static final String DEAD_LETTER_ROUTING_KEY = "billing.dead-letter";

    @Bean
    public TopicExchange mediflowEventsExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange mediflowEventsDlx() {
        return new TopicExchange(DLX, true, false);
    }

    /** Poison message (deserialize lỗi, xử lý ném exception nhiều lần) rơi vào đây thay vì lặp vô hạn. */
    @Bean
    public Queue billingDeadLetterQueue() {
        return QueueBuilder.durable(DLQ).build();
    }

    @Bean
    public Queue billingQueue() {
        return QueueBuilder.durable(QUEUE)
                .deadLetterExchange(DLX)
                .deadLetterRoutingKey(DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    /**
     * Bind {@value #QUEUE} vào cả 6 routing key. Trả về {@link Declarables} (không phải mảng
     * {@code Binding[]} thô) vì {@code RabbitAdmin} chỉ tự khai báo các bean kiểu {@code Declarable}
     * — nó tìm theo {@code getBeansOfType(Declarable.class)} nên không "mở" được một mảng.
     */
    @Bean
    public Declarables billingQueueBindings(Queue billingQueue, TopicExchange mediflowEventsExchange) {
        Binding[] bindings = new Binding[SUBSCRIBED_ROUTING_KEYS.length];
        for (int i = 0; i < SUBSCRIBED_ROUTING_KEYS.length; i++) {
            bindings[i] = BindingBuilder.bind(billingQueue).to(mediflowEventsExchange).with(SUBSCRIBED_ROUTING_KEYS[i]);
        }
        return new Declarables(bindings);
    }

    @Bean
    public Binding billingDeadLetterBinding(Queue billingDeadLetterQueue, TopicExchange mediflowEventsDlx) {
        return BindingBuilder.bind(billingDeadLetterQueue).to(mediflowEventsDlx).with(DEAD_LETTER_ROUTING_KEY);
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
