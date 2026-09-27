package com.mediflow.inpatient.infrastructure.messaging.config;

import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableRabbit
@ConditionalOnProperty(name = "mediflow.inpatient.messaging.consumers.enabled", havingValue = "true")
public class InpatientConsumerConfiguration {
    public static final String EVENTS_EXCHANGE = "mediflow.events";
    public static final String DEAD_LETTER_EXCHANGE = "mediflow.events.dlx";
    public static final String INPATIENT_QUEUE = "inpatient.q";
    public static final String DEAD_LETTER_QUEUE = "inpatient.dlq";
    public static final String DEAD_LETTER_ROUTING_KEY = "inpatient.dead-letter";

    @Bean
    TopicExchange inpatientEventsExchange() {
        return new TopicExchange(EVENTS_EXCHANGE, true, false);
    }

    @Bean
    DirectExchange inpatientDeadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    Queue inpatientQueue() {
        return new Queue(INPATIENT_QUEUE, true, false, false, java.util.Map.of(
                "x-dead-letter-exchange", DEAD_LETTER_EXCHANGE,
                "x-dead-letter-routing-key", DEAD_LETTER_ROUTING_KEY));
    }

    @Bean
    Queue inpatientDeadLetterQueue() {
        return new Queue(DEAD_LETTER_QUEUE, true);
    }

    @Bean
    Binding admissionRequestedBinding(
            @Qualifier("inpatientQueue") Queue queue,
            @Qualifier("inpatientEventsExchange") TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with("admission.requested");
    }

    @Bean
    Binding financialClearanceBinding(
            @Qualifier("inpatientQueue") Queue queue,
            @Qualifier("inpatientEventsExchange") TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with("financial.clearance.granted");
    }

    @Bean
    Binding settlementCompletedBinding(
            @Qualifier("inpatientQueue") Queue queue,
            @Qualifier("inpatientEventsExchange") TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with("settlement.completed");
    }

    @Bean
    Binding depositTopupRequiredBinding(
            @Qualifier("inpatientQueue") Queue queue,
            @Qualifier("inpatientEventsExchange") TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with("deposit.topup.required");
    }

    @Bean
    Binding labResultCreatedBinding(
            @Qualifier("inpatientQueue") Queue queue,
            @Qualifier("inpatientEventsExchange") TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with("lab.result.created");
    }

    @Bean
    Binding prescriptionFilledBinding(
            @Qualifier("inpatientQueue") Queue queue,
            @Qualifier("inpatientEventsExchange") TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with("prescription.filled");
    }

    @Bean
    Binding surgeryReadyBinding(
            @Qualifier("inpatientQueue") Queue queue,
            @Qualifier("inpatientEventsExchange") TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with("surgery.ready");
    }

    @Bean
    Binding surgeryCompletedBinding(
            @Qualifier("inpatientQueue") Queue queue,
            @Qualifier("inpatientEventsExchange") TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with("surgery.completed");
    }

    @Bean
    Binding surgeryCancelledBinding(
            @Qualifier("inpatientQueue") Queue queue,
            @Qualifier("inpatientEventsExchange") TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with("surgery.cancelled");
    }

    @Bean
    Binding inpatientDeadLetterBinding(@Qualifier("inpatientDeadLetterQueue") Queue queue,
                                       DirectExchange inpatientDeadLetterExchange) {
        return BindingBuilder.bind(queue).to(inpatientDeadLetterExchange)
                .with(DEAD_LETTER_ROUTING_KEY);
    }

    @Bean
    SimpleRabbitListenerContainerFactory inpatientListenerContainerFactory(
            ConnectionFactory connectionFactory) {
        var factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setDefaultRequeueRejected(false);
        factory.setAdviceChain((MethodInterceptor) RetryInterceptorBuilder.stateless()
                .maxAttempts(3)
                .backOffOptions(500, 2.0, 5000)
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build());
        return factory;
    }
}
