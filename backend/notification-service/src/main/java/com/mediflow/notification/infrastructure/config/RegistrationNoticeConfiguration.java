package com.mediflow.notification.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.notification.application.dto.command.RegistrationNoticeCommand;
import com.mediflow.notification.application.port.out.CareNotificationRepositoryPort;
import com.mediflow.notification.application.port.out.RegistrationNoticeProjectionPort;
import com.mediflow.notification.application.service.RegistrationNoticeService;
import com.mediflow.notification.infrastructure.messaging.RegistrationNoticeDecoder;
import com.mediflow.notification.messaging.consumer.RegistrationNoticeConsumer;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "mediflow.notification.outpatient-registration-consumer.enabled", havingValue = "true")
public class RegistrationNoticeConfiguration {
    @Bean RegistrationNoticeConsumer registrationNoticeConsumer(ObjectMapper mapper, CareNotificationRepositoryPort history,
            RegistrationNoticeProjectionPort projection, @Qualifier("notificationTransactionManager") PlatformTransactionManager manager) {
        var tx = new TransactionTemplate(manager); tx.setTimeout(5);
        return new RegistrationNoticeConsumer(new RegistrationNoticeDecoder(mapper), new RegistrationNoticeService(history, projection), tx);
    }

    @Bean Declarables registrationNoticeBindings() {
        var queue = QueueBuilder.durable("notification.outpatient-registration.q").deadLetterExchange(RabbitConfig.DLX)
                .deadLetterRoutingKey("notification.dead-letter").build();
        return new Declarables(queue, new Binding(queue.getName(), Binding.DestinationType.QUEUE,
                RabbitConfig.EXCHANGE, RegistrationNoticeCommand.EVENT_TYPE, null));
    }
}
