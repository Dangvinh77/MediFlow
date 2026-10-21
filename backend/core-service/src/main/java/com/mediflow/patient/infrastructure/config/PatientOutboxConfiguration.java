package com.mediflow.patient.infrastructure.config;

import com.mediflow.patient.infrastructure.messaging.PatientOutboxDispatcher;
import com.mediflow.patient.infrastructure.messaging.PatientOutboxStore;
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
public class PatientOutboxConfiguration {
    @Bean
    PatientOutboxDispatcher patientOutboxDispatcher(PatientOutboxStore store, ConnectionFactory connection,
            @Qualifier("patientTransactionManager") PlatformTransactionManager manager,
            @Value("${mediflow.patient.outbox.maximum-attempts:8}") int maximumAttempts) {
        RabbitTemplate rabbit = new RabbitTemplate(connection);
        rabbit.setMandatory(true);
        return new PatientOutboxDispatcher(store, rabbit, new TransactionTemplate(manager), maximumAttempts);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    @ConditionalOnProperty(name = "mediflow.patient.outbox.scheduling-enabled", havingValue = "true", matchIfMissing = true)
    static class Scheduling {
        private final PatientOutboxDispatcher dispatcher;

        Scheduling(PatientOutboxDispatcher dispatcher) {
            this.dispatcher = dispatcher;
        }

        @Scheduled(fixedDelayString = "${mediflow.patient.outbox.delay-ms:5000}")
        void relay() {
            dispatcher.dispatch();
        }
    }
}
