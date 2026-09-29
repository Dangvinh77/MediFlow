package com.mediflow.surgery.infrastructure.config;

import com.mediflow.surgery.application.port.out.SurgeryClockPort;
import com.mediflow.surgery.application.port.out.SurgeryEventPublisherPort;
import com.mediflow.surgery.application.port.out.SurgeryOutboxPort;
import com.mediflow.surgery.infrastructure.messaging.RabbitSurgeryEventPublisherAdapter;
import com.mediflow.surgery.infrastructure.messaging.SurgeryOutboxDispatcher;
import java.time.Duration;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Keeps broker delivery dormant until both business and producer gates are explicitly enabled. */
@Configuration(proxyBeanMethods = false)
@Profile("!test")
@EnableScheduling
@Conditional(SurgeryOutboxEnabledCondition.class)
public class SurgeryOutboxDispatchConfiguration {

    @Bean
    TopicExchange surgeryEventsExchange(
            @Value("${mediflow.surgery.messaging.producer.exchange:mediflow.events}") String exchange) {
        return new TopicExchange(exchange, true, false);
    }

    @Bean
    SurgeryEventPublisherPort surgeryEventPublisherPort(
            RabbitTemplate rabbitTemplate,
            @Value("${mediflow.surgery.messaging.producer.exchange:mediflow.events}") String exchange,
            @Value("${mediflow.surgery.messaging.producer.confirm-timeout-ms:5000}") long confirmTimeoutMillis) {
        return new RabbitSurgeryEventPublisherAdapter(rabbitTemplate, exchange, confirmTimeoutMillis);
    }

    @Bean
    SurgeryOutboxDispatcher surgeryOutboxDispatcher(
            PlatformTransactionManager transactionManager,
            SurgeryOutboxPort outbox,
            SurgeryEventPublisherPort publisher,
            SurgeryClockPort clock,
            @Value("${mediflow.surgery.messaging.producer.lease-seconds:30}") long leaseSeconds,
            @Value("${mediflow.surgery.messaging.producer.batch-size:20}") int batchSize) {
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return new SurgeryOutboxDispatcher(
                transactions, outbox, publisher, clock, Duration.ofSeconds(leaseSeconds), batchSize);
    }
}
