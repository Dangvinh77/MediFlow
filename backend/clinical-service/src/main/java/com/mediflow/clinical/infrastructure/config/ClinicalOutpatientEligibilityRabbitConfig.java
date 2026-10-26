package com.mediflow.clinical.infrastructure.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Dedicated durable route for the new outpatient financial eligibility event. */
@Configuration
@ConditionalOnProperty(prefix = "mediflow.clinical.scheduling", name = "enabled", havingValue = "true")
public class ClinicalOutpatientEligibilityRabbitConfig {
    public static final String ELIGIBILITY_QUEUE = "clinical.outpatient-eligibility.q";
    public static final String ELIGIBILITY_DLQ = "clinical.outpatient-eligibility.dlq";
    public static final String ELIGIBILITY_DEAD_LETTER_KEY = "clinical.outpatient-eligibility.dead-letter";
    public static final String ELIGIBILITY_ROUTING_KEY = "outpatient.financial.eligibility.granted";

    @Bean
    public Queue clinicalOutpatientEligibilityQueue() {
        return QueueBuilder.durable(ELIGIBILITY_QUEUE)
                .deadLetterExchange(RabbitConfig.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(ELIGIBILITY_DEAD_LETTER_KEY)
                .build();
    }

    @Bean
    public Queue clinicalOutpatientEligibilityDeadLetterQueue() {
        return QueueBuilder.durable(ELIGIBILITY_DLQ).build();
    }

    @Bean
    public Binding clinicalOutpatientEligibilityBinding(
            @Qualifier("clinicalOutpatientEligibilityQueue") Queue queue,
            @Qualifier("eventsExchange") TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(ELIGIBILITY_ROUTING_KEY);
    }

    @Bean
    public Binding clinicalOutpatientEligibilityDeadLetterBinding(
            @Qualifier("clinicalOutpatientEligibilityDeadLetterQueue") Queue queue,
            @Qualifier("deadLetterExchange") TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(ELIGIBILITY_DEAD_LETTER_KEY);
    }
}
