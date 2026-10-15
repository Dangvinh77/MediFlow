package com.mediflow.clinical.infrastructure.config;

import java.io.IOException;
import java.util.Map;

import org.aopalliance.aop.Advice;
import org.springframework.amqp.AmqpException;
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
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessException;
import org.springframework.retry.policy.SimpleRetryPolicy;

import com.mediflow.clinical.application.exception.ConsumerInboxConflictException;

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
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(3, Map.of(
                DataAccessException.class, true,
                AmqpException.class, true,
                IOException.class, true,
                MessageConversionException.class, false,
                org.springframework.amqp.AmqpRejectAndDontRequeueException.class, false,
                ConsumerInboxConflictException.class, false), false, true);
        factory.setDefaultRequeueRejected(false);
        factory.setAdviceChain(RetryInterceptorBuilder.stateless()
                .retryPolicy(retryPolicy)
                .backOffOptions(250, 2.0, 2000)
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build());
        return factory;
    }
}
