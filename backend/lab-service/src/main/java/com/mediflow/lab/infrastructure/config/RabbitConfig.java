package com.mediflow.lab.infrastructure.config;

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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.aopalliance.aop.Advice;
import org.springframework.dao.DataAccessException;
import org.springframework.retry.policy.SimpleRetryPolicy;

import java.io.IOException;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/** RabbitMQ topology and JSON serialization for Lab publishers and consumers. */
@Configuration
public class RabbitConfig {

    public static final String EVENTS_EXCHANGE = "mediflow.events";
    public static final String DEAD_LETTER_EXCHANGE = "mediflow.events.dlx";
    public static final String LAB_QUEUE = "lab.q";
    public static final String LAB_DEAD_LETTER_QUEUE = "lab.dlq";
    public static final String MEDICAL_RECORD_CREATED = "medicalrecord.created";
    public static final String PAYMENT_COMPLETED = "payment.completed";
    public static final String FINANCIAL_CLEARANCE_GRANTED = "financial.clearance.granted";
    public static final String LAB_DEAD_LETTER_KEY = "lab.dead-letter";

    @Bean
    public TopicExchange eventsExchange() {
        return new TopicExchange(EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange deadLetterExchange() {
        return new TopicExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    public Queue labQueue() {
        return QueueBuilder.durable(LAB_QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(LAB_DEAD_LETTER_KEY)
                .build();
    }

    @Bean
    public Queue labDeadLetterQueue() {
        return QueueBuilder.durable(LAB_DEAD_LETTER_QUEUE).build();
    }

    @Bean
    public Binding medicalRecordCreatedBinding(
            @Qualifier("labQueue") Queue labQueue,
            @Qualifier("eventsExchange") TopicExchange eventsExchange) {
        return BindingBuilder.bind(labQueue).to(eventsExchange).with(MEDICAL_RECORD_CREATED);
    }

    @Bean
    public Binding paymentCompletedBinding(
            @Qualifier("labQueue") Queue labQueue,
            @Qualifier("eventsExchange") TopicExchange eventsExchange) {
        return BindingBuilder.bind(labQueue).to(eventsExchange).with(PAYMENT_COMPLETED);
    }

    @Bean
    @ConditionalOnProperty(name = "mediflow.features.care-finance-v2", havingValue = "true")
    public Binding financialClearanceBinding(
            @Qualifier("labQueue") Queue labQueue,
            @Qualifier("eventsExchange") TopicExchange eventsExchange) {
        return BindingBuilder.bind(labQueue).to(eventsExchange).with(FINANCIAL_CLEARANCE_GRANTED);
    }

    @Bean
    public Binding labDeadLetterBinding(
            @Qualifier("labDeadLetterQueue") Queue deadLetterQueue,
            @Qualifier("deadLetterExchange") TopicExchange deadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueue)
                .to(deadLetterExchange)
                .with(LAB_DEAD_LETTER_KEY);
    }

    @Bean
    public Jackson2JsonMessageConverter rabbitJsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public Advice labListenerRetryInterceptor() {
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(3, Map.of(
                DataAccessException.class, true,
                org.springframework.amqp.AmqpException.class, true,
                IOException.class, true,
                MessageConversionException.class, false), false, true);
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
            Advice labListenerRetryInterceptor) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setDefaultRequeueRejected(false);
        factory.setAdviceChain(labListenerRetryInterceptor);
        return factory;
    }
}
