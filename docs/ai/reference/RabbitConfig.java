package com.mediflow.<service>.infrastructure.config;

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
 *
 * <p>Copy into {@code <service>-service/infrastructure/config/} and replace:
 * <ul>
 *   <li>{@code <service>} in the package name → the module name (pharmacy, billing, ...)</li>
 *   <li>Routing-key constants → the keys this service publishes/subscribes (from its spec)</li>
 *   <li>{@code QUEUE}/{@code DLQ} → {@code <name>.q} / {@code <name>.dlq}</li>
 *   <li>Queue bindings → the routing keys this service's queue binds to</li>
 * </ul>
 */
@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "mediflow.events";
    public static final String DLX = "mediflow.events.dlx";

    // Routing keys this service publishes / subscribes. Replace with the spec's catalog.
    public static final String RK_SOME_EVENT = "some.event";

    public static final String QUEUE = "<service>.q";
    public static final String DLQ = "<service>.dlq";

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
    public Queue serviceDlq() {
        return QueueBuilder.durable(DLQ).build();
    }

    @Bean
    public Queue serviceQueue() {
        return QueueBuilder.durable(QUEUE)
                .deadLetterExchange(DLX)
                .deadLetterRoutingKey(RK_SOME_EVENT)
                .build();
    }

    @Bean
    public Binding serviceQueueBinding(Queue serviceQueue, TopicExchange mediflowEventsExchange) {
        return BindingBuilder.bind(serviceQueue).to(mediflowEventsExchange).with(RK_SOME_EVENT);
    }

    @Bean
    public Binding serviceDlqBinding(Queue serviceDlq, TopicExchange mediflowEventsDlx) {
        return BindingBuilder.bind(serviceDlq).to(mediflowEventsDlx).with(RK_SOME_EVENT);
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
