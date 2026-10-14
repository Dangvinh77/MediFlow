package com.mediflow.organization.infrastructure.config;

import com.mediflow.organization.infrastructure.messaging.SurgeryAuthorityOutboxDispatcher;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix="mediflow.organization.surgery-authority", name="outbox-enabled", havingValue="true")
@EnableScheduling
public class SurgeryAuthorityOutboxConfiguration {
    @Bean
    SurgeryAuthorityOutboxDispatcher surgeryAuthorityOutboxDispatcher(JdbcTemplate jdbc,
            ConnectionFactory connection, PlatformTransactionManager manager) {
        // Dedicated template: do not change mandatory-return behavior of existing publishers.
        RabbitTemplate rabbit = new RabbitTemplate(connection);
        rabbit.setMandatory(true);
        return new SurgeryAuthorityOutboxDispatcher(jdbc, rabbit, new TransactionTemplate(manager));
    }
}
