package com.mediflow.notification.infrastructure.config;

import com.mediflow.notification.application.port.in.ReactToSurgeryNoticeUseCase;
import com.mediflow.notification.application.port.out.CareNotificationRepositoryPort;
import com.mediflow.notification.application.port.out.SurgeryNoticeStatePort;
import com.mediflow.notification.application.port.out.SurgeryNoticeWirePort;
import com.mediflow.notification.application.service.SurgeryNoticeService;
import com.mediflow.notification.messaging.consumer.SurgeryNoticeConsumer;
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
@ConditionalOnProperty(name = {"mediflow.notification.care-v1.enabled", "mediflow.notification.surgery-consumer.enabled"}, havingValue = "true")
public class SurgeryNoticeConfiguration {
    @Bean Declarables surgeryNoticeTopology() {
        var events = new TopicExchange("mediflow.events", true, false);
        var dead = new TopicExchange("mediflow.events.dlx", true, false);
        var queue = QueueBuilder.durable("notification.surgery-v1.q").deadLetterExchange(dead.getName())
                .deadLetterRoutingKey("notification.surgery-v1.dlq").build();
        var dlq = QueueBuilder.durable("notification.surgery-v1.dlq").build();
        return new Declarables(events, dead, queue, dlq,
                BindingBuilder.bind(queue).to(events).with("surgery.ready"),
                BindingBuilder.bind(queue).to(events).with("surgery.readiness.invalidated"),
                BindingBuilder.bind(queue).to(events).with("surgery.cancelled"),
                BindingBuilder.bind(queue).to(events).with("surgery.completed"),
                BindingBuilder.bind(dlq).to(dead).with(dlq.getName()));
    }
    @Bean ReactToSurgeryNoticeUseCase surgeryNotices(CareNotificationRepositoryPort history, SurgeryNoticeStatePort state,
            PlatformTransactionManager manager) {
        var service = new SurgeryNoticeService(history, state);
        var transaction = new TransactionTemplate(manager); transaction.setTimeout(5);
        return command -> transaction.executeWithoutResult(status -> service.receive(command));
    }
    @Bean SurgeryNoticeConsumer surgeryNoticeConsumer(SurgeryNoticeWirePort decoder, ReactToSurgeryNoticeUseCase notices) {
        return new SurgeryNoticeConsumer(decoder, notices);
    }
    @Bean SimpleRabbitListenerContainerFactory surgeryNoticeListenerFactory(ConnectionFactory connection,
            @Value("${spring.rabbitmq.listener.simple.auto-startup:true}") boolean autoStartup) {
        var factory = new SimpleRabbitListenerContainerFactory(); factory.setConnectionFactory(connection);
        factory.setAcknowledgeMode(AcknowledgeMode.AUTO); factory.setAutoStartup(autoStartup);
        factory.setDefaultRequeueRejected(false); factory.setConcurrentConsumers(1); factory.setMaxConcurrentConsumers(2); factory.setPrefetchCount(10);
        factory.setAdviceChain(RetryInterceptorBuilder.stateless()
                .retryPolicy(new org.springframework.retry.policy.SimpleRetryPolicy(3, Map.of(AmqpRejectAndDontRequeueException.class, false), true, true))
                .backOffOptions(100, 2, 500).recoverer((message, error) -> {
                    throw new AmqpRejectAndDontRequeueException("Surgery notification retries exhausted");
                }).build());
        return factory;
    }
}
