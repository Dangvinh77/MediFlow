package com.mediflow.clinical.infrastructure.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "mediflow.features.care-finance-v2", name = "enabled", havingValue = "true")
public class ClinicalClearanceRabbitConfig {
    public static final String CLEARANCE_QUEUE = "clinical.clearance.q";
    public static final String CLEARANCE_DLQ = "clinical.clearance.dlq";
    public static final String CLEARANCE_DEAD_LETTER_KEY = "clinical.clearance.dead-letter";
    public static final String CLEARANCE_ROUTING_KEY = "financial.clearance.granted";

    @Bean
    public Queue clinicalClearanceQueue() {
        return QueueBuilder.durable(CLEARANCE_QUEUE)
                .deadLetterExchange(RabbitConfig.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(CLEARANCE_DEAD_LETTER_KEY)
                .build();
    }

    @Bean
    public Queue clinicalClearanceDeadLetterQueue() {
        return QueueBuilder.durable(CLEARANCE_DLQ).build();
    }

    @Bean
    public Binding clinicalClearanceBinding(@Qualifier("clinicalClearanceQueue") Queue queue,
                                            @Qualifier("eventsExchange") TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(CLEARANCE_ROUTING_KEY);
    }

    @Bean
    public Binding clinicalClearanceDeadLetterBinding(
            @Qualifier("clinicalClearanceDeadLetterQueue") Queue queue,
            @Qualifier("deadLetterExchange") TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(CLEARANCE_DEAD_LETTER_KEY);
    }

    @Bean
    public SimpleRabbitListenerContainerFactory clinicalClearanceListenerContainerFactory(
            ConnectionFactory connectionFactory,
            @Qualifier("rabbitJsonMessageConverter") Jackson2JsonMessageConverter messageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setAdviceChain(RetryInterceptorBuilder.stateless()
                .maxAttempts(3)
                .backOffOptions(1000, 2.0, 10000)
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build());
        return factory;
    }
}
