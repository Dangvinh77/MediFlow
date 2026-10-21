package com.mediflow.organization.infrastructure.config;

import com.mediflow.organization.infrastructure.messaging.OrganizationEventOutboxDispatcher;
import com.mediflow.organization.infrastructure.messaging.OrganizationEventOutboxStore;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
public class OrganizationEventOutboxConfiguration {
    @Bean
    OrganizationEventOutboxDispatcher organizationEventOutboxDispatcher(
            OrganizationEventOutboxStore store, ConnectionFactory connection,
            @Qualifier("organizationTransactionManager") PlatformTransactionManager manager,
            @Value("${mediflow.organization.event-outbox.maximum-attempts:8}") int maximumAttempts) {
        RabbitTemplate rabbit = new RabbitTemplate(connection);
        rabbit.setMandatory(true);
        return new OrganizationEventOutboxDispatcher(store, rabbit,
                new TransactionTemplate(manager), maximumAttempts);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    @ConditionalOnProperty(name = "mediflow.organization.event-outbox.scheduling-enabled",
            havingValue = "true", matchIfMissing = true)
    static class Scheduling {
        private final OrganizationEventOutboxDispatcher dispatcher;
        Scheduling(OrganizationEventOutboxDispatcher dispatcher) { this.dispatcher = dispatcher; }
        @Scheduled(fixedDelayString = "${mediflow.organization.event-outbox.delay-ms:5000}")
        void relay() { dispatcher.dispatch(); }
    }
}
