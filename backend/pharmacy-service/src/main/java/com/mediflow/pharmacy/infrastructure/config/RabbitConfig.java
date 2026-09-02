package com.mediflow.pharmacy.infrastructure.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cấu hình RabbitMQ của pharmacy-service.
 *
 * <p>Service dùng topic exchange chung {@value #EVENTS_EXCHANGE}, nhận
 * {@value #PAYMENT_COMPLETED_ROUTING_KEY} qua queue riêng {@value #PHARMACY_QUEUE}
 * và chuyển poison message sang {@value #PHARMACY_DEAD_LETTER_QUEUE}. Các event được
 * mã hóa JSON để các service không phụ thuộc Java serialization của nhau.</p>
 */
@Configuration
public class RabbitConfig {

    /** Topic exchange chung chứa domain event của MediFlow. */
    public static final String EVENTS_EXCHANGE = "mediflow.events";

    /** Topic exchange nhận các message không thể xử lý sau chính sách retry. */
    public static final String DEAD_LETTER_EXCHANGE = "mediflow.events.dlx";

    /** Queue bền dành riêng cho các event pharmacy tiêu thụ. */
    public static final String PHARMACY_QUEUE = "pharmacy.q";

    /** Dead-letter queue của pharmacy-service. */
    public static final String PHARMACY_DEAD_LETTER_QUEUE = "pharmacy.dlq";

    /** Routing key kích hoạt luồng xuất thuốc sau thanh toán. */
    public static final String PAYMENT_COMPLETED_ROUTING_KEY = "payment.completed";

    /** Routing key nội bộ dùng để chuyển message lỗi vào pharmacy DLQ. */
    public static final String PHARMACY_DEAD_LETTER_ROUTING_KEY = "pharmacy.dead-letter";

    /**
     * Khai báo topic exchange domain event chung.
     *
     * @return exchange bền, không tự xóa
     */
    @Bean
    public TopicExchange eventsExchange() {
        return new TopicExchange(EVENTS_EXCHANGE, true, false);
    }

    /**
     * Khai báo exchange dead-letter chung.
     *
     * @return exchange bền, không tự xóa
     */
    @Bean
    public TopicExchange deadLetterExchange() {
        return new TopicExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    /**
     * Khai báo queue chính của pharmacy và chính sách dead-letter.
     *
     * @return queue {@value #PHARMACY_QUEUE}
     */
    @Bean
    public Queue pharmacyQueue() {
        return QueueBuilder.durable(PHARMACY_QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(PHARMACY_DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    /**
     * Khai báo dead-letter queue để giữ message cần điều tra thủ công.
     *
     * @return queue {@value #PHARMACY_DEAD_LETTER_QUEUE}
     */
    @Bean
    public Queue pharmacyDeadLetterQueue() {
        return QueueBuilder.durable(PHARMACY_DEAD_LETTER_QUEUE).build();
    }

    /**
     * Bind payment event vào queue của pharmacy.
     *
     * @param pharmacyQueue queue nhận event
     * @param eventsExchange exchange domain event
     * @return binding cho routing key {@value #PAYMENT_COMPLETED_ROUTING_KEY}
     */
    @Bean
    public Binding paymentCompletedBinding(
            @Qualifier("pharmacyQueue") Queue pharmacyQueue,
            @Qualifier("eventsExchange") TopicExchange eventsExchange
    ) {
        return BindingBuilder.bind(pharmacyQueue)
                .to(eventsExchange)
                .with(PAYMENT_COMPLETED_ROUTING_KEY);
    }

    /**
     * Bind dead-letter routing key vào pharmacy DLQ.
     *
     * @param pharmacyDeadLetterQueue queue nhận message lỗi
     * @param deadLetterExchange exchange dead-letter
     * @return binding của pharmacy DLQ
     */
    @Bean
    public Binding pharmacyDeadLetterBinding(
            @Qualifier("pharmacyDeadLetterQueue") Queue pharmacyDeadLetterQueue,
            @Qualifier("deadLetterExchange") TopicExchange deadLetterExchange
    ) {
        return BindingBuilder.bind(pharmacyDeadLetterQueue)
                .to(deadLetterExchange)
                .with(PHARMACY_DEAD_LETTER_ROUTING_KEY);
    }

    /**
     * Converter JSON dùng chung cho RabbitTemplate và listener container do Spring Boot tạo.
     *
     * @return Jackson message converter
     */
    @Bean
    public Jackson2JsonMessageConverter rabbitJsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
