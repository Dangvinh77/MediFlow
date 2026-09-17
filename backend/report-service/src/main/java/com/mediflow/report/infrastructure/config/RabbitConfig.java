package com.mediflow.report.infrastructure.config;

import java.util.Set;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.mediflow.report.infrastructure.messaging.ReportMessageRecoverer;

/** RabbitMQ topology and bounded retry policy for the report read model. */
@Configuration
public class RabbitConfig {

    public static final String EVENTS_EXCHANGE = "mediflow.events";
    public static final String DEAD_LETTER_EXCHANGE = "mediflow.events.dlx";
    public static final String QUEUE = "report.q";
    public static final String DLQ = "report.dlq";
    public static final String DEAD_LETTER_ROUTING_KEY = "report.dead-letter";

    /** Conventional aliases used by the other service RabbitConfig classes. */
    public static final String EXCHANGE = EVENTS_EXCHANGE;
    public static final String DLX = DEAD_LETTER_EXCHANGE;

    public static final String RK_MEDICAL_RECORD_CREATED = "medicalrecord.created";
    public static final String RK_LAB_RESULT_CREATED = "lab.result.created";
    public static final String RK_PRESCRIPTION_FILLED = "prescription.filled";
    public static final String RK_PAYMENT_COMPLETED = "payment.completed";
    public static final String RK_PAYMENT_FAILED = "payment.failed";

    /** The V1 contract intentionally excludes staff.department.changed. */
    public static final Set<String> SUBSCRIBED_ROUTING_KEYS = Set.of(
            RK_MEDICAL_RECORD_CREATED,
            RK_LAB_RESULT_CREATED,
            RK_PRESCRIPTION_FILLED,
            RK_PAYMENT_COMPLETED,
            RK_PAYMENT_FAILED);

    @Bean
    public TopicExchange eventsExchange() {
        return new TopicExchange(EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange deadLetterExchange() {
        return new TopicExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    public Queue reportQueue() {
        return QueueBuilder.durable(QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue reportDeadLetterQueue() {
        return QueueBuilder.durable(DLQ).build();
    }

    @Bean
    public Declarables reportQueueBindings(Queue reportQueue, TopicExchange eventsExchange) {
        Binding[] bindings = SUBSCRIBED_ROUTING_KEYS.stream()
                .map(routingKey -> BindingBuilder.bind(reportQueue)
                        .to(eventsExchange)
                        .with(routingKey))
                .toArray(Binding[]::new);
        return new Declarables(bindings);
    }

    @Bean
    public Binding reportDeadLetterBinding(Queue reportDeadLetterQueue,
                                           TopicExchange deadLetterExchange) {
        return BindingBuilder.bind(reportDeadLetterQueue)
                .to(deadLetterExchange)
                .with(DEAD_LETTER_ROUTING_KEY);
    }

    @Bean
    public Jackson2JsonMessageConverter rabbitJsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /** Retry transient consumer failures a finite number of times, then reject to report.dlq. */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter converter,
            @Value("${mediflow.report.rabbit.retry.max-attempts:3}") int maxAttempts,
            @Value("${mediflow.report.rabbit.retry.initial-interval-ms:1000}") long initialIntervalMs,
            @Value("${mediflow.report.rabbit.retry.max-interval-ms:10000}") long maxIntervalMs,
            @Value("${spring.rabbitmq.listener.simple.auto-startup:true}") boolean autoStartup) {

        if (maxAttempts < 1 || initialIntervalMs <= 0 || maxIntervalMs < initialIntervalMs) {
            throw new IllegalArgumentException("Rabbit retry configuration is invalid");
        }
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(converter);
        factory.setAutoStartup(autoStartup);
        factory.setDefaultRequeueRejected(false);
        factory.setAdviceChain(RetryInterceptorBuilder.stateless()
                .maxAttempts(maxAttempts)
                .backOffOptions(initialIntervalMs, 2.0, maxIntervalMs)
                .recoverer(new ReportMessageRecoverer())
                .build());
        return factory;
    }
}
