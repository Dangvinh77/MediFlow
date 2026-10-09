package com.mediflow.notification.infrastructure.config;

import com.mediflow.notification.application.port.in.ReactToSurgeryPaymentNoticeUseCase;
import com.mediflow.notification.application.port.out.CareNotificationRepositoryPort;
import com.mediflow.notification.application.port.out.NotificationSourcePort;
import com.mediflow.notification.application.port.out.SurgeryPaymentNoticeWirePort;
import com.mediflow.notification.application.service.SurgeryPaymentNoticeService;
import com.mediflow.notification.messaging.consumer.SurgeryPaymentNoticeConsumer;
import java.util.Map;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
@EnableRabbit
@ConditionalOnProperty(name = {"mediflow.notification.care-v1.enabled", "mediflow.notification.surgery-payment-request-consumer.enabled"}, havingValue = "true")
public class SurgeryPaymentNoticeConfiguration {
    @Bean Declarables surgeryPaymentNoticeTopology() {
        var events = new TopicExchange("mediflow.events", true, false); var dead = new TopicExchange("mediflow.events.dlx", true, false);
        var queue = QueueBuilder.durable("notification.surgery-payment-requests-v1.q").deadLetterExchange(dead.getName())
                .deadLetterRoutingKey("notification.surgery-payment-requests-v1.dlq").build();
        var dlq = QueueBuilder.durable("notification.surgery-payment-requests-v1.dlq").build();
        return new Declarables(events, dead, queue, dlq, BindingBuilder.bind(queue).to(events).with("invoice.created"), BindingBuilder.bind(dlq).to(dead).with(dlq.getName()));
    }
    @Bean ReactToSurgeryPaymentNoticeUseCase surgeryPaymentNotices(CareNotificationRepositoryPort history, NotificationSourcePort sources, PlatformTransactionManager manager) {
        var service = new SurgeryPaymentNoticeService(history, sources); var tx = new TransactionTemplate(manager); tx.setTimeout(5);
        return command -> tx.executeWithoutResult(status -> service.receive(command));
    }
    @Bean SurgeryPaymentNoticeConsumer surgeryPaymentNoticeConsumer(SurgeryPaymentNoticeWirePort wire, ReactToSurgeryPaymentNoticeUseCase notices) {
        return new SurgeryPaymentNoticeConsumer(wire, notices);
    }
    @Bean SimpleRabbitListenerContainerFactory surgeryPaymentNoticeListenerFactory(ConnectionFactory connection,
            @Value("${spring.rabbitmq.listener.simple.auto-startup:true}") boolean autoStartup) {
        var factory = new SimpleRabbitListenerContainerFactory(); factory.setConnectionFactory(connection);
        factory.setAcknowledgeMode(AcknowledgeMode.AUTO); factory.setAutoStartup(autoStartup); factory.setDefaultRequeueRejected(false);
        factory.setConcurrentConsumers(1); factory.setMaxConcurrentConsumers(2); factory.setPrefetchCount(10);
        factory.setAdviceChain(RetryInterceptorBuilder.stateless()
                .retryPolicy(new org.springframework.retry.policy.SimpleRetryPolicy(3, Map.of(AmqpRejectAndDontRequeueException.class, false), true, true))
                .backOffOptions(100, 2, 500).recoverer((message, error) -> { throw new AmqpRejectAndDontRequeueException("Surgery payment notice retries exhausted"); }).build());
        return factory;
    }
}
