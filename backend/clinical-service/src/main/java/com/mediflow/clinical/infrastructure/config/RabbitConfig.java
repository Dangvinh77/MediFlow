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
import org.springframework.dao.DataAccessException;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.mediflow.clinical.application.exception.ConsumerInboxConflictException;

/** RabbitMQ topology and JSON serialization for Clinical publishers and consumers. */
@Configuration
public class RabbitConfig {

    public static final String EVENTS_EXCHANGE = "mediflow.events";
    public static final String DEAD_LETTER_EXCHANGE = "mediflow.events.dlx";
    public static final String CLINICAL_QUEUE = "clinical.q";
    public static final String CLINICAL_DEAD_LETTER_QUEUE = "clinical.dlq";
    public static final String LAB_RESULT_CREATED = "lab.result.created";
    public static final String PRESCRIPTION_FILLED = "prescription.filled";
    public static final String CLINICAL_DEAD_LETTER_KEY = "clinical.dead-letter";

    @Bean
    public TopicExchange eventsExchange() {
        return new TopicExchange(EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange deadLetterExchange() {
        return new TopicExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    public Queue clinicalQueue() {
        return QueueBuilder.durable(CLINICAL_QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(CLINICAL_DEAD_LETTER_KEY)
                .build();
    }

    @Bean
    public Queue clinicalDeadLetterQueue() {
        return QueueBuilder.durable(CLINICAL_DEAD_LETTER_QUEUE).build();
    }

    @Bean
    public Binding labResultBinding(
            @Qualifier("clinicalQueue") Queue clinicalQueue,
            @Qualifier("eventsExchange") TopicExchange eventsExchange) {
        return BindingBuilder.bind(clinicalQueue).to(eventsExchange).with(LAB_RESULT_CREATED);
    }

    @Bean
    public Binding prescriptionFilledBinding(
            @Qualifier("clinicalQueue") Queue clinicalQueue,
            @Qualifier("eventsExchange") TopicExchange eventsExchange) {
        return BindingBuilder.bind(clinicalQueue).to(eventsExchange).with(PRESCRIPTION_FILLED);
    }

    @Bean
    public Binding clinicalDeadLetterBinding(
            @Qualifier("clinicalDeadLetterQueue") Queue deadLetterQueue,
            @Qualifier("deadLetterExchange") TopicExchange deadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueue)
                .to(deadLetterExchange)
                .with(CLINICAL_DEAD_LETTER_KEY);
    }

    @Bean
    public Jackson2JsonMessageConverter rabbitJsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public Advice clinicalListenerRetryInterceptor() {
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(3, Map.of(
                DataAccessException.class, true,
                AmqpException.class, true,
                IOException.class, true,
                MessageConversionException.class, false,
                org.springframework.amqp.AmqpRejectAndDontRequeueException.class, false,
                ConsumerInboxConflictException.class, false), false, true);
        return RetryInterceptorBuilder.stateless()
                .retryPolicy(retryPolicy)
                .backOffOptions(250, 2.0, 2000)
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build();
    }

    @Bean(name = "rabbitListenerContainerFactory")
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter messageConverter,
            Advice clinicalListenerRetryInterceptor) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setDefaultRequeueRejected(false);
        factory.setAdviceChain(clinicalListenerRetryInterceptor);
        return factory;
    }
}
